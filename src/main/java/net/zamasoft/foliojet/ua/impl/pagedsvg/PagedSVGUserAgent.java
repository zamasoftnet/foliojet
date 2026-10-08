package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.BoundSide;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.ImageMetricsIO;
import net.zamasoft.foliojet.ua.MultiDocumentOutput;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.RandomResultUserAgent;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.AbstractUserAgent;
import net.zamasoft.foliojet.ua.impl.NopVisitor;
import net.zamasoft.foliojet.ua.props.PagedSvgCompression;
import net.zamasoft.foliojet.ua.props.PagedSvgFontScope;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;

/**
 * Produces stable URI-addressed pages, shared WOFF2 subsets and image assets.
 *
 * <p>
 * <b>EPUB outputs each item (spine XHTML) independently</b> (2026-09-02,
 * {@link MultiDocumentOutput}). The parent UA opens a child UA per item; each child emits
 * a bundle in the same form as a standalone document (its own {@code manifest.json}, fonts,
 * and images) under {@code items/NNNN/}. The parent writes only {@code index.json}
 * (item order, cumulative page numbers, and table of contents) at the end.
 * {@link DocumentRelease} releases results in spine order, so even with parallel item layout
 * the consumer receives the same sequence as with sequential layout.
 * </p>
 */
public class PagedSVGUserAgent extends AbstractUserAgent implements RandomResultUserAgent, MultiDocumentOutput {
	private ResultSink sink, savedSink;
	private boolean middleStateSaved;
	private FontManagerImpl fontManager;
	private PagedSVGResources.PageData currentPage;
	/** Whether to compress output. Affects only page SVG and page JSON. */
	private PagedSvgCompression compression = PagedSvgCompression.NONE;
	/** Used only for the custom writer. Null for Batik. */
	private java.io.StringWriter directBuffer;
	private SVGPageOutput directPage;
	private PagedSVGResources resources;
	private PagedSVGVisitor visitor;
	private int page;

	// ---- Page numbers for one-pass target-counter() (2026-10-04, docs/design/one-pass-target-counter-design.md §8)

	/**
	 * Recorder for the current page's drawing operations. For documents with slots
	 * ({@code TargetCounterSlotImage}), record pages first; on closing, immediately replay and
	 * emit the page if no slots remain unresolved.
	 */
	private net.zamasoft.pdfg2d.gc.RecorderGC recorder;

	/** A page whose output is deferred because it has unresolved slots. */
	private record HeldPage(PagedSVGResources.PageData page, net.zamasoft.pdfg2d.gc.RecorderGC.Page recording,
			List<net.zamasoft.foliojet.layout.box.impl.TargetCounterSlotImage> slots) {
		boolean resolved() {
			for (final var slot : this.slots) {
				if (!slot.isResolved()) {
					return false;
				}
			}
			return true;
		}
	}

	private final List<HeldPage> heldPages = new ArrayList<>();

	/**
	 * Maximum number of deferred pages. Recordings live in memory, so even a sequence of pages
	 * referencing distant pages must not accumulate without bound. Above the limit, emit the oldest
	 * pages with the numbers known so far (unresolved slots remain empty).
	 */
	private static final int MAX_HELD_PAGES = 64;
	private final Map<String, String> metadata = new LinkedHashMap<>();

	// ---- Simultaneous PDF output (2026-09-03, cti.li request: "both PDF and Paged SVG in one conversion")

	/**
	 * Companion UA that also writes PDF from the same layout.
	 * Created on the first page with {@code output.paged-svg.pdf=true}.
	 */
	private net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent pdfCompanion;
	/** Temporary storage for the companion PDF (emitted as one result at the end; never keeps two results open at once). */
	private java.io.File pdfSpool;
	/** Name of the PDF in the results. */
	static final String PDF_URI = "document.pdf";

	/**
	 * Whether to <b>return everything in one ZIP</b> (B-2, 2026-08-29). A normal bundle has
	 * multiple results and cannot be received through a sessionless, single-request REST call
	 * ({@code POST /transcode}; returns 4001). A ZIP is one result and can be returned directly via REST.
	 */
	private final boolean zipBundle;

	/** Result URI and media type when returning a ZIP. */
	static final String BUNDLE_URI = ResultSink.ZipSink.BUNDLE_URI;

	static final String BUNDLE_MEDIA_TYPE = ResultSink.ZipSink.BUNDLE_MEDIA_TYPE;

	// ---- State as the parent of multiple documents (EPUB)
	/** Session message consumer. Child messages reach it through the release stage. */
	private MessageHandler sessionMessages;
	private DocumentRelease release;
	private DocumentSet documents;
	private final List<PagedSVGUserAgent> children = new ArrayList<>();
	/** Completed item index → page count. */
	private final Map<Integer, Integer> pageCounts = new TreeMap<>();
	/** Completed item index → binding direction. index.json uses the first item's value. */
	private final Map<Integer, BoundSide> bindings = new TreeMap<>();
	/** Abort request received by the parent. Also forwarded to children opened later. */
	private volatile byte abortRequested = 0;

	// ---- State as a child (item)
	private final PagedSVGUserAgent parent;
	private final DocumentUnit unit;
	private final DocumentRelease.Unit releaseUnit;

	public PagedSVGUserAgent() {
		this(false);
	}

	public PagedSVGUserAgent(final boolean zipBundle) {
		this.zipBundle = zipBundle;
		this.parent = null;
		this.unit = null;
		this.releaseUnit = null;
		this.resetOutput();
	}

	/** Child that lays out an EPUB item. Results go to the parent's release stage. */
	private PagedSVGUserAgent(final PagedSVGUserAgent parent, final DocumentUnit unit,
			final DocumentRelease.Unit releaseUnit) {
		this.zipBundle = parent.zipBundle;
		this.parent = parent;
		this.unit = unit;
		this.releaseUnit = releaseUnit;
		this.sink = new ChildSink(releaseUnit);
		this.resetOutput();
	}

	@Override
	public void setResults(final Results results) {
		this.sink = this.zipBundle ? new ResultSink.ZipSink(results) : new ResultSink.ResultsSink(results);
		this.resetOutput();
	}

	@Override
	public void setMessageHandler(final MessageHandler messageHandler) {
		super.setMessageHandler(messageHandler);
		this.sessionMessages = messageHandler;
	}

	/**
	 * An item's UA reports a style sheet warning only if no item of the book has (2026-10-08): the items share their
	 * style sheets, and the same warning came once per item.
	 */
	@Override
	protected net.zamasoft.foliojet.ua.UAContext warningContext() {
		return this.parent != null ? this.parent.getUAContext() : super.warningContext();
	}

	@Override
	public void prepare(final PrepareMode mode) {
		super.prepare(mode);
		switch (mode) {
		case MIDDLE_PASS -> {
			if (!this.middleStateSaved) {
				this.savedSink = this.sink;
				this.middleStateSaved = true;
			}
			this.sink = ResultSink.NopSink.INSTANCE;
			this.resetOutput();
		}
		case LAST_PASS -> {
			if (this.middleStateSaved) {
				this.sink = this.savedSink;
				this.savedSink = null;
				this.middleStateSaved = false;
			}
			this.resetOutput();
		}
		default -> {
			// keep the current document state
		}
		}
	}

	private void resetOutput() {
		this.directBuffer = null;
		this.directPage = null;
		this.currentPage = null;
		this.recorder = null;
		this.heldPages.clear();
		this.page = 0;
		this.metadata.clear();
		this.resources = new PagedSVGResources(this::emit, this.getUAContext().getPagedSvgFontCarry());
		if (this.unit != null && this.unit.uri() != null) {
			// Carryover keys are per item. Even with the same font, glyph order differs between chapters.
			this.resources.setDocument(this.unit.uri().toString());
		}
		// Record the resource identity of drawn images in the dimension table (2026-08-28).
		// Given this table, the next reconversion can write the same references without opening images.
		this.resources.setAssetRecorder((uri, asset) -> this.getUAContext().getImageMetrics().putAsset(uri.toString(),
				new net.zamasoft.foliojet.ua.ImageMetricsCache.Asset(asset.sha256(), asset.mediaType(),
						extensionOf(asset.uri()), asset.width(), asset.height())));
		this.visitor = null;
	}

	/** Extracts the extension from a resource URI ({@code assets/images/<sha>.<ext>}). */
	private static String extensionOf(final String uri) {
		final int dot = uri.lastIndexOf('.');
		return dot < 0 ? "bin" : uri.substring(dot + 1);
	}

	// ---- Parent of multiple documents (EPUB)

	@Override
	public void describeDocuments(final DocumentSet documents) {
		if (this.sink == null) {
			throw new IllegalStateException("Results is not set");
		}
		this.documents = documents;
		this.release = new DocumentRelease(this.sink, this.sessionMessages);
	}

	@Override
	public UserAgent openDocument(final DocumentUnit unit) {
		if (this.release == null) {
			throw new IllegalStateException("describeDocuments() must precede openDocument()");
		}
		final DocumentRelease.Unit releaseUnit = this.release.open(PagedSvgIndex.itemPrefix(unit.index()));
		final PagedSVGUserAgent child = new PagedSVGUserAgent(this, unit, releaseUnit);
		// Use the parent's settings, resource resolution, and fonts. Pass a copy of properties
		// (document PIs change them, so each item needs a separate table).
		child.setProperties(this.getProperties());
		child.setOperatorLimits(this.getOperatorLimits());
		child.setSourceResolver(this.getSourceResolver());
		child.setMessageHandler(releaseUnit::message);
		child.getUAContext().setFontSourceManager(this.getUAContext().getFontSourceManager());
		child.getUAContext().setPagedSvgFontCarry(this.getUAContext().getPagedSvgFontCarry());
		// The carryover cache was replaced, so recreate the registry.
		child.resetOutput();
		synchronized (this.children) {
			this.children.add(child);
			if (this.abortRequested != 0) {
				child.abort(this.abortRequested);
			}
		}
		return child;
	}

	/** The child has finished layout. Record its page count and binding direction for index.json. */
	private void childFinished(final PagedSVGUserAgent child) {
		synchronized (this.children) {
			this.pageCounts.put(child.unit.index(), child.page);
			this.bindings.put(child.unit.index(), child.getBoundSide());
			// Aggregate the buffering high-water mark in the parent as a book-wide diagnostic (B3, 2026-09-06).
			this.getRetainedTextLimit().mergeHighWater(child.getRetainedTextLimit());
		}
	}

	@Override
	public void abort(final byte mode) {
		super.abort(mode);
		this.abortRequested = mode;
		if (this.pdfCompanion != null) {
			this.pdfCompanion.abort(mode);
		}
		synchronized (this.children) {
			for (final PagedSVGUserAgent child : this.children) {
				child.abort(mode);
			}
		}
	}

	/** Destination for child results and messages. {@link #end()} notifies the parent of item completion. */
	private final class ChildSink implements ResultSink {
		private final DocumentRelease.Unit unit;

		ChildSink(final DocumentRelease.Unit unit) {
			this.unit = unit;
		}

		@Override
		public OutputStream open(final String uri, final String mimeType) throws IOException {
			return this.unit.open(uri, mimeType);
		}

		@Override
		public void end() throws IOException {
			this.unit.done(PagedSVGUserAgent.this.page);
		}
	}

	// ---- Images

	/**
	 * Attaches the source URI to an image (2026-08-28). Needed to associate the resource identity
	 * determined during drawing with the image's source URI in {@code metrics.json}.
	 * Wrapping does not change drawing behavior
	 * ({@link SourcedImage}).
	 */
	@Override
	public net.zamasoft.pdfg2d.gc.image.Image getImage(final URI uri,
			final net.zamasoft.zstream.resolver.Source source) throws IOException {
		final net.zamasoft.pdfg2d.gc.image.Image image = super.getImage(uri, source);
		if (image == null || uri == null) {
			return image;
		}
		// **Record dimensions during the drawing pass as well** (2026-08-28). The base class
		// only covers measurement passes, so single-pass conversions left the dimension table empty,
		// emitted no metrics.json, and retained nothing usable for the next reconversion.
		// These records hold only dimensions, not pixels, so their size is negligible.
		if (!"data".equalsIgnoreCase(uri.getScheme())) {
			this.getUAContext().getImageMetrics().putSize(uri.toString(), image.getWidth(), image.getHeight());
		}
		final SourcedImage sourced = new SourcedImage(image, uri);
		if (this.parent == null && UAProps.OUTPUT_PAGED_SVG_PDF.getBoolean(this)) {
			// Simultaneous PDF output: let the companion load images from the same source (PDF deduplicates by URI
			// and embeds original JPEG bytes. Passing the primary UA's pixels recompressed and embedded each use,
			// increasing a real document from 2.0 MB to 6.7 MB).
			try {
				sourced.companion = this.pdfCompanion().getImage(uri, source);
			} catch (final IOException | RuntimeException e) {
				sourced.companion = null;
			}
		}
		return sourced;
	}

	/**
	 * Does not open resources when dimensions alone suffice.
	 *
	 * <p>
	 * The base class consults the dimension table only in measurement and structural scan passes,
	 * but Paged SVG can use it <b>during the drawing pass too</b> (2026-08-28). Page references are
	 * {@code assets/images/<sha256>.<ext>}; if the previous {@code metrics.json} also recorded
	 * that identity, the same references can be written without opening images.
	 * Settings that re-emit data need pixels, so restrict this to
	 * {@code resources=omit} with direct output.
	 * </p>
	 */
	@Override
	public net.zamasoft.pdfg2d.gc.image.Image getImageMetrics(final URI uri) {
		final net.zamasoft.pdfg2d.gc.image.Image known = super.getImageMetrics(uri);
		if (known != null || uri == null) {
			return known;
		}
		if (this.isMeasurePass() || this.isStructureScanPass()) {
			return null;
		}
		if (UAProps.OUTPUT_PAGED_SVG_RESOURCES.get(this) != net.zamasoft.foliojet.ua.props.PagedSvgResourceMode.OMIT) {
			return null;
		}
		final var metrics = this.getUAContext().getImageMetrics();
		final var asset = metrics.getAsset(uri.toString());
		final var size = metrics.get(uri.toString());
		if (asset == null || size == null) {
			return null;
		}
		return new KnownAssetImage(size.getWidth(), size.getHeight(), asset);
	}

	/**
	 * Emits images as resources <b>with their original bytes unchanged</b> (2026-08-28).
	 * Page SVG only references {@code assets/images/<sha256>.<ext>}, so JPEGs need not be re-encoded as PNGs.
	 */
	@Override
	public boolean keepsEncodedImages() {
		return true;
	}

	/**
	 * Defers pages with unresolved slots and draws them when values become available (2026-10-04, §8).
	 * Only for standalone documents (the parent manages page release order for EPUB items).
	 */
	@Override
	public boolean paintsPageNumbersLater() {
		return this.parent == null && this.release == null;
	}

	@Override
	public FontManager getFontManager() {
		if (this.fontManager == null) {
			if (this.parent == null && UAProps.OUTPUT_PAGED_SVG_PDF.getBoolean(this)
					&& this.pdfCompanion().getFontManager() instanceof final FontManagerImpl pdfFonts) {
				// Simultaneous PDF output (2026-09-03): share the font store with the companion PDF. PDF assigns
				// font resource names in its own store, and embedded font glyph IDs are sequential
				// numbers within subsets, so text cannot be passed to PDF with separate stores
				// (missing resource names cause NPEs, and mismatched glyph IDs garble text). With one store,
				// the same shaped Text can be drawn to both outputs.
				this.fontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager(),
						pdfFonts.getFontStore());
			} else {
				this.fontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager());
			}
		}
		return this.fontManager;
	}

	/**
	 * Default font policy for this output. <b>SVG defaults to embedding</b>
	 * (2026-08-28).
	 *
	 * <p>
	 * The shared default is {@code output.pdf.fonts.policy}=cid-keyed, meaning
	 * "reference as an external CID-keyed font in PDF". SVG has no such mechanism,
	 * so SVG output can only fall back to outlining all glyphs as paths.
	 * Measurements (ja.wikipedia "地方病", 68 pages):
	 * cid-keyed produced 141.3 MB in 13.5 seconds versus embedded at 32.8 MB in 8.9 seconds,
	 * a 4.3-fold output size difference and a 34% generation-time difference.
	 * Embedding also emits characters as {@code <text>}, allowing selection and search.
	 * </p>
	 *
	 * <p>
	 * Fonts that prohibit embedding (OS/2 fsType) or whose glyphs cannot be copied
	 * still fall back to outlines as before, so licensing implications do not change.
	 * Honor {@code output.pdf.fonts.policy} if the user sets it explicitly.
	 * </p>
	 */
	@Override
	protected boolean embedsFontsByDefault() {
		return true;
	}

	@Override
	public void meta(final String name, final String content) {
		if (name != null && content != null) {
			this.metadata.put(name, content);
			if (this.pdfCompanion != null) {
				this.pdfCompanion.meta(name, content);
			}
		}
	}

	// ---- Pages

	@Override
	protected GC nextPage() {
		this.checkAbort(CTISession.ABORT_FORCE);
		if (this.isMeasurePass() || this.isStructureScanPass()) {
			this.noteProgress();
			return null;
		}
		final int number = ++this.page;
		if (number == 1) {
			this.resources.setResourceMode(UAProps.OUTPUT_PAGED_SVG_RESOURCES.get(this));
			this.resources.setBaseUri(this.baseUri());
			this.resources.setFontScope(UAProps.OUTPUT_PAGED_SVG_FONT_SCOPE.get(this));
			this.resources.setImagePolicy(UAProps.OUTPUT_PAGED_SVG_IMAGE_COMPRESSION.get(this),
					UAProps.OUTPUT_PAGED_SVG_IMAGE_COMPRESSION_LOSSLESS.getInteger(this),
					UAProps.OUTPUT_PAGED_SVG_IMAGE_MAX_WIDTH.getInteger(this),
					UAProps.OUTPUT_PAGED_SVG_IMAGE_MAX_HEIGHT.getInteger(this));
			this.resources.setPageChecksums(UAProps.OUTPUT_PAGED_SVG_PAGE_CHECKSUMS.getBoolean(this));
			this.resources.setRasterPixelLimit(UAProps.OUTPUT_IMAGE_PIXEL_LIMIT.getLong(this));
			// Do not compress contents when returning ZIP: ZIP already compresses them, so this would be redundant,
			// and names should be directly openable after extraction (.svg/.json).
			this.compression = this.zipBundle ? PagedSvgCompression.NONE
					: UAProps.OUTPUT_PAGED_SVG_COMPRESSION.get(this);
			// Emit subsets from the previous conversion **before the first page** (2026-08-29).
			// When relaying out the same book with only font size changed, the consumer can render
			// in the intended faces starting with the first page.
			try {
				this.resources.emitCarriedFonts();
			} catch (final IOException e) {
				throw new GraphicsException(e);
			}
		}
		this.currentPage = new PagedSVGResources.PageData(number, this.pageWidth, this.pageHeight);
		final GC svgGc;
		if (this.getUAContext().hasTargetCounterSlots()
				&& net.zamasoft.foliojet.layout.box.impl.TargetCounterSlotImage.available(this)) {
			// Documents with slots: record the page first. Defer output if slot values are unresolved
			// (§8). The recorder's supports() returns the same answer as the SVG GC (including nested groups).
			// Otherwise, recording takes an approximate drawing path, which replay cannot undo.
			this.recorder = new net.zamasoft.pdfg2d.gc.RecorderGC(this.getFontManager(),
					DirectSVGGC::supportsCapability);
			svgGc = this.recorder;
		} else {
			svgGc = this.openDirectPage(this.currentPage);
		}
		if (this.parent == null && UAProps.OUTPUT_PAGED_SVG_PDF.getBoolean(this)) {
			final GC pdfGc = this.pdfCompanion().nextPage(this.pageWidth, this.pageHeight);
			if (pdfGc != null) {
				return new TeeGC(svgGc, pdfGc);
			}
		}
		return svgGc;
	}

	/**
	 * Opens a page that writes without building a DOM. Page contents are not final yet,
	 * so write to the result when closing (to compute a streaming hash, each result must
	 * be written completely in a single stream).
	 */
	private GC openDirectPage(final PagedSVGResources.PageData page) {
		try {
			this.directBuffer = new java.io.StringWriter(1 << 14);
			this.directPage = new SVGPageOutput(this.directBuffer, page.width, page.height);
			final String base = this.resources.getBaseUri();
			this.directPage.writer().setFontSrc(uri -> base + uri);
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
		return new DirectPagedSVGGC(this.directPage.writer(), this.getFontManager(), this.resources, page);
	}

	/** Replays a recorded page to SVG and emits it. */
	private void drawRecordedPage(final PagedSVGResources.PageData page,
			final net.zamasoft.pdfg2d.gc.RecorderGC.Page recording) throws IOException {
		this.currentPage = page;
		try {
			recording.drawTo(this.openDirectPage(page));
			this.closeDirectPage();
		} finally {
			this.directBuffer = null;
			this.directPage = null;
			this.currentPage = null;
		}
	}

	/**
	 * Emits deferred pages whose slot values are all available. With {@code force}, emits all
	 * (at document end; slots without targets remain empty).
	 */
	private void flushHeldPages(final boolean force) throws IOException {
		if (this.heldPages.isEmpty()) {
			return;
		}
		final var context = this.getUAContext();
		context.setDrawingHeldPages(force);
		try {
			for (final var i = this.heldPages.iterator(); i.hasNext();) {
				final HeldPage held = i.next();
				if (force || held.resolved()) {
					i.remove();
					this.drawRecordedPage(held.page(), held.recording());
				}
			}
		} finally {
			context.setDrawingHeldPages(false);
		}
	}

	/** Emits old pages above the deferral limit with known numbers (unresolved slots empty; warns once). */
	private void releaseOldestHeldPage() throws IOException {
		final HeldPage held = this.heldPages.remove(0);
		final var context = this.getUAContext();
		if (context.getReportedApproximations().add("target-counter() 2822.target-counter-held")) {
			this.message(net.zamasoft.foliojet.message.MessageCodes.WARN_APPROXIMATED_RENDERING,
					"target-counter()", UAProps.OUTPUT_TYPE.getString(this),
					net.zamasoft.foliojet.message.MessageCodeUtils.detail("2822.target-counter-held"));
		}
		context.setDrawingHeldPages(true);
		try {
			this.drawRecordedPage(held.page(), held.recording());
		} finally {
			context.setDrawingHeldPages(false);
		}
	}

	/**
	 * Creates the companion PDF UA on first use. Copies input state (resource resolution, properties,
	 * font sources, base URI, metadata, and binding), and writes results to a temporary file.
	 * Shared font sources let PDF write the text shaped for page SVG directly as text
	 * (the default font policy is embedding, as for page SVG).
	 */
	private net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent pdfCompanion() {
		if (this.pdfCompanion != null) {
			return this.pdfCompanion;
		}
		final net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent pdf = (net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent) new net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgentFactory()
				.createUserAgent();
		pdf.setSourceResolver(this.getSourceResolver());
		pdf.setMessageHandler(this.sessionMessages);
		final Map<String, String> props = new java.util.HashMap<>(this.getProperties());
		props.putIfAbsent(UAProps.OUTPUT_PDF_FONTS_POLICY.name, "core,embedded");
		pdf.setProperties(props);
		pdf.setOperatorLimits(this.getOperatorLimits());
		pdf.getUAContext().setFontSourceManager(this.getUAContext().getFontSourceManager());
		try {
			// Delete after layout and during cleanup (deleteOnExit only grows the exit-time list in a long-running server).
			this.pdfSpool = java.io.File.createTempFile("copper-paged-svg-", ".pdf");
			final net.zamasoft.zstream.io.FragmentedOutput spool = new net.zamasoft.zstream.io.impl.FileFragmentedOutput(
					this.pdfSpool);
			pdf.setResults(new Results() {
				@Override
				public boolean hasNext() {
					return true;
				}

				@Override
				public net.zamasoft.zstream.io.FragmentedOutput nextBuilder(final net.zamasoft.zstream.resolver.SourceMetadata metadata) {
					return spool;
				}

				@Override
				public void end() {
					// The caller (finish) moves it to the result set.
				}
			});
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
		pdf.prepare(PrepareMode.DOCUMENT);
		pdf.getDocumentContext().setBaseURI(this.getDocumentContext().getBaseURI());
		pdf.setBoundSide(this.getBoundSide());
		pdf.setPageProgression(this.getPageProgression());
		for (final var e : this.metadata.entrySet()) {
			pdf.meta(e.getKey(), e.getValue());
		}
		this.pdfCompanion = pdf;
		return pdf;
	}

	/** Closes the companion PDF and moves it to the result set as one result ({@link #PDF_URI}). */
	private void finishPdfCompanion() throws BrokenResultException, IOException {
		final net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent pdf = this.pdfCompanion;
		if (pdf == null) {
			return;
		}
		this.pdfCompanion = null;
		try {
			pdf.finish();
		} finally {
			pdf.dispose();
		}
		try (var in = new java.io.FileInputStream(this.pdfSpool);
				var out = this.sink.open(PDF_URI, "application/pdf")) {
			in.transferTo(out);
		} finally {
			this.pdfSpool.delete();
			this.pdfSpool = null;
		}
		this.resources.setPdfUri(PDF_URI);
	}

	/**
	 * Prefix for shared resource references from page SVG.
	 *
	 * <p>
	 * The default {@code ../} goes up from {@code pages/} to the bundle root, and also reaches
	 * the item root for EPUB items ({@code items/NNNN/pages/}).
	 * For an absolute URL prefix, append the item's path
	 * ({@code https://example.com/book/} → {@code https://example.com/book/items/0003/}).
	 * </p>
	 */
	private String baseUri() {
		final String base = normaliseBaseUri(UAProps.OUTPUT_PAGED_SVG_BASE_URI.getString(this));
		if (this.releaseUnit == null || base.isEmpty() || base.startsWith(".")) {
			return base;
		}
		return base + this.releaseUnit.prefix;
	}

	@Override
	public void closePage(final GC gc) throws IOException {
		super.closePage(gc);
		if (gc == null) {
			return;
		}
		if (gc instanceof final TeeGC tee && this.pdfCompanion != null) {
			this.pdfCompanion.closePage(tee.secondary());
		}
		try {
			if (this.recorder != null) {
				final net.zamasoft.pdfg2d.gc.RecorderGC.Page recording = this.recorder.getPage();
				this.recorder = null;
				final var slots = net.zamasoft.foliojet.layout.box.impl.TargetCounterSlotImage.slots(recording);
				final HeldPage held = new HeldPage(this.currentPage, recording, slots);
				if (held.resolved()) {
					this.drawRecordedPage(held.page(), recording);
				} else {
					this.heldPages.add(held);
					if (this.heldPages.size() > MAX_HELD_PAGES) {
						this.releaseOldestHeldPage();
					}
				}
			} else {
				this.closeDirectPage();
			}
			this.flushHeldPages(false);
		} catch (final IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.directBuffer = null;
			this.directPage = null;
			this.currentPage = null;
		}
		this.checkAbort(CTISession.ABORT_NORMAL);
	}

	/**
	 * Closes a page from the custom writer.
	 *
	 * <p>
	 * Assemble contents in {@link java.io.StringWriter} and write them in one operation.
	 * No DOM is built, but <b>computing SHA-256 while streaming requires writing a complete
	 * result in one stream</b>. This still uses less memory than the Batik version,
	 * which retained the entire page DOM.
	 * </p>
	 */
	private void closeDirectPage() throws IOException {
		this.directPage.close();
		final String svg = this.directBuffer.toString();
		this.directBuffer = null;
		this.directPage = null;

		// **Emit the page's fonts before its page SVG** (2026-09-02,
		// font-scope: page). The consumer has the glyphs when the page arrives.
		if (this.resources.getFontScope() == PagedSvgFontScope.PAGE) {
			this.resources.closeFontScope();
		}

		final String stem = String.format(Locale.ROOT, "pages/%04d", this.currentPage.number);
		final String svgUri = this.pageUri(stem, ".svg");
		final String svgSha = this.emit(svgUri, "image/svg+xml", out -> {
			try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
				writer.write(svg);
			}
		});
		final String jsonUri = this.pageUri(stem, ".json");
		final PagedSVGResources.PageData page = this.currentPage;
		final String jsonSha = this.emit(jsonUri, "application/json", out -> {
			try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
				page.writeJson(writer);
			}
		});
		this.resources.addPage(new PagedSVGResources.PageAsset(this.currentPage.number, this.currentPage.width,
				this.currentPage.height, svgUri, svgSha, jsonUri, jsonSha));
	}

	/**
	 * Normalizes {@code output.paged-svg.base-uri} for use as a prefix.
	 * Empty (or unspecified) means no prefix; append a trailing {@code /} if absent.
	 */
	private static String normaliseBaseUri(final String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.endsWith("/") ? value : value + "/";
	}

	// ---- Result output

	/** Writes the contents of one result. Write directly to the supplied output without buffering. */
	@FunctionalInterface
	interface ContentWriter {
		void write(OutputStream out) throws IOException;
	}

	private void emit(final String uri, final String mimeType, final byte[] bytes) throws IOException {
		this.emit(uri, mimeType, out -> out.write(bytes));
	}

	/**
	 * Writes one result and returns its SHA-256.
	 *
	 * <p>
	 * <b>Does not buffer the contents</b> (2026-08-16). Computes the digest while writing
	 * and does not declare a length ({@code -1}). Previously, the entire page SVG was built in
	 * {@code ByteArrayOutputStream} before writing. Image output and single SVG output
	 * already wrote directly; only this path buffered.
	 * </p>
	 *
	 * <p>
	 * The returned SHA-256 covers <b>the bytes actually written</b>, keeping the manifest consistent
	 * with the data. {@link ResultSink} hides the destination (result set, ZIP, or EPUB release stage);
	 * this method handles only hashing and gzip.
	 * </p>
	 */
	private String emit(final String uri, final String mimeType, final ContentWriter content) throws IOException {
		if (this.sink == null) {
			throw new IOException("Results is not set");
		}
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		try (OutputStream raw = this.sink.open(uri, mimeType)) {
			// Compute SHA-256 over **the bytes actually passed on**. For compression,
			// place the digest outside (at the gzip output). The consumer can check
			// the manifest value directly against the saved file.
			final OutputStream digested = new DigestOutputStream(raw, digest);
			if (this.isCompressed(uri)) {
				// GZIPOutputStream must close to write its trailer,
				// but must not close the underlying stream.
				try (var gzip = new GZIPOutputStream(new UnclosableOutputStream(digested))) {
					content.write(new UnclosableOutputStream(gzip));
				}
			} else {
				content.write(new UnclosableOutputStream(digested));
			}
			digested.flush();
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/**
	 * Whether to return this result compressed with gzip.
	 *
	 * <p>
	 * Compress only textual page SVG and page JSON. Shared WOFF2 and PNG/JPEG are already
	 * compressed and will not shrink; double wrapping also adds work for the consumer.
	 * Leave {@code manifest.json} unchanged because it is the entry point.
	 * </p>
	 */
	private boolean isCompressed(final String uri) {
		return this.compression == PagedSvgCompression.GZIP
				&& (uri.endsWith(".svgz") || uri.endsWith(".json.gz"));
	}

	/** Uses {@code .svgz}/{@code .json.gz} when compression is enabled; otherwise leaves the name unchanged. */
	private String pageUri(final String stem, final String extension) {
		if (this.compression != PagedSvgCompression.GZIP) {
			return stem + extension;
		}
		return stem + (".svg".equals(extension) ? ".svgz" : extension + ".gz");
	}

	/**
	 * A wrapper that keeps the underlying stream open even when the writer calls {@code close()}.
	 * This lets {@code try} close {@code OutputStreamWriter} to flush all contents reliably,
	 * while result boundaries are closed by our own procedure.
	 */
	private static final class UnclosableOutputStream extends FilterOutputStream {
		UnclosableOutputStream(final OutputStream out) {
			super(out);
		}

		@Override
		public void write(final byte[] b, final int off, final int len) throws IOException {
			this.out.write(b, off, len);
		}

		@Override
		public void close() throws IOException {
			this.flush();
		}
	}

	@Override
	public void finish() throws BrokenResultException, IOException {
		super.finish();
		if (this.release != null) {
			// Parent of multiple documents. Each item has written its own manifest,
			// so write only the top-level index.json. All items have been released
			// (the caller waits for children to finish before calling finish()).
			final String binding;
			synchronized (this.children) {
				final BoundSide first = this.bindings.isEmpty() ? null : this.bindings.values().iterator().next();
				binding = first == null ? "single" : first.name().toLowerCase(Locale.ROOT);
				this.emit("index.json", "application/json",
						PagedSvgIndex.json(this.documents, new TreeMap<>(this.pageCounts), binding));
			}
			this.sink.end();
			return;
		}
		// Emit deferred pages (before fonts and the manifest).
		this.flushHeldPages(true);
		this.resources.emitFonts();
		// Retain measured image dimensions. Passing them as input.image-metrics when laying out
		// the same book with a different font size or screen size lets passes that only need dimensions
		// avoid opening any images.
		final var imageMetrics = this.getUAContext().getImageMetrics();
		if (imageMetrics.size() != 0) {
			this.emit(ImageMetricsIO.FILE_NAME, ImageMetricsIO.MEDIA_TYPE,
					ImageMetricsIO.write(imageMetrics, UAProps.OUTPUT_RESOLUTION.getDouble(this)));
		}
		this.finishPdfCompanion();
		final String binding = this.getBoundSide() == null ? "single"
				: this.getBoundSide().name().toLowerCase(Locale.ROOT);
		this.emit("manifest.json", "application/json",
				this.resources.manifest(this.metadata, binding, this.getPageProgressionDirection()));
		this.sink.end();
		if (this.parent != null) {
			this.parent.childFinished(this);
		}
	}

	@Override
	public Visitor getVisitor(final GC gc) {
		if (gc == null) {
			return new NopVisitor(this);
		}
		if (this.visitor == null) {
			this.visitor = new PagedSVGVisitor(this, this.resources);
		}
		this.visitor.nextPage(gc, this.currentPage);
		return this.visitor;
	}

	@Override
	public void dispose() {
		if (this.pdfCompanion != null) {
			this.pdfCompanion.dispose();
			this.pdfCompanion = null;
		}
		if (this.pdfSpool != null) {
			this.pdfSpool.delete();
			this.pdfSpool = null;
		}
		synchronized (this.children) {
			for (final PagedSVGUserAgent child : this.children) {
				child.dispose();
			}
			this.children.clear();
		}
		if (this.release != null) {
			this.release.close();
		}
		if (this.fontManager != null) {
			this.fontManager.close();
			this.fontManager = null;
		}
		super.dispose();
	}
}
