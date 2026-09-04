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
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
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
 * <b>EPUBは項目(spineのXHTML)ごとに独立した出力にする</b>(2026-09-02、
 * {@link MultiDocumentOutput})。親のUAは項目ごとに子のUAを開き、子は
 * {@code items/NNNN/}の下へ単一の文書と同じ形のバンドル(自分の
 * {@code manifest.json}・フォント・画像)を出す。親は最後に
 * {@code index.json}(項目の並び・累積のページ番号・目次)だけを書く。
 * 結果は{@link DocumentRelease}がspine順に解放するので、項目を並列に
 * 組んでも受け手に届く列は逐次と同じ。
 * </p>
 */
public class PagedSVGUserAgent extends AbstractUserAgent implements RandomResultUserAgent, MultiDocumentOutput {
	private ResultSink sink, savedSink;
	private boolean middleStateSaved;
	private FontManagerImpl fontManager;
	private PagedSVGResources.PageData currentPage;
	/** 縮めて返すかどうか。ページSVGとページJSONにだけ効く。 */
	private PagedSvgCompression compression = PagedSvgCompression.NONE;
	/** 独自書き出しのときだけ使う。Batikのときはnull。 */
	private java.io.StringWriter directBuffer;
	private SVGPageOutput directPage;
	private PagedSVGResources resources;
	private PagedSVGVisitor visitor;
	private int page;
	private final Map<String, String> metadata = new LinkedHashMap<>();

	// ---- PDF の同時出力(2026-09-03、cti.li の要望「1回の変換で PDF と Paged SVG を両方」)

	/** 同じ組版から PDF も書く随伴の UA。{@code output.paged-svg.pdf=true} の最初のページで作る。 */
	private net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent pdfCompanion;
	/** 随伴の PDF の一時置き場(結果集合へは最後に1件で出す。途中で2件を開いたままにしない)。 */
	private java.io.File pdfSpool;
	/** 結果に書く PDF の名前。 */
	static final String PDF_URI = "document.pdf";

	/**
	 * <b>1本のZIPにまとめて返すか</b>(B-2、2026-08-29)。結果が複数になる
	 * ふつうのバンドルは、セッションを使わない一発のREST
	 * ({@code POST /transcode})では受け取れない(4001になる)。ZIPなら
	 * 結果1件なのでRESTでもそのまま返せる。
	 */
	private final boolean zipBundle;

	/** ZIPで返すときの結果URIとメディア型。 */
	static final String BUNDLE_URI = ResultSink.ZipSink.BUNDLE_URI;

	static final String BUNDLE_MEDIA_TYPE = ResultSink.ZipSink.BUNDLE_MEDIA_TYPE;

	// ---- 複数文書(EPUB)の親としての状態
	/** セッションのメッセージの受け手。子のメッセージは解放段を通ってここへ届く。 */
	private MessageHandler sessionMessages;
	private DocumentRelease release;
	private DocumentSet documents;
	private final List<PagedSVGUserAgent> children = new ArrayList<>();
	/** 組み終えた項目の位置→ページ数。 */
	private final Map<Integer, Integer> pageCounts = new TreeMap<>();
	/** 組み終えた項目の位置→綴じ方向。index.jsonには最初の項目のものを書く。 */
	private final Map<Integer, BoundSide> bindings = new TreeMap<>();
	/** 親に届いた中断要求。後から開く子にも伝える。 */
	private volatile byte abortRequested = 0;

	// ---- 子(項目)としての状態
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

	/** EPUBの項目を組む子。結果は親の解放段へ。 */
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
		this.page = 0;
		this.metadata.clear();
		this.resources = new PagedSVGResources(this::emit, this.getUAContext().getPagedSvgFontCarry());
		if (this.unit != null && this.unit.uri() != null) {
			// 持ち越しの鍵は項目ごと。同じフォントでも章が違えば字形の並びが違う
			this.resources.setDocument(this.unit.uri().toString());
		}
		// 描いた画像の資源同一性を寸法表へ控える(2026-08-28)。次の
		// 再変換はこれを渡されれば画像を開かずに同じ参照を書ける
		this.resources.setAssetRecorder((uri, asset) -> this.getUAContext().getImageMetrics().putAsset(uri.toString(),
				new net.zamasoft.foliojet.ua.ImageMetricsCache.Asset(asset.sha256(), asset.mediaType(),
						extensionOf(asset.uri()), asset.width(), asset.height())));
		this.visitor = null;
	}

	/** 資源URI({@code assets/images/<sha>.<ext>})から拡張子を取り出します。 */
	private static String extensionOf(final String uri) {
		final int dot = uri.lastIndexOf('.');
		return dot < 0 ? "bin" : uri.substring(dot + 1);
	}

	// ---- 複数文書(EPUB)の親

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
		// 親と同じ設定・資源の解決・フォント。プロパティは写しを渡す
		// (文書内のPIが書き換えるので、項目ごとに別の表でなければならない)
		child.setProperties(this.getProperties());
		child.setSourceResolver(this.getSourceResolver());
		child.setMessageHandler(releaseUnit::message);
		child.getUAContext().setFontSourceManager(this.getUAContext().getFontSourceManager());
		child.getUAContext().setPagedSvgFontCarry(this.getUAContext().getPagedSvgFontCarry());
		// 持ち越しの控えを差し替えたので、台帳を作り直す
		child.resetOutput();
		synchronized (this.children) {
			this.children.add(child);
			if (this.abortRequested != 0) {
				child.abort(this.abortRequested);
			}
		}
		return child;
	}

	/** 子が組み終えた。index.jsonのために項目のページ数と綴じ方向を控える。 */
	private void childFinished(final PagedSVGUserAgent child) {
		synchronized (this.children) {
			this.pageCounts.put(child.unit.index(), child.page);
			this.bindings.put(child.unit.index(), child.getBoundSide());
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

	/** 子の結果とメッセージの行き先。項目の完了は{@link #end()}で親へ伝える。 */
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

	// ---- 画像

	/**
	 * 画像に取得元URIを添えます(2026-08-28)。描画時に決まる資源の同一性を
	 * 「どのURIの画像だったか」と結び付けて{@code metrics.json}へ書くために
	 * 必要で、包んでも描画の振る舞いは変わりません
	 * ({@link SourcedImage})。
	 */
	@Override
	public net.zamasoft.pdfg2d.gc.image.Image getImage(final URI uri,
			final net.zamasoft.zstream.resolver.Source source) throws IOException {
		final net.zamasoft.pdfg2d.gc.image.Image image = super.getImage(uri, source);
		if (image == null || uri == null) {
			return image;
		}
		// **描画パスでも寸法を控える**(2026-08-28)。基底は測定パスだけを
		// 対象にするため、単一パスの変換では寸法表が空のままで
		// metrics.jsonが出力されず、次の再変換で使えるものが何も残らなかった。
		// 画素は持たない寸法だけの記録なので容量は無視できる
		if (!"data".equalsIgnoreCase(uri.getScheme())) {
			this.getUAContext().getImageMetrics().putSize(uri.toString(), image.getWidth(), image.getHeight());
		}
		final SourcedImage sourced = new SourcedImage(image, uri);
		if (this.parent == null && UAProps.OUTPUT_PAGED_SVG_PDF.getBoolean(this)) {
			// PDF の同時出力: 従にも同じ取得元から画像を作らせる(PDF は URI で重複排除し、
			// JPEG は元のバイト列のまま埋める。主の画素を渡すと使うたびに再圧縮して
			// 埋め、実文書で 2.0MB→6.7MB になった)
			try {
				sourced.companion = this.pdfCompanion().getImage(uri, source);
			} catch (final IOException | RuntimeException e) {
				sourced.companion = null;
			}
		}
		return sourced;
	}

	/**
	 * 寸法だけで済むなら資源を開きません。
	 *
	 * <p>
	 * 基底は測定パス・構造走査パスに限って寸法表を引きますが、Paged SVGでは
	 * <b>描画パスでも</b>引けます(2026-08-28)。ページが書く参照は
	 * {@code assets/images/<sha256>.<ext>}で、前回の{@code metrics.json}に
	 * その同一性まで控えてあれば、画像を開かずに同じ参照を書けるためです。
	 * 実体を出し直す設定では画素が要るので、{@code resources=omit}
	 * かつ直接書き出しのときだけに限ります。
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
	 * 画像は<b>元のバイト列のまま</b>資源として出します(2026-08-28)。
	 * ページSVGは{@code assets/images/<sha256>.<ext>}を参照するだけなので、
	 * JPEGをPNGへ焼き直す必要がない。
	 */
	@Override
	public boolean keepsEncodedImages() {
		return true;
	}

	@Override
	public FontManager getFontManager() {
		if (this.fontManager == null) {
			if (this.parent == null && UAProps.OUTPUT_PAGED_SVG_PDF.getBoolean(this)
					&& this.pdfCompanion().getFontManager() instanceof final FontManagerImpl pdfFonts) {
				// PDF の同時出力(2026-09-03): フォント倉庫を随伴の PDF と共有する。PDF は
				// フォントの資源名を自分の倉庫で付け、埋め込みフォントの字形 ID は
				// サブセット内の通し番号なので、倉庫が別だと文字を PDF へ流せない
				// (資源名が無く NPE、字形 ID がずれて文字化け)。同じ倉庫なら
				// 整形した Text をそのまま両方に描ける
				this.fontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager(),
						pdfFonts.getFontStore());
			} else {
				this.fontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager());
			}
		}
		return this.fontManager;
	}

	/**
	 * この出力の既定フォント方針です。<b>SVGでは埋め込みを既定にします</b>
	 * (2026-08-28)。
	 *
	 * <p>
	 * 共通の既定は{@code output.pdf.fonts.policy}=cid-keyed、つまり
	 * 「PDFの外部CIDフォントとして参照する」方針だが、これはSVGには
	 * 存在しない仕組みで、SVG出力では字形をすべてアウトライン(path)へ
	 * 落とす経路(アウトライン化)
	 * にしかならない。実測(ja.wikipedia「地方病」68ページ):
	 * cid-keyed 141.3MB・13.5秒に対し、embedded 32.8MB・8.9秒
	 * ——出力4.3倍・生成時間34%の差で、しかも埋め込み側は文字が
	 * {@code <text>}として出るため選択・検索もできる。
	 * </p>
	 *
	 * <p>
	 * 埋め込みが許されないフォント(OS/2 fsType)や字形を写せない場合は
	 * 従来どおりアウトラインへ退化するので、
	 * ライセンス面の意味は変わらない。利用者が
	 * {@code output.pdf.fonts.policy}を明示した場合はそちらに従う。
	 * </p>
	 */
	@Override
	public CSSJFontPolicyValue getDefaultFontPolicy() {
		if (this.getProperty(UAProps.OUTPUT_PDF_FONTS_POLICY.name) != null) {
			return super.getDefaultFontPolicy();
		}
		return CSSJFontPolicyValue.CORE_EMBEDDED_VALUE;
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

	// ---- ページ

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
			// ZIPで返すときは中身を縮めない——ZIP側が縮めるので二重になるし、
			// 受け手が展開してそのまま開ける名前(.svg/.json)であるべき
			this.compression = this.zipBundle ? PagedSvgCompression.NONE
					: UAProps.OUTPUT_PAGED_SVG_COMPRESSION.get(this);
			// 前回の変換のサブセットを**1ページ目より先に**出す(2026-08-29)。
			// 同じ本を文字サイズだけ変えて組み直すとき、受け手は最初のページ
			// から本来の書体で描ける
			try {
				this.resources.emitCarriedFonts();
			} catch (final IOException e) {
				throw new GraphicsException(e);
			}
		}
		this.currentPage = new PagedSVGResources.PageData(number, this.pageWidth, this.pageHeight);
		// DOMを作らず書き出す。ページの内容はここでは確定しないので、
		// 結果への書き出しは closePage で行う(ハッシュを流しながら
		// 取るため、結果1件は1回のストリームで書き切る必要がある)
		try {
			this.directBuffer = new java.io.StringWriter(1 << 14);
			this.directPage = new SVGPageOutput(this.directBuffer, this.pageWidth, this.pageHeight);
			final String base = this.resources.getBaseUri();
			this.directPage.writer().setFontSrc(uri -> base + uri);
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
		final GC svgGc = new DirectPagedSVGGC(this.directPage.writer(), this.getFontManager(), this.resources,
				this.currentPage);
		if (this.parent == null && UAProps.OUTPUT_PAGED_SVG_PDF.getBoolean(this)) {
			final GC pdfGc = this.pdfCompanion().nextPage(this.pageWidth, this.pageHeight);
			if (pdfGc != null) {
				return new TeeGC(svgGc, pdfGc);
			}
		}
		return svgGc;
	}

	/**
	 * 随伴の PDF の UA を(初回に)作ります。入力側の状態(資源解決・プロパティ・
	 * フォント源・基底 URI・メタデータ・綴じ)を写し、結果は一時ファイルへ。
	 * フォント源は共有なので、ページSVG用に整形した文字を PDF もそのまま
	 * 文字として書ける(フォント方針はページSVGと同じ埋め込みが既定)。
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
		pdf.getUAContext().setFontSourceManager(this.getUAContext().getFontSourceManager());
		try {
			this.pdfSpool = java.io.File.createTempFile("copper-paged-svg-", ".pdf");
			this.pdfSpool.deleteOnExit();
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
					// 呼び出し側(finish)が結果集合へ移す
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

	/** 随伴の PDF を閉じて、結果集合へ1件({@link #PDF_URI})として移します。 */
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
	 * ページSVGから共有資源を指す前置き。
	 *
	 * <p>
	 * 既定の{@code ../}は{@code pages/}から自分のバンドルの根へ上がる相対で、
	 * EPUBの項目({@code items/NNNN/pages/})でもそのまま項目の根に着く。
	 * 絶対URLの前置きを与えられたときは、項目の分を足す
	 * ({@code https://example.com/book/}→{@code https://example.com/book/items/0003/})。
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
			this.closeDirectPage();
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
	 * 独自書き出しのページを閉じます。
	 *
	 * <p>
	 * 中身は{@link java.io.StringWriter}へ組み立ててから1回で書き出します。
	 * DOMを作らない点はそのままですが、<b>結果1件を1回のストリームで
	 * 書き切らないとSHA-256を流しながら取れない</b>ためです。Batik版は
	 * ページ全体のDOMを保持していたので、これでも保持量は減ります。
	 * </p>
	 */
	private void closeDirectPage() throws IOException {
		this.directPage.close();
		final String svg = this.directBuffer.toString();
		this.directBuffer = null;
		this.directPage = null;

		// **ページSVGより先にそのページの書体を出す**(2026-09-02、
		// font-scope: page)。受け手はページが届いた時点で字形を持っている
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
	 * {@code output.paged-svg.base-uri}を前置きとして使える形に整えます。
	 * 空(または未指定)はそのまま前置き無し、末尾の{@code /}は無ければ補う。
	 */
	private static String normaliseBaseUri(final String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.endsWith("/") ? value : value + "/";
	}

	// ---- 結果の書き出し

	/** 結果1件の中身を書きます。溜めずに、渡された出力へ直接書くこと。 */
	@FunctionalInterface
	interface ContentWriter {
		void write(OutputStream out) throws IOException;
	}

	private void emit(final String uri, final String mimeType, final byte[] bytes) throws IOException {
		this.emit(uri, mimeType, out -> out.write(bytes));
	}

	/**
	 * 結果を1件書き出し、そのSHA-256を返します。
	 *
	 * <p>
	 * <b>中身を溜めません</b>(2026-08-16)。ダイジェスト計算も書きながら行い、
	 * 長さは申告しません({@code -1})。以前はページSVG全体を
	 * {@code ByteArrayOutputStream}へ作ってから書いていました。
	 * 画像出力・単一SVG出力は元から直接書いており、溜めていたのはここだけです。
	 * </p>
	 *
	 * <p>
	 * 返すSHA-256は<b>実際に書いたバイト列</b>に対する値なので、
	 * manifestの記載と実体が食い違いません。行き先(結果集合・ZIP・EPUBの
	 * 解放段)は{@link ResultSink}が隠し、ここはハッシュとgzipだけを受け持つ。
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
			// SHA-256は**実際に渡すバイト**に対して取る。だから縮める場合は
			// digestを外側(gzipの出口)に置く。受け手はmanifestの値を
			// 保存したファイルへそのまま当てられる
			final OutputStream digested = new DigestOutputStream(raw, digest);
			if (this.isCompressed(uri)) {
				// GZIPOutputStreamはcloseでトレーラを書くので閉じる必要があるが、
				// 下位まで閉じさせない
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
	 * gzipで縮めて返す結果かどうか。
	 *
	 * <p>
	 * 縮めるのは文字で書かれたページSVGとページJSONだけです。共有WOFF2と
	 * PNG/JPEGは既に圧縮済みで縮まないうえ、二重に包むと受け手の手間が増えます。
	 * {@code manifest.json}は読み口なので、そのままにします。
	 * </p>
	 */
	private boolean isCompressed(final String uri) {
		return this.compression == PagedSvgCompression.GZIP
				&& (uri.endsWith(".svgz") || uri.endsWith(".json.gz"));
	}

	/** 縮める設定なら{@code .svgz}/{@code .json.gz}へ、そうでなければそのまま。 */
	private String pageUri(final String stem, final String extension) {
		if (this.compression != PagedSvgCompression.GZIP) {
			return stem + extension;
		}
		return stem + (".svg".equals(extension) ? ".svgz" : extension + ".gz");
	}

	/**
	 * 書き手が{@code close()}しても下位を閉じない包み。
	 * {@code OutputStreamWriter}を{@code try}で閉じて内容を確実に流し切りつつ、
	 * 結果の境界はこちらの手順で閉じるためです。
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
			// 複数文書の親。項目はそれぞれ自分のmanifestを書いてあるので、
			// 上位のindex.jsonだけを書く。全項目は解放済み(呼び出し側が
			// 子の完了を待ってから finish() に来る)
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
		this.resources.emitFonts();
		// 測った画像の寸法を残す。次に同じ本を別の文字サイズ・画面サイズで
		// 組むとき input.image-metrics に渡せば、寸法しか要らないパスで
		// 画像を一度も開かずに済む。
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
