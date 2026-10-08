package net.zamasoft.foliojet.formatter.impl.epub;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

import jp.cssj.cti2.TranscoderException;
import net.zamasoft.foliojet.formatter.Formatter;
import net.zamasoft.foliojet.formatter.MultiDocumentFormatter;
import net.zamasoft.foliojet.formatter.impl.document.TranscoderHandler;
import net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.layout.RetainedTextLimitException;
import net.zamasoft.foliojet.layout.util.LayoutThreadContext;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.MultiDocumentOutput;
import net.zamasoft.foliojet.ua.MultiDocumentOutput.DocumentSet;
import net.zamasoft.foliojet.ua.MultiDocumentOutput.DocumentUnit;
import net.zamasoft.foliojet.ua.MultiDocumentOutput.TocEntry;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.Impositions;
import net.zamasoft.foliojet.ua.props.BooleanPropManager;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.ParserFactory;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.foliojet.xml.vocab.CSSJML;
import net.zamasoft.foliojet.plugin.PluginRegistry;
import net.zamasoft.foliojet.epub.ArchiveFile;
import net.zamasoft.foliojet.epub.BaseURISourceResolver;
import net.zamasoft.foliojet.epub.Container;
import net.zamasoft.foliojet.epub.Container.Rootfile;
import net.zamasoft.foliojet.epub.Contents;
import net.zamasoft.foliojet.epub.EPubFile;
import net.zamasoft.foliojet.epub.Item;
import net.zamasoft.foliojet.epub.ItemRef;
import net.zamasoft.foliojet.epub.NavPoint;
import net.zamasoft.foliojet.epub.PropertiedString;
import net.zamasoft.foliojet.epub.ResolvedArchiveFile;
import net.zamasoft.foliojet.epub.Toc;
import net.zamasoft.foliojet.epub.ZipArchiveFile;
import net.zamasoft.foliojet.epub.util.WritingModeHandler;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.util.SourceWrapper;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.zstream.resolver.protocol.zip.ZIPFileSource;
import net.zamasoft.zstream.resolver.protocol.zip.ZIPFileSourceResolver;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Formats EPub.
 *
 * <p>
 * If the output is {@link MultiDocumentOutput} (Paged SVG), lays out spine items <b>as independent
 * documents</b> ({@link #formatDocuments}). Opens a child UA for each item,
 * which drives its own passes and can run in parallel. The parent releases results in spine order.
 * Other output formats (PDF and images) feed all items sequentially to a single UA ({@link #format}).
 * In either case, each item always starts on a new page
 * (the last page of each item closes at that item's end, as measured on 2026-09-02).
 * </p>
 *
 * <p>
 * In the sequential case the items share pages but stay separate documents (2026-10-08, from the EPUB brush-up
 * test): each item gets a fresh {@link net.zamasoft.foliojet.ua.DocumentContext} and its own style sheets
 * ({@link UserAgent#beginDocument}), element keys continue across items so two-pass facts do not collide,
 * one imposition serves the whole book (slug page numbers and n-up sheets continue), links between items become
 * internal links (resolved at output time against {@link net.zamasoft.foliojet.ua.UAContext#getDocumentSet}),
 * {@code page-spread-left/right} becomes a recto/verso break before the item, and the document information
 * comes from the package rather than from the items.
 * </p>
 */
public class EPubFormatter implements MultiDocumentFormatter {
	private static final Logger LOG = Logger.getLogger(EPubFormatter.class.getName());

	private static final String PLUGIN_NAME = "net.zamasoft.foliojet.plugins.epub";

	public static final BooleanPropManager REPLACE_NUMBERS = new BooleanPropManager(
			"x.net.zamasoft.foliojet.formatter.impl.epub.replace-numbers", false);

	/**
	 * The MIME type indicating that EPUB content is supplied as a directory.
	 * Retrieves only the required items under the base URI without sending a ZIP.
	 */
	public static final String DIRECTORY_MEDIA_TYPE = "application/epub+directory";

	public boolean match(final Source key) {
		final Source source = (Source) key;
		try {
			final String uri = source.getURI().toString();
			if (uri.length() >= 5 && uri.substring(uri.length() - 5).equalsIgnoreCase(".epub")) {
				return true;
			}
			// Directory format. A ".epub/" suffix allows selection by extension alone
			if (uri.length() >= 6 && uri.substring(uri.length() - 6).equalsIgnoreCase(".epub/")) {
				return true;
			}
			final String mimeType = source.getMimeType();
			if (mimeType != null
					&& (mimeType.equals("application/epub+zip") || mimeType.equals(DIRECTORY_MEDIA_TYPE))) {
				return true;
			}
			// For input whose extension and type do not identify it as EPUB (e.g., a URL with application/octet-stream),
			// treat it as EPUB if it is a ZIP whose first item, mimetype, contains application/epub+zip
			// (2026-09-02, cti.li handoff: it kept reading the input as HTML and never finished).
			// Only inspect input backed by a file (inspection would consume a stream)
			if (source.isFile() && looksLikeEpub(source.getFile())) {
				return true;
			}
		} catch (IOException e) {
			LOG.log(Level.WARNING, "変換元文書のMIME型を取得できませんでした", e);
		}
		return false;
	}

	/** Inspects the ZIP local header and the uncompressed {@code mimetype} item that OCF places first. */
	static boolean looksLikeEpub(final java.io.File file) {
		if (file == null || !file.isFile()) {
			return false;
		}
		final byte[] head = new byte[64];
		int n = 0;
		try (java.io.InputStream in = new java.io.FileInputStream(file)) {
			for (int r; n < head.length && (r = in.read(head, n, head.length - n)) != -1;) {
				n += r;
			}
		} catch (IOException e) {
			return false;
		}
		if (n < 40 || head[0] != 0x50 || head[1] != 0x4B || head[2] != 0x03 || head[3] != 0x04) {
			return false;
		}
		// Local header: name length (26-27), extra field length (28-29), name (30-), extra field, data
		final int nameLen = (head[26] & 0xFF) | ((head[27] & 0xFF) << 8);
		final int extraLen = (head[28] & 0xFF) | ((head[29] & 0xFF) << 8);
		final String s = new String(head, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
		if (nameLen != 8 || !s.startsWith("mimetype", 30)) {
			return false;
		}
		final int data = 30 + nameLen + extraLen;
		return data + 20 <= n && s.startsWith("application/epub+zip", data);
	}

	/** Opens an item by its path. This is the only difference between ZIP and directory input. */
	private interface EntryOpener {
		Source open(URI path, String mediaType) throws IOException;
	}

	/** An operation on an open EPUB. */
	private interface Body {
		void run(EPubFile epub, Contents contents, EntryOpener opener) throws Exception;
	}

	public void format(final Source source, final UserAgent ua) throws AbortException, TranscoderException {
		this.withArchive(source, ua, (epub, contents, opener) -> this.formatSequential(contents, opener, ua));
	}

	@Override
	public void formatDocuments(final Source source, final MultiDocumentOutput ua, final int passCount)
			throws AbortException, TranscoderException {
		this.withArchive(source, ua,
				(epub, contents, opener) -> this.formatIndependent(epub, contents, opener, ua, passCount));
	}

	/**
	 * Opens an EPUB (ZIP or directory), sets resource resolution on the UA, then runs the main operation.
	 * Preserves failure types: propagates abort and conversion exceptions unchanged,
	 * and wraps other exceptions as plugin failures.
	 */
	private void withArchive(final Source source, final UserAgent ua, final Body body)
			throws AbortException, TranscoderException {
		try {
			if (isDirectory(source)) {
				final URI base = toDirectoryURI(source.getURI());
				// References within an EPUB use relative URIs. Resolution relative to the base URI
				// serves the same role as the zip: scheme for ZIP input
				final BaseURISourceResolver entries = new BaseURISourceResolver(ua.getSourceResolver(), base);
				final CompositeSourceResolver resolver = new CompositeSourceResolver();
				resolver.setDefaultSourceResolver(entries);
				ua.setSourceResolver(resolver);
				this.open(new ResolvedArchiveFile(entries), (path, mediaType) -> {
					final Source entry = entries.resolve(path);
					return mediaType == null ? entry : new SourceWrapper(entry) {
						@Override
						public String getMimeType() {
							return mediaType;
						}
					};
				}, body);
				return;
			}
			final File epubFile;
			if (source.isFile()) {
				epubFile = source.getFile();
			} else {
				epubFile = File.createTempFile("epub", ".epub");
				try (final OutputStream out = new FileOutputStream(epubFile)) {
					final InputStream in = source.getInputStream();
					in.transferTo(out);
				}
			}
			try {
				try (final ZipFile zip = new ZipFile(epubFile)) {
					// Set the ZIP file as the data source
					final CompositeSourceResolver resolver = new CompositeSourceResolver();
					resolver.addSourceResolver("zip", new ZIPFileSourceResolver(zip));
					resolver.setDefaultSourceResolver(ua.getSourceResolver());
					resolver.setDefaultScheme("zip");
					ua.setSourceResolver(resolver);
					this.open(new ZipArchiveFile(epubFile, zip),
							(path, mediaType) -> new ZIPFileSource(zip, path, mediaType), body);
				}
			} finally {
				if (!source.isFile()) {
					epubFile.delete();
				}
			}
		} catch (final AbortException | TranscoderException e) {
			throw e;
		} catch (final Exception e) {
			throw pluginFailure(ua, e);
		}
	}

	private static TranscoderException pluginFailure(final UserAgent ua, final Throwable e) {
		final RetainedTextLimitException retained = RetainedTextLimitException.findIn(e);
		if (retained != null) throw retained;
		final ContinuationInvariantViolationException invariant = ContinuationInvariantViolationException.findIn(e);
		if (invariant != null) throw invariant;
		final short code = MessageCodes.ERROR_PLUGIN;
		final String[] args = new String[] { PLUGIN_NAME, e.getLocalizedMessage() };
		final String mes = MessageCodeUtils.toString(code, args);
		ua.message(code, args);
		LOG.log(Level.WARNING, mes, e);
		final TranscoderException failure = new TranscoderException(code, args, mes);
		failure.initCause(e);
		return failure;
	}

	private void open(final ArchiveFile archive, final EntryOpener opener, final Body body) throws Exception {
		// Parse metadata
		final EPubFile epub = new EPubFile(archive);
		final Container container = epub.readContainer();
		final Rootfile root = container.rootfiles[0];
		final Contents contents = epub.readContents(root);
		body.run(epub, contents, opener);
	}

	/** Maps page progression to {@code output.print-mode} and returns whether the binding is horizontal. */
	private static boolean applyProgression(final UserAgent ua, final Contents contents) {
		boolean leftBind = true;
		switch (contents.pageProgressionDirection) {
		case Contents.PAGE_PROGRESSION_DIRECTION_LTR:
			ua.setProperty(UAProps.OUTPUT_PRINT_MODE.getName(), "left-side");
			break;
		case Contents.PAGE_PROGRESSION_DIRECTION_RTL:
			ua.setProperty(UAProps.OUTPUT_PRINT_MODE.getName(), "right-side");
			leftBind = false;
			break;
		}
		return leftBind;
	}

	/**
	 * The page side an item must start on, as a {@code break-before} value: {@code page-spread-left/right}
	 * names the physical side of a spread, recto and verso depend on the binding (left binding: right pages
	 * are recto; right binding: left pages are recto). {@code null} when the item has no side.
	 */
	static String spreadBreak(final byte pageSpread, final boolean leftBind) {
		switch (pageSpread) {
		case ItemRef.PAGE_SPREAD_LEFT:
			return leftBind ? "verso" : "recto";
		case ItemRef.PAGE_SPREAD_RIGHT:
			return leftBind ? "recto" : "verso";
		default:
			return null;
		}
	}

	/**
	 * Whether an item is pre-paginated (fixed layout): {@code rendition:layout} of the package or the itemref's
	 * {@code rendition:layout-*} override; also the Sony e-book {@code layout:fixed-layout} that magazines from some
	 * distributors still use (2026-10-08: such a magazine's page images were sliced over three A4 pages).
	 */
	static boolean isFixedLayout(final Contents contents, final ItemRef ir) {
		if (ir.properties != null) {
			if (ir.properties.contains("rendition:layout-pre-paginated")) {
				return true;
			}
			if (ir.properties.contains("rendition:layout-reflowable")) {
				return false;
			}
		}
		final String layout = contents.getMeta("rendition:layout");
		if (layout != null) {
			return layout.trim().equals("pre-paginated");
		}
		final String sony = contents.getMeta("layout:fixed-layout");
		return sony != null && sony.trim().equals("true");
	}

	// ---- Feed all items sequentially to a single UA (PDF and images)

	private void formatSequential(final Contents contents, final EntryOpener opener, final UserAgent ua)
			throws Exception {
		final boolean leftBind = applyProgression(ua, contents);
		final boolean[] included = selectSpine(ua, contents);
		// Links into these documents are internal links (AbstractVisitor)
		final Set<URI> documents = new LinkedHashSet<>();
		for (int i = 0; i < contents.spine.length; ++i) {
			if (included[i]) {
				documents.add(URIHelper.create("UTF-8", contents.spine[i].item.fullPath));
			}
		}
		ua.getUAContext().setDocumentSet(Collections.unmodifiableSet(documents));
		// One imposition for the whole book: the slug page number and n-up sheets continue across items
		final Imposition imposition = Impositions.createImposition(ua);
		ua.getPassContext().setSharedImposition(imposition);
		final String useMetaInfo = ua.getProperty(UAProps.OUTPUT_USE_META_INFO.getName());
		final boolean packageInfo = UAProps.OUTPUT_USE_META_INFO.getBoolean(ua);
		if (packageInfo) {
			applyPackageInformation(ua, contents);
			// The items' <title> and <meta> are chapter titles, not the book's (restored below)
			ua.setProperty(UAProps.OUTPUT_USE_META_INFO.getName(), "false");
		}
		try {
			for (int i = 0; i < contents.spine.length; ++i) {
				if (!included[i]) {
					continue;
				}
				final ItemRef ir = contents.spine[i];
				this.formatItem(ua, ir, opener, spreadBreak(ir.pageSpread, leftBind), isFixedLayout(contents, ir));
			}
			// Closes the last n-up sheet once, at the end of the book (each item's PageSequence skips it)
			imposition.finish();
		} finally {
			ua.getPassContext().setSharedImposition(null);
			if (packageInfo) {
				ua.setProperty(UAProps.OUTPUT_USE_META_INFO.getName(), useMetaInfo);
			}
		}
	}

	/**
	 * Sets the document information from the package (dc:title, dc:creator, dc:description). The language becomes the
	 * default of {@code output.pdf.tagged.lang}, which tagged PDF uses as the document language.
	 */
	private static void applyPackageInformation(final UserAgent ua, final Contents contents) {
		if (contents.title != null && contents.title.text != null && !contents.title.text.isBlank()) {
			ua.meta("title", contents.title.text.trim());
		}
		final StringBuilder authors = new StringBuilder();
		for (final PropertiedString author : contents.author) {
			if (author != null && author.text != null && !author.text.isBlank()) {
				if (authors.length() != 0) {
					authors.append(", ");
				}
				authors.append(author.text.trim());
			}
		}
		if (authors.length() != 0) {
			ua.meta("author", authors.toString());
		}
		if (contents.description != null && contents.description.text != null
				&& !contents.description.text.isBlank()) {
			ua.meta("subject", contents.description.text.trim());
		}
		if (ua.getProperty(UAProps.OUTPUT_PDF_TAGGED_LANG.getName()) == null) {
			for (final PropertiedString language : contents.language) {
				if (language != null && language.text != null && !language.text.isBlank()) {
					ua.setProperty(UAProps.OUTPUT_PDF_TAGGED_LANG.getName(), language.text.trim());
					break;
				}
			}
		}
	}

	// ---- Independent items: Lay out in parallel with child UAs and release in spine order

	private void formatIndependent(final EPubFile epub, final Contents contents, final EntryOpener opener,
			final MultiDocumentOutput ua, final int passCount) throws Exception {
		applyProgression(ua, contents);
		final boolean[] included = selectSpine(ua, contents);
		final List<DocumentUnit> units = new ArrayList<>();
		int includedCount = 0;
		for (int i = 0; i < contents.spine.length; ++i) {
			final ItemRef ir = contents.spine[i];
			units.add(new DocumentUnit(i + 1, ir.item.id, URIHelper.create("UTF-8", ir.item.fullPath), included[i]));
			if (included[i]) {
				++includedCount;
			}
		}
		ua.describeDocuments(new DocumentSet(Collections.unmodifiableList(units), progressionName(contents),
				metadata(contents), toc(epub, contents)));
		if (includedCount == 0) {
			return;
		}

		// Submit items in spine order. Earlier items run first, allowing results to start releasing sooner
		final int concurrency = Math.min(includedCount, concurrency(ua));
		final LayoutThreadContext context = LayoutThreadContext.capture();
		final ExecutorService pool = Executors.newFixedThreadPool(concurrency, r -> {
			final Thread t = new Thread(null, r, "foliojet-epub-item", LayoutThreadContext.LAYOUT_STACK_SIZE);
			t.setDaemon(true);
			return t;
		});
		final List<Future<?>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < contents.spine.length; ++i) {
				if (!included[i]) {
					continue;
				}
				final ItemRef ir = contents.spine[i];
				final UserAgent child = ua.openDocument(units.get(i));
				futures.add(pool.submit(() -> {
					try (AutoCloseable scope = context.apply()) {
						this.formatItemPasses(child, ir, opener, passCount, isFixedLayout(contents, ir));
					} catch (final TranscoderException | RuntimeException | Error e) {
						// AbortException is a RuntimeException. Let it propagate unchanged
						throw e;
					} catch (final Exception e) {
						throw pluginFailure(child, e);
					}
					return null;
				}));
			}
			awaitAll(ua, futures);
		} finally {
			// Return only when no writers remain. Even on abort, wait for all children
			// to stop (prevent writes after the caller closes the session)
			pool.shutdownNow();
			boolean interrupted = false;
			while (true) {
				try {
					if (pool.awaitTermination(1, TimeUnit.SECONDS)) {
						break;
					}
				} catch (final InterruptedException e) {
					interrupted = true;
					ua.abort(AbortException.ABORT_FORCE);
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Waits for all items to finish. On the first failure, aborts the rest and throws that failure
	 * after all have stopped. Also maps an interrupt (deadline) to an abort and continues waiting.
	 */
	private static void awaitAll(final MultiDocumentOutput ua, final List<Future<?>> futures)
			throws AbortException, TranscoderException {
		Throwable failure = null;
		boolean interrupted = false;
		for (final Future<?> future : futures) {
			for (;;) {
				try {
					future.get();
					break;
				} catch (final InterruptedException e) {
					interrupted = true;
					ua.abort(AbortException.ABORT_FORCE);
				} catch (final ExecutionException e) {
					final Throwable cause = e.getCause();
					if (failure == null) {
						failure = cause;
						ua.abort(cause instanceof AbortException abort ? abort.getState()
								: AbortException.ABORT_FORCE);
					}
					break;
				} catch (final CancellationException e) {
					break;
				}
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		if (failure instanceof AbortException e) {
			throw e;
		}
		if (failure instanceof TranscoderException e) {
			throw e;
		}
		if (failure instanceof RuntimeException e) {
			throw e;
		}
		if (failure instanceof Error e) {
			throw e;
		}
		if (failure != null) {
			throw pluginFailure(ua, failure);
		}
		if (interrupted) {
			throw new AbortException(AbortException.ABORT_FORCE);
		}
	}

	/** The number of items to lay out concurrently. {@code 0} (default) uses the smaller of the core count and 4. */
	private static int concurrency(final UserAgent ua) {
		final int configured = UAProps.PROCESSING_CONCURRENCY.getInteger(ua);
		if (configured > 0) {
			return configured;
		}
		return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
	}

	/**
	 * Drives the passes for one item in the same order as {@code DirectSession.format}
	 * (structure scan → intermediate × n → final). Reopens the ZIP item for input, so no temporary file is needed.
	 */
	private void formatItemPasses(final UserAgent child, final ItemRef ir, final EntryOpener opener,
			final int passCount, final boolean fixedLayout) throws Exception {
		// Independent bundles have no spread to align with: page-spread is left to the reader (no blank pages)
		if (passCount <= 1) {
			child.prepare(PrepareMode.DOCUMENT);
			child.getUAContext().setPassCount(1);
			child.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(1));
			this.formatItem(child, ir, opener, null, fixedLayout);
		} else {
			child.prepare(PrepareMode.STRUCTURE_SCAN);
			this.formatItem(child, ir, opener, null, fixedLayout);
			for (int remaining = passCount; remaining > 1; --remaining) {
				child.prepare(PrepareMode.MIDDLE_PASS);
				child.getUAContext().setPassCount(remaining);
				child.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(remaining));
				this.formatItem(child, ir, opener, null, fixedLayout);
			}
			child.prepare(PrepareMode.LAST_PASS);
			child.getUAContext().setPassCount(1);
			child.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(1));
			this.formatItem(child, ir, opener, null, fixedLayout);
		}
		child.finish();
	}

	/**
	 * Lays out one item in the currently prepared pass.
	 *
	 * @param spreadBreak {@code recto} or {@code verso} when the item must start on that side, otherwise {@code null}
	 * @param fixedLayout whether the item is pre-paginated (fixed layout): its viewport is the page
	 */
	private void formatItem(final UserAgent ua, final ItemRef ir, final EntryOpener opener, final String spreadBreak,
			final boolean fixedLayout) throws Exception {
		ua.getPassContext().resetNonPageCounters();
		final URI path = URIHelper.create("UTF-8", ir.item.fullPath);
		ua.beginDocument(path);
		final Source zSource = opener.open(path, ir.item.mediaType);
		final String mimeType = zSource.getMimeType();
		if (mimeType.equals("application/xhtml+xml")) {
			ParserFactory pf = PluginRegistry.getInstance().search(ParserFactory.class, mimeType);
			Parser parser = pf.createParser();
			TranscoderHandler transcoderHandler = new TranscoderHandler(ua);
			XMLHandler entryPoint = transcoderHandler;
			final StringBuilder itemStyle = new StringBuilder();
			if (fixedLayout) {
				// The viewport is the page; the author's @page rules, which come later, still win
				itemStyle.append("@page{margin:0}");
			}
			if (spreadBreak != null) {
				itemStyle.append("body{break-before:").append(spreadBreak).append(" !important}");
			}
			if (itemStyle.length() != 0) {
				entryPoint = new ItemStyleFilter(entryPoint, itemStyle.toString());
			}
			// A pre-paginated item's <meta name="viewport"> sets the page size for that item only
			final String[] restore = fixedLayout ? new String[] { UAProps.INPUT_VIEWPORT.getName(),
					UAProps.OUTPUT_PAGE_WIDTH.getName(), UAProps.OUTPUT_PAGE_HEIGHT.getName() } : new String[0];
			final String[] saved = new String[restore.length];
			for (int i = 0; i < restore.length; ++i) {
				saved[i] = ua.getProperty(restore[i]);
			}
			if (fixedLayout) {
				ua.setProperty(UAProps.INPUT_VIEWPORT.getName(), "true");
			}
			if (REPLACE_NUMBERS.getBoolean(ua)) {
				entryPoint = XMLHandler.of(new WritingModeHandler(entryPoint, true), null);
			}
			try {
				parser.parse(ua, zSource, entryPoint);
			} catch (final SAXParseException e) {
				// Encrypted (DRM) books and broken items: one message naming the item (2026-10-08).
				// Previously the parser's "Content is not allowed in prolog" surfaced as a plugin failure with a stack trace.
				LOG.log(Level.FINE, "EPUB item is not XML: " + ir.item.fullPath, e);
				final short code = MessageCodes.ERROR_EPUB_ITEM_NOT_XML;
				final String[] args = { ir.item.fullPath, e.getMessage() };
				ua.message(code, args);
				final TranscoderException failure = new TranscoderException(code, args,
						MessageCodeUtils.toString(code, args));
				failure.initCause(e);
				throw failure;
			} finally {
				// E-6 increment 3b-2: Clean up spill temporary files (idempotent)
				transcoderHandler.dispose();
				for (int i = 0; i < restore.length; ++i) {
					ua.setProperty(restore[i], saved[i]);
				}
			}
		} else {
			Formatter formatter = PluginRegistry.getInstance().search(Formatter.class, zSource);
			formatter.format(zSource, ua);
		}
	}

	// ---- Spine filtering and overall description

	/**
	 * Selects items to lay out via {@code input.epub.spine}; empty means all items. Entries can be
	 * idrefs, paths, one-based indices, or index ranges. Warns about and ignores entries that match none of these.
	 */
	static boolean[] selectSpine(final UserAgent ua, final Contents contents) {
		final boolean[] included = new boolean[contents.spine.length];
		final String value = UAProps.INPUT_EPUB_SPINE.getString(ua);
		if (value == null || value.isBlank()) {
			java.util.Arrays.fill(included, true);
			return included;
		}
		for (final String token : value.trim().split("[\\s,]+")) {
			if (token.isEmpty()) {
				continue;
			}
			boolean matched = false;
			final java.util.regex.Matcher range = java.util.regex.Pattern.compile("(\\d+)(?:-(\\d+))?")
					.matcher(token);
			if (range.matches()) {
				final int from = Integer.parseInt(range.group(1));
				final int to = range.group(2) == null ? from : Integer.parseInt(range.group(2));
				for (int i = Math.max(1, from); i <= Math.min(contents.spine.length, to); ++i) {
					included[i - 1] = true;
					matched = true;
				}
			} else {
				for (int i = 0; i < contents.spine.length; ++i) {
					final Item item = contents.spine[i].item;
					if (token.equals(item.id) || token.equals(item.href) || token.equals(item.fullPath)
							|| (item.fullPath != null && item.fullPath.endsWith("/" + token))) {
						included[i] = true;
						matched = true;
					}
				}
			}
			if (!matched) {
				ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.INPUT_EPUB_SPINE.getName(), token);
			}
		}
		return included;
	}

	private static String progressionName(final Contents contents) {
		return switch (contents.pageProgressionDirection) {
		case Contents.PAGE_PROGRESSION_DIRECTION_LTR -> "ltr";
		case Contents.PAGE_PROGRESSION_DIRECTION_RTL -> "rtl";
		default -> "default";
		};
	}

	private static Map<String, String> metadata(final Contents contents) {
		final Map<String, String> metadata = new LinkedHashMap<>();
		put(metadata, "title", contents.title);
		put(metadata, "description", contents.description);
		put(metadata, "identifier", contents.identifier);
		put(metadata, "language", contents.language);
		put(metadata, "author", contents.author);
		put(metadata, "publisher", contents.publisher);
		put(metadata, "rights", contents.rights);
		return metadata;
	}

	private static void put(final Map<String, String> metadata, final String key, final PropertiedString value) {
		if (value != null && value.text != null && !value.text.isEmpty()) {
			metadata.put(key, value.text);
		}
	}

	private static void put(final Map<String, String> metadata, final String key,
			final List<PropertiedString> values) {
		if (values == null || values.isEmpty()) {
			return;
		}
		final StringBuilder joined = new StringBuilder();
		for (final PropertiedString value : values) {
			if (value == null || value.text == null || value.text.isEmpty()) {
				continue;
			}
			if (joined.length() != 0) {
				joined.append(", ");
			}
			joined.append(value.text);
		}
		if (joined.length() != 0) {
			metadata.put(key, joined.toString());
		}
	}

	/** The table of contents (nav/ncx). Empty if it cannot be read. */
	private static List<TocEntry> toc(final EPubFile epub, final Contents contents) {
		try {
			final Toc toc = epub.readToc(contents);
			if (toc == null || toc.navPoints == null) {
				return List.of();
			}
			return tocEntries(toc.navPoints);
		} catch (final Exception e) {
			LOG.log(Level.FINE, "EPUBの目次を読めませんでした", e);
			return List.of();
		}
	}

	private static List<TocEntry> tocEntries(final NavPoint[] points) {
		final List<TocEntry> entries = new ArrayList<>();
		if (points == null) {
			return entries;
		}
		for (final NavPoint point : points) {
			if (point == null) {
				continue;
			}
			URI uri = point.uri;
			if (point.item != null) {
				try {
					uri = URIHelper.create("UTF-8", point.item.fullPath);
				} catch (final URISyntaxException e) {
					// If the item's path is invalid, keep the URI that nav points to
				}
			}
			final String fragment = point.uri == null ? null : point.uri.getFragment();
			entries.add(new TocEntry(point.label, uri, fragment, tocEntries(point.children)));
		}
		return entries;
	}

	/** Whether the EPUB content is supplied as a directory. */
	private static boolean isDirectory(final Source source) {
		try {
			final String mimeType = source.getMimeType();
			if (DIRECTORY_MEDIA_TYPE.equals(mimeType)) {
				return true;
			}
		} catch (final IOException e) {
			// If the MIME type is unavailable, decide from the URI
		}
		final URI uri = source.getURI();
		if (uri == null) {
			return false;
		}
		final String path = uri.getPath();
		return path != null && path.endsWith("/");
	}

	/** Ensures a trailing {@code /}, since this URI serves as the base for relative resolution. */
	private static URI toDirectoryURI(final URI uri) {
		if (uri == null) {
			throw new IllegalArgumentException("EPUB directory URI is missing");
		}
		final String text = uri.toString();
		return text.endsWith("/") ? uri : URI.create(text + "/");
	}
}

/**
 * Gives an item a style sheet ahead of its own (2026-10-08), as a processing instruction before the root element.
 *
 * <ul>
 * <li>{@code page-spread-left/right}: {@code body { break-before: recto|verso !important }}. The existing left/right
 * page break at the start of a document adds a blank page only when the side does not match, and that page goes
 * through the page sequence (the item's {@code @page} size, the imposition, {@code :blank}). Previously the formatter
 * drew its own blank page of the default size, compared against the opposite side, and did not advance the page
 * side, so books bound on the left got a blank page before every item.</li>
 * <li>Fixed layout: {@code @page { margin: 0 }}, so the viewport-sized page holds the page content.</li>
 * </ul>
 */
final class ItemStyleFilter extends DefaultXMLHandlerFilter {
	private final String css;
	private boolean started = false;

	ItemStyleFilter(final XMLHandler handler, final String css) {
		super(handler);
		this.css = css;
	}

	@Override
	public void startElement(final String uri, final String lName, final String qName, final Attributes atts)
			throws SAXException {
		if (!this.started) {
			this.started = true;
			super.processingInstruction(CSSJML.PI_STYLESHEET, "[" + XMLUtils.escapePseudeData(this.css) + "]");
		}
		super.startElement(uri, lName, qName, atts);
	}
}
