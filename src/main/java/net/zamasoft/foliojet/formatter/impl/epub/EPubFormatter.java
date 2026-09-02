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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.formatter.Formatter;
import net.zamasoft.foliojet.formatter.MultiDocumentFormatter;
import net.zamasoft.foliojet.formatter.impl.document.TranscoderHandler;
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
import net.zamasoft.foliojet.ua.props.BooleanPropManager;
import net.zamasoft.foliojet.ua.props.OutputPrintMode;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.ParserFactory;
import net.zamasoft.foliojet.xml.XMLHandler;
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
import net.zamasoft.pdfg2d.gc.GC;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * EPubをフォーマットします。
 *
 * <p>
 * 出力が{@link MultiDocumentOutput}(Paged SVG)なら、spine項目を<b>独立した
 * 文書として</b>組む({@link #formatDocuments})。項目ごとに子のUAを開いて
 * 自分のパス駆動を回し、並列に走らせられる。結果は親がspine順に解放する。
 * それ以外の出力(PDF・画像)は従来どおり1つのUAへ全項目を順に流す
 * ({@link #format})。どちらでも項目は必ず新しいページから始まる
 * (各項目の最後のページはその項目の終わりで閉じられる——2026-09-02に実測)。
 * </p>
 */
public class EPubFormatter implements MultiDocumentFormatter {
	private static final Logger LOG = Logger.getLogger(EPubFormatter.class.getName());

	private static final String PLUGIN_NAME = "net.zamasoft.foliojet.plugins.epub";

	public static final BooleanPropManager REPLACE_NUMBERS = new BooleanPropManager(
			"x.net.zamasoft.foliojet.formatter.impl.epub.replace-numbers", false);

	/**
	 * EPUBの中身がディレクトリとして与えられることを示すMIME型です。
	 * ZIPを送らず、必要な項目だけを基底URIの下から取ります。
	 */
	public static final String DIRECTORY_MEDIA_TYPE = "application/epub+directory";

	public boolean match(final Source key) {
		final Source source = (Source) key;
		try {
			final String uri = source.getURI().toString();
			if (uri.length() >= 5 && uri.substring(uri.length() - 5).equalsIgnoreCase(".epub")) {
				return true;
			}
			// ディレクトリ形式。末尾が「.epub/」なら拡張子指定だけで選べる
			if (uri.length() >= 6 && uri.substring(uri.length() - 6).equalsIgnoreCase(".epub/")) {
				return true;
			}
			final String mimeType = source.getMimeType();
			if (mimeType != null
					&& (mimeType.equals("application/epub+zip") || mimeType.equals(DIRECTORY_MEDIA_TYPE))) {
				return true;
			}
			// 拡張子も型も EPUB を名乗らない入力(URL 入力で application/octet-stream 等)は、
			// 中身が ZIP で先頭項目 mimetype が application/epub+zip なら EPUB と見る
			// (2026-09-02、cti.li の申し送り——HTML として読み続けて終わらなかった)。
			// 覗けるのはファイルに実体のある入力だけ(ストリームは消費してしまう)
			if (source.isFile() && looksLikeEpub(source.getFile())) {
				return true;
			}
		} catch (IOException e) {
			LOG.log(Level.WARNING, "変換元文書のMIME型を取得できませんでした", e);
		}
		return false;
	}

	/** ZIP のローカルヘッダと、OCF が先頭に置く {@code mimetype} 項目(無圧縮)を見ます。 */
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
		// ローカルヘッダ: 名前の長さ(26-27)・拡張の長さ(28-29)、名前(30-)、拡張、データ
		final int nameLen = (head[26] & 0xFF) | ((head[27] & 0xFF) << 8);
		final int extraLen = (head[28] & 0xFF) | ((head[29] & 0xFF) << 8);
		final String s = new String(head, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
		if (nameLen != 8 || !s.startsWith("mimetype", 30)) {
			return false;
		}
		final int data = 30 + nameLen + extraLen;
		return data + 20 <= n && s.startsWith("application/epub+zip", data);
	}

	private CSSElement getPageSide(UserAgent ua, boolean leftBind) {
		CSSElement pageElement = ua.getPassContext().getPageSide();
		switch (UAProps.OUTPUT_PRINT_MODE.get(ua)) {
		case DOUBLE_SIDE:
		case LEFT_SIDE:
		case RIGHT_SIDE:
			// 両面
			if (leftBind) {
				// 横書き
				if (pageElement == null) {
					pageElement = CSSElement.PAGE_FIRST_RIGHT;
				} else if (pageElement == CSSElement.PAGE_FIRST_RIGHT) {
					pageElement = CSSElement.PAGE_LEFT_EVEN;
				} else if (pageElement == CSSElement.PAGE_LEFT_EVEN) {
					pageElement = CSSElement.PAGE_RIGHT_ODD;
				} else if (pageElement == CSSElement.PAGE_RIGHT_ODD) {
					pageElement = CSSElement.PAGE_LEFT_EVEN;
				}
			} else {
				// 縦書き
				if (pageElement == null) {
					pageElement = CSSElement.PAGE_FIRST_LEFT;
				} else if (pageElement == CSSElement.PAGE_FIRST_LEFT) {
					pageElement = CSSElement.PAGE_RIGHT_ODD;
				} else if (pageElement == CSSElement.PAGE_RIGHT_ODD) {
					pageElement = CSSElement.PAGE_LEFT_EVEN;
				} else if (pageElement == CSSElement.PAGE_LEFT_EVEN) {
					pageElement = CSSElement.PAGE_RIGHT_ODD;
				}
			}
			break;

		case SINGLE_SIDE:
			// 片面
			if (pageElement == null) {
				pageElement = CSSElement.PAGE_SINGLE_FIRST;
			} else {
				pageElement = CSSElement.PAGE_SINGLE;
			}
			break;

		default:
			throw new IllegalStateException();
		}
		return pageElement;
	}

	/** 項目のパスから実体を開きます。ZIPとディレクトリで違うのはここだけです。 */
	private interface EntryOpener {
		Source open(URI path, String mediaType) throws IOException;
	}

	/** 開いたEPUBに対して行う処理。 */
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
	 * EPUBを開き(ZIPまたはディレクトリ)、資源の解決をUAへ据えてから本体を走らせます。
	 * 失敗は種類を保って伝えます——中断と変換例外はそのまま、それ以外はプラグインの
	 * 失敗として包む。
	 */
	private void withArchive(final Source source, final UserAgent ua, final Body body)
			throws AbortException, TranscoderException {
		try {
			if (isDirectory(source)) {
				final URI base = toDirectoryURI(source.getURI());
				// EPUB内部は相対URIで参照し合う。ZIPの zip: スキームと同じ役目を
				// 「基底URIへの相対解決」が果たす
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
					// データ源をZIPファイルに設定
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
		final short code = MessageCodes.ERROR_PLUGIN;
		final String[] args = new String[] { PLUGIN_NAME, e.getLocalizedMessage() };
		final String mes = MessageCodeUtils.toString(code, args);
		ua.message(code, args);
		LOG.log(Level.WARNING, mes, e);
		return new TranscoderException(code, args, mes);
	}

	private void open(final ArchiveFile archive, final EntryOpener opener, final Body body) throws Exception {
		// メタ情報解析
		final EPubFile epub = new EPubFile(archive);
		final Container container = epub.readContainer();
		final Rootfile root = container.rootfiles[0];
		final Contents contents = epub.readContents(root);
		body.run(epub, contents, opener);
	}

	/** ページ進行方向を{@code output.print-mode}へ写し、横綴じかどうかを返します。 */
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

	private static Map<URI, Item> fullPathToItem(final Contents contents) {
		final Map<URI, Item> fullPathToItem = new HashMap<URI, Item>();
		for (int i = 0; i < contents.spine.length; ++i) {
			final ItemRef ir = contents.spine[i];
			fullPathToItem.put(URI.create(ir.item.fullPath), ir.item);
		}
		return fullPathToItem;
	}

	// ---- 従来どおり: 1つのUAへ全項目を順に流す

	private void formatSequential(final Contents contents, final EntryOpener opener, final UserAgent ua)
			throws Exception {
		final boolean leftBind = applyProgression(ua, contents);
		final Map<URI, Item> fullPathToItem = fullPathToItem(contents);
		final boolean[] included = selectSpine(ua, contents);
		for (int i = 0; i < contents.spine.length; ++i) {
			if (!included[i]) {
				continue;
			}
			this.formatItem(ua, contents.spine[i], fullPathToItem, opener, leftBind);
		}
	}

	// ---- 項目ごとに独立: 子のUAで並列に組み、spine順に解放する

	private void formatIndependent(final EPubFile epub, final Contents contents, final EntryOpener opener,
			final MultiDocumentOutput ua, final int passCount) throws Exception {
		final boolean leftBind = applyProgression(ua, contents);
		final Map<URI, Item> fullPathToItem = fullPathToItem(contents);
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

		// 項目はspine順に投入する。先頭から先に走るので、解放も早く始まる
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
						this.formatItemPasses(child, ir, fullPathToItem, opener, leftBind, passCount);
					} catch (final TranscoderException | RuntimeException | Error e) {
						// AbortExceptionはRuntimeException。そのまま通す
						throw e;
					} catch (final Exception e) {
						throw pluginFailure(child, e);
					}
					return null;
				}));
			}
			awaitAll(ua, futures);
		} finally {
			// 誰も書いていない状態でしか戻らない。中断でも、子がすべて
			// 止まるまで待つ(呼び出し側がセッションを閉じたあとに書き続けさせない)
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
	 * 全項目の完了を待ちます。最初の失敗で残りを中断し、全部が止まってから
	 * その失敗を投げる。割り込み(締切)も中断に写し、待ち続ける。
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

	/** 同時に組む項目の数。{@code 0}(既定)はコア数と4の小さいほう。 */
	private static int concurrency(final UserAgent ua) {
		final int configured = UAProps.PROCESSING_CONCURRENCY.getInteger(ua);
		if (configured > 0) {
			return configured;
		}
		return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
	}

	/**
	 * 項目1つのパス駆動。{@code DirectSession.format}と同じ順
	 * (構造走査→中間×n→最終)で、入力はZIPの項目を開き直すので一時ファイルは要らない。
	 */
	private void formatItemPasses(final UserAgent child, final ItemRef ir, final Map<URI, Item> fullPathToItem,
			final EntryOpener opener, final boolean leftBind, final int passCount) throws Exception {
		if (passCount <= 1) {
			child.prepare(PrepareMode.DOCUMENT);
			child.getUAContext().setPassCount(1);
			child.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(1));
			this.formatItem(child, ir, fullPathToItem, opener, leftBind);
		} else {
			child.prepare(PrepareMode.STRUCTURE_SCAN);
			this.formatItem(child, ir, fullPathToItem, opener, leftBind);
			for (int remaining = passCount; remaining > 1; --remaining) {
				child.prepare(PrepareMode.MIDDLE_PASS);
				child.getUAContext().setPassCount(remaining);
				child.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(remaining));
				this.formatItem(child, ir, fullPathToItem, opener, leftBind);
			}
			child.prepare(PrepareMode.LAST_PASS);
			child.getUAContext().setPassCount(1);
			child.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(1));
			this.formatItem(child, ir, fullPathToItem, opener, leftBind);
		}
		child.finish();
	}

	/** 項目1つを、いま準備されているパスで組みます。 */
	private void formatItem(final UserAgent ua, final ItemRef ir, final Map<URI, Item> fullPathToItem,
			final EntryOpener opener, final boolean leftBind) throws Exception {
		switch (ir.pageSpread) {
		case ItemRef.PAGE_SPREAD_LEFT: {
			CSSElement e = this.getPageSide(ua, leftBind);
			if (e.isPseudoClass(CSSElement.PC_LEFT)) {
				this.blankPage(ua);
			}
		}
			break;
		case ItemRef.PAGE_SPREAD_RIGHT:
			CSSElement e = this.getPageSide(ua, leftBind);
			if (e.isPseudoClass(CSSElement.PC_RIGHT)) {
				this.blankPage(ua);
			}
			break;
		}

		ua.getPassContext().resetNonPageCounters();
		final URI path = URIHelper.create("UTF-8", ir.item.fullPath);
		ua.getDocumentContext().setBaseURI(path);
		final Source zSource = opener.open(path, ir.item.mediaType);
		final String mimeType = zSource.getMimeType();
		if (mimeType.equals("application/xhtml+xml")) {
			ParserFactory pf = PluginRegistry.getInstance().search(ParserFactory.class, mimeType);
			Parser parser = pf.createParser();
			TranscoderHandler transcoderHandler = new TranscoderHandler(ua);
			XMLHandler entryPoint = new LinkHandler(transcoderHandler, ir.item, fullPathToItem);
			boolean replaceNumbers = REPLACE_NUMBERS.getBoolean(ua);
			WritingModeHandler xhandler = new WritingModeHandler(entryPoint, ir.item, replaceNumbers);
			entryPoint = XMLHandler.of(xhandler, null);
			try {
				parser.parse(ua, zSource, entryPoint);
			} finally {
				// E-6増分3b-2: spill一時ファイルの清算(冪等)
				transcoderHandler.dispose();
			}
		} else {
			Formatter formatter = PluginRegistry.getInstance().search(Formatter.class, zSource);
			formatter.format(zSource, ua);
		}
	}

	/** 見開きの合わせのための白紙。 */
	private void blankPage(final UserAgent ua) throws IOException {
		String ws = UAProps.OUTPUT_PAGE_WIDTH.getString(ua);
		AbsoluteLengthValue wl = ValueUtils.toAbsoluteLength(ua, false, ws);
		String hs = UAProps.OUTPUT_PAGE_HEIGHT.getString(ua);
		AbsoluteLengthValue hl = ValueUtils.toAbsoluteLength(ua, false, hs);
		ws = UAProps.OUTPUT_PAPER_WIDTH.getString(ua);
		if (ws != null) {
			wl = ValueUtils.toAbsoluteLength(ua, false, ws);
		}
		hs = UAProps.OUTPUT_PAPER_HEIGHT.getString(ua);
		if (hs != null) {
			hl = ValueUtils.toAbsoluteLength(ua, false, hs);
		}
		GC gc = ua.nextPage(wl.getLength(), hl.getLength());
		ua.closePage(gc);
	}

	// ---- spine の絞り込みと全体の記述

	/**
	 * {@code input.epub.spine}で組む項目を選びます。空なら全部。要素は
	 * idref・パス・1起点の番号・番号の範囲。どれにも当たらない要素は警告して無視する。
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

	/** 目次(nav/ncx)。読めなければ空。 */
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
					// 項目のパスが壊れているなら、nav が指すURIのまま
				}
			}
			final String fragment = point.uri == null ? null : point.uri.getFragment();
			entries.add(new TocEntry(point.label, uri, fragment, tocEntries(point.children)));
		}
		return entries;
	}

	/** EPUBの中身がディレクトリとして与えられているか。 */
	private static boolean isDirectory(final Source source) {
		try {
			final String mimeType = source.getMimeType();
			if (DIRECTORY_MEDIA_TYPE.equals(mimeType)) {
				return true;
			}
		} catch (final IOException e) {
			// MIME型が取れないならURIで判断する
		}
		final URI uri = source.getURI();
		if (uri == null) {
			return false;
		}
		final String path = uri.getPath();
		return path != null && path.endsWith("/");
	}

	/** 末尾を{@code /}に揃えます。相対解決の基点になるためです。 */
	private static URI toDirectoryURI(final URI uri) {
		if (uri == null) {
			throw new IllegalArgumentException("EPUB directory URI is missing");
		}
		final String text = uri.toString();
		return text.endsWith("/") ? uri : URI.create(text + "/");
	}
}

class LinkHandler extends DefaultXMLHandlerFilter {
	final AttributesImpl attsi = new AttributesImpl();
	final Item item;
	final Map<URI, Item> fullPathToItem;
	final URI base;

	LinkHandler(XMLHandler handler, Item item, Map<URI, Item> fullPathToItem) {
		super(handler);
		this.item = item;
		this.base = URI.create(item.fullPath);
		this.fullPathToItem = fullPathToItem;
	}

	/**
	 * {@code href} が指す項目です。無ければ {@code null}。
	 *
	 * <p>
	 * 2026-09-02(cti.li の申し送り): 目次の {@code href="3260.xhtml#ix_ACCS 不正アクセス事件"}
	 * のように空白や日本語を含む断片で {@code URISyntaxException} になり、以前は
	 * {@code SAXException} で**本全体**が I/O error に落ちていた(PDF でも)。断片は
	 * 項目の特定に要らないので、まず断片を捨てて解決し直し、それでも駄目なら
	 * その href だけ書き換えずに続行する。
	 * </p>
	 */
	private Item itemOf(final String ref) {
		try {
			return this.fullPathToItem.get(URIHelper.resolve("UTF-8", this.base, ref));
		} catch (URISyntaxException e) {
			final int hash = ref.indexOf('#');
			if (hash >= 0) {
				try {
					return this.fullPathToItem.get(URIHelper.resolve("UTF-8", this.base, ref.substring(0, hash)));
				} catch (URISyntaxException e2) {
					// 下へ
				}
			}
			java.util.logging.Logger.getLogger(LinkHandler.class.getName()).log(Level.FINE,
					"EPUB link left as written (not a URI): " + ref, e);
			return null;
		}
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (lName.equals("body")) {
			super.startElement(uri, lName, qName, atts);

			this.attsi.clear();
			this.attsi.addAttribute("", "id", "id", "CDATA", this.item.fullPath);
			this.attsi.addAttribute("", "name", "name", "CDATA", "x-epub-" + this.item.fullPath);
			super.startElement(uri, "a", "a", this.attsi);
			super.endElement(uri, "a", "a");
			return;
		} else if (lName.equals("a")) {
			int href = atts.getIndex("href");
			if (href != -1) {
				String ref = atts.getValue(href);
				Item item = this.itemOf(ref);
				if (item != null) {
					this.attsi.setAttributes(atts);
					atts = this.attsi;
					this.attsi.setValue(href, "#x-epub-" + item.fullPath);
				}
			}
		}
		super.startElement(uri, lName, qName, atts);
	}
}
