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
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;


import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.results.NopResults;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.ImageMetricsIO;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.RandomResultUserAgent;
import net.zamasoft.foliojet.ua.impl.AbstractUserAgent;
import net.zamasoft.foliojet.ua.impl.NopVisitor;
import net.zamasoft.foliojet.ua.props.PagedSvgCompression;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.SequentialOutput;
import net.zamasoft.zstream.io.util.FragmentOutputAdapter;
import net.zamasoft.zstream.io.util.SequentialOutputAdapter;
import net.zamasoft.zstream.resolver.util.SimpleSourceMetadata;

/** Produces stable URI-addressed pages, shared WOFF2 subsets and image assets. */
public class PagedSVGUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	private Results results, savedResults;
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

	/**
	 * <b>1本のZIPにまとめて返すか</b>(B-2、2026-08-29)。結果が複数になる
	 * ふつうのバンドルは、セッションを使わない一発のREST
	 * ({@code POST /transcode})では受け取れない(4001になる)。ZIPなら
	 * 結果1件なのでRESTでもそのまま返せる。
	 */
	/** ZIPで返すときの結果URIとメディア型。 */
	static final String BUNDLE_URI = "paged-svg.zip";

	static final String BUNDLE_MEDIA_TYPE = "application/zip";

	private final boolean zipBundle;

	/** ZIPで返すときの唯一の結果。最初のemitで開く。 */
	private java.util.zip.ZipOutputStream zip;

	private FragmentedOutput zipBuilder;

	public PagedSVGUserAgent() {
		this(false);
	}

	public PagedSVGUserAgent(final boolean zipBundle) {
		this.zipBundle = zipBundle;
		this.resetOutput();
	}

	@Override
	public void setResults(final Results results) {
		this.results = results;
		this.resetOutput();
	}

	@Override
	public void prepare(final PrepareMode mode) {
		super.prepare(mode);
		switch (mode) {
		case MIDDLE_PASS -> {
			if (!this.middleStateSaved) {
				this.savedResults = this.results;
				this.middleStateSaved = true;
			}
			this.results = NopResults.SHARED_INSTANCE;
			this.resetOutput();
		}
		case LAST_PASS -> {
			if (this.middleStateSaved) {
				this.results = this.savedResults;
				this.savedResults = null;
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
		return new SourcedImage(image, uri);
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
			this.fontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager());
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
		}
	}

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
			this.resources.setBaseUri(normaliseBaseUri(UAProps.OUTPUT_PAGED_SVG_BASE_URI.getString(this)));
			this.resources.setFontPerPage(UAProps.OUTPUT_PAGED_SVG_FONT_SCOPE
					.get(this) == net.zamasoft.foliojet.ua.props.PagedSvgFontScope.PAGE);
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
		return new DirectPagedSVGGC(this.directPage.writer(), this.getFontManager(), this.resources,
				this.currentPage);
	}

	@Override
	public void closePage(final GC gc) throws IOException {
		super.closePage(gc);
		if (gc == null) {
			return;
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
		this.resources.emitPageFonts();

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
	 * manifestの記載と実体が食い違いません。
	 * </p>
	 */
	private String emit(final String uri, final String mimeType, final ContentWriter content) throws IOException {
		if (this.results == null) {
			throw new IOException("Results is not set");
		}
		if (this.zipBundle) {
			return this.emitZipEntry(uri, content);
		}
		if (!this.results.hasNext()) {
			throw new AbortException(CTISession.ABORT_NORMAL);
		}
		final var metadata = new SimpleSourceMetadata(URI.create(uri), mimeType, null, -1);
		final FragmentedOutput builder = this.results.nextBuilder(metadata);
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		try {
			final OutputStream raw;
			if (builder instanceof SequentialOutput sequential) {
				raw = new SequentialOutputAdapter(sequential);
			} else {
				builder.addFragment();
				raw = new FragmentOutputAdapter(builder, 0);
			}
			try (raw) {
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
			}
		} finally {
			builder.close();
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
	/**
	 * ZIPの1エントリとして書き出します(B-2、2026-08-29)。
	 *
	 * <p>
	 * 名前はふつうのバンドルと同じURI({@code pages/0001.svg}、
	 * {@code assets/fonts/font-0001.woff2}…)。展開すればディレクトリ出力と
	 * 同じ形になり、{@code manifest.json}の参照もそのまま解決する。
	 * SHA-256は<b>エントリの中身</b>(圧縮前)に対して取るので、受け手は
	 * 展開したファイルへそのまま当てられる。
	 * </p>
	 */
	private String emitZipEntry(final String uri, final ContentWriter content) throws IOException {
		final java.util.zip.ZipOutputStream out = this.requireZip();
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		out.putNextEntry(new java.util.zip.ZipEntry(uri));
		try {
			content.write(new DigestOutputStream(new UnclosableOutputStream(out), digest));
		} finally {
			out.closeEntry();
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/** ZIPの結果を必要になった時点で1件だけ開きます。 */
	private java.util.zip.ZipOutputStream requireZip() throws IOException {
		if (this.zip != null) {
			return this.zip;
		}
		if (!this.results.hasNext()) {
			throw new AbortException(CTISession.ABORT_NORMAL);
		}
		final var metadata = new SimpleSourceMetadata(URI.create(BUNDLE_URI), BUNDLE_MEDIA_TYPE, null, -1);
		this.zipBuilder = this.results.nextBuilder(metadata);
		final OutputStream raw;
		if (this.zipBuilder instanceof SequentialOutput sequential) {
			raw = new SequentialOutputAdapter(sequential);
		} else {
			this.zipBuilder.addFragment();
			raw = new FragmentOutputAdapter(this.zipBuilder, 0);
		}
		this.zip = new java.util.zip.ZipOutputStream(raw);
		return this.zip;
	}

	private void closeZip() throws IOException {
		if (this.zip == null) {
			return;
		}
		try {
			this.zip.finish();
			this.zip.close();
		} finally {
			this.zip = null;
			if (this.zipBuilder != null) {
				this.zipBuilder.close();
				this.zipBuilder = null;
			}
		}
	}

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
		this.resources.emitFonts();
		// 測った画像の寸法を残す。次に同じ本を別の文字サイズ・画面サイズで
		// 組むとき input.image-metrics に渡せば、寸法しか要らないパスで
		// 画像を一度も開かずに済む。
		final var imageMetrics = this.getUAContext().getImageMetrics();
		if (imageMetrics.size() != 0) {
			this.emit(ImageMetricsIO.FILE_NAME, ImageMetricsIO.MEDIA_TYPE,
					ImageMetricsIO.write(imageMetrics, UAProps.OUTPUT_RESOLUTION.getDouble(this)));
		}
		final String binding = this.getBoundSide() == null ? "single"
				: this.getBoundSide().name().toLowerCase(Locale.ROOT);
		this.emit("manifest.json", "application/json", this.resources.manifest(this.metadata, binding));
		this.closeZip();
		this.results.end();
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
		if (this.fontManager != null) {
			this.fontManager.close();
			this.fontManager = null;
		}
		super.dispose();
	}
}
