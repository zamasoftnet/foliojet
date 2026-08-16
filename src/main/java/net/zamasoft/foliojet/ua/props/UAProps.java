package net.zamasoft.foliojet.ua.props;

/**
 * 利用可能なパラメータ名です。
 * 
 * @author MIYABE Tatsuhiko
 */
public final class UAProps {
	private UAProps() {
		// constants
	}

	/**
	 * PIでのプロパティの上書き許可です。
	 */
	public static final BooleanPropManager INPUT_PROPERTY_PI = new BooleanPropManager("input.property-pi", false);

	/**
	 * XML/HTMLへのフィルタ処理です。
	 */
	public static final StringPropManager INPUT_FILTERS = new StringPropManager("input.filters",
			"xslt default-to-xhtml loose-html");

	/**
	 * Support change default namespace.
	 */
	public static final BooleanPropManager INPUT_CHANGE_DEFAULT_NAMESPACE = new BooleanPropManager("input.html.change-default-namespace",
			false);

	/**
	 * 選択するalternateスタイルシートのタイトルです。
	 */
	public static final StringPropManager INPUT_STYLESHEET_TITLES = new StringPropManager("input.stylesheet.titles",
			null);

	/**
	 * Normalize text by NFC mode.
	 */
	public static final BooleanPropManager INPUT_NORMALIZE_TEXT = new BooleanPropManager("input.normalize-text",
			false);

	/**
	 * デフォルトのエンコーディングです。
	 */
	public static final StringPropManager INPUT_DEFAULT_ENCODING = new StringPropManager("input.default-encoding",
			"JISUniAutoDetect");

	/**
	 * デフォルトのCSSスタイルシートです。
	 */
	public static final StringPropManager INPUT_DEFAULT_STYLESHEET = new StringPropManager("input.default-stylesheet",
			null);

	/**
	 * 画像の寸法をあらかじめ記したXMLです。レイアウトのために画像を読み込む代わりに
	 * この寸法を使うので、多パス処理や同じ本の組み直しが速くなります。
	 * Paged SVGは同じ形式のXMLを<code>metrics.xml</code>として出力するので、
	 * それをそのまま次回の指定に使えます。
	 */
	public static final StringPropManager INPUT_IMAGE_METRICS = new StringPropManager("input.image-metrics", null);

	/**
	 * デフォルトのXSLTスタイルシートです。
	 */
	public static final StringPropManager INPUT_XSLT_DEFAULT_STYLESHEET = new StringPropManager(
			"input.xslt.default-stylesheet", null);

	/**
	 * Refererヘッダの送信。
	 */
	public static final BooleanPropManager INPUT_HTTP_REFERER = new BooleanPropManager("input.http.referer", true);

	/**
	 * 接続タイムアウトです。0は無制限。
	 * 既定を無制限から60秒へ変更(2026-08-08)——応答しないサーバ1台で
	 * 変換全体が永久に止まるのを防ぐ。
	 */
	public static final IntegerPropManager INPUT_HTTP_CONNECTION_TIMEOUT = new IntegerPropManager(
			"input.http.connection.timeout", 60000);

	/**
	 * ソケットタイムアウトです(応答待ち・読み取り停止の上限)。0は無制限。
	 * 既定を無制限から60秒へ変更(2026-08-08)——kakaku.comの外部リソース
	 * 1本の配信停止で変換が2000秒超ハングした実バグの再発防止。
	 */
	public static final IntegerPropManager INPUT_HTTP_SOCKET_TIMEOUT = new IntegerPropManager(
			"input.http.socket.timeout", 60000);

	/**
	 * 主文書の最大入力サイズです。負数は無制限です。
	 */
	public static final LongPropManager INPUT_SIZE_LIMIT = new LongPropManager("input.size-limit", -1L);

	/**
	 * 主文書から解決する外部資源の累積最大入力サイズです。負数は無制限です。
	 */
	public static final LongPropManager INPUT_RESOURCE_SIZE_LIMIT = new LongPropManager(
			"input.resource-size-limit", -1L);

	/**
	 * 主文書から解決する相異なる外部資源URIの最大数です。負数は無制限です。
	 */
	public static final IntegerPropManager INPUT_RESOURCE_COUNT_LIMIT = new IntegerPropManager(
			"input.resource-count-limit", -1);

	/**
	 * 変換をまたぐHTTP応答キャッシュ(2026-08-10)。認証情報・Cookieを
	 * 伴わないGETだけが対象で、応答の{@code Cache-Control}
	 * (no-store/no-cache/private/max-age)を尊重する。@importされた
	 * ウェブフォントCSS等、毎変換同じ外部リソースを取り直す遅延の解消。
	 */
	public static final BooleanPropManager INPUT_HTTP_CACHE = new BooleanPropManager("input.http.cache", true);

	/**
	 * HTTP応答キャッシュの保持期間(秒)です。0はキャッシュ無効。
	 * 応答が{@code max-age}を持つ場合は短い方を使う。
	 */
	public static final IntegerPropManager INPUT_HTTP_CACHE_TTL = new IntegerPropManager("input.http.cache.ttl", 600);

	/**
	 * プロクシホスト名です。
	 */
	public static final StringPropManager INPUT_HTTP_PROXY_HOST = new StringPropManager("input.http.proxy.host", null);

	/**
	 * プロクシポート番号です。
	 */
	public static final IntegerPropManager INPUT_HTTP_PROXY_PORT = new IntegerPropManager("input.http.proxy.port",
			8080);

	/**
	 * プロクシのユーザーです。
	 */
	public static final StringPropManager INPUT_HTTP_PROXY_AUTHENTICATION_USER = new StringPropManager(
			"input.http.proxy.authentication.user", null);

	/**
	 * プロクシのパスワードです。
	 */
	public static final StringPropManager INPUT_HTTP_PROXY_AUTHENTICATION_PASSWORD = new StringPropManager(
			"input.http.proxy.authentication.password", "");

	/**
	 * 認証時に最初に認証情報を送るかどうかの設定です。
	 */
	public static final BooleanPropManager INPUT_HTTP_AUTHENTICATION_PREEMPTIVE = new BooleanPropManager(
			"input.http.authentication.preemptive", false);

	/**
	 * 認証の設定です。
	 */
	public static final String INPUT_HTTP_AUTHENTICATION = "input.http.authentication.";

	/**
	 * クッキーの設定です。
	 */
	public static final String INPUT_HTTP_COOKIE = "input.http.cookie.";

	/**
	 * HTTPヘッダの設定です。
	 */
	public static final String INPUT_HTTP_HEADER = "input.http.header.";

	/**
	 * &lt;meta name="viewport"～をページサイズとして認識します。
	 */
	public static final BooleanPropManager INPUT_VIEWPORT = new BooleanPropManager("input.viewport", false);

	/**
	 * ページ幅です。
	 */
	public static final StringPropManager OUTPUT_PAGE_WIDTH = new StringPropManager("output.page-width", "210mm");

	/**
	 * ページの高さです。
	 */
	public static final StringPropManager OUTPUT_PAGE_HEIGHT = new StringPropManager("output.page-height", "297mm");

	/**
	 * ページのマージンです。
	 */
	public static final StringPropManager OUTPUT_PAGE_MARGINS = new StringPropManager("output.page-margins", "12.7mm");

	/**
	 * 用紙の幅です。
	 */
	public static final StringPropManager OUTPUT_PAPER_WIDTH = new StringPropManager("output.paper-width", null);

	/**
	 * 用紙の高さです。
	 */
	public static final StringPropManager OUTPUT_PAPER_HEIGHT = new StringPropManager("output.paper-height", null);

	/**
	 * 印刷モードです。
	 */
	public static final CodePropManager<OutputPrintMode> OUTPUT_PRINT_MODE = new CodePropManager<>("output.print-mode", OutputPrintMode.class, OutputPrintMode.DOUBLE_SIDE);

	/**
	 * 1枚の用紙に面付けする論理ページ数(N-up)です。1で面付けなし。
	 */
	public static final IntegerPropManager OUTPUT_N_UP = new IntegerPropManager("output.n-up", 1);

	/**
	 * N-up面付けのページの並び順です。
	 */
	public static final CodePropManager<OutputNUpOrder> OUTPUT_N_UP_ORDER = new CodePropManager<>("output.n-up.order", OutputNUpOrder.class, OutputNUpOrder.HORIZONTAL);

	/**
	 * 水平方向の断ち代の幅です。
	 */
	public static final StringPropManager OUTPUT_HTRIM = new StringPropManager("output.htrim", "1cm");

	/**
	 * 垂直方向の断ち代の幅です。
	 */
	public static final StringPropManager OUTPUT_VTRIM = new StringPropManager("output.vtrim", "1cm");

	/**
	 * 断ち代の幅です。
	 */
	public static final StringPropManager OUTPUT_TRIMS = new StringPropManager("output.trims", null);

	/**
	 * 内容を用紙に合わせて拡大します。
	 */
	public static final CodePropManager<OutputFitToPaper> OUTPUT_FIT_TO_PAPER = new CodePropManager<>("output.fit-to-paper", OutputFitToPaper.class, OutputFitToPaper.FALSE);

	/**
	 * 内容または用紙を自動回転します
	 */
	public static final CodePropManager<OutputAutoRotate> OUTPUT_AUTO_ROTATE = new CodePropManager<>("output.auto-rotate", OutputAutoRotate.class, OutputAutoRotate.NONE);

	/**
	 * トンボの内部をクリップします。
	 */
	public static final BooleanPropManager OUTPUT_CLIP = new BooleanPropManager("output.clip", true);

	/**
	 * デフォルトのフォントです。
	 */
	public static final StringPropManager OUTPUT_DEFAULT_FONT_FAMILY = new StringPropManager(
			"output.default-font-family", "serif");

	/**
	 * テキストの倍率です。
	 */
	public static final DoublePropManager OUTPUT_TEXT_SIZE = new DoublePropManager("output.text-size", 1.0);

	/**
	 * 自動高さです。
	 */
	public static final BooleanPropManager OUTPUT_AUTO_HEIGHT = new BooleanPropManager("output.auto-height", false);

	/**
	 * 自動高さです。
	 */
	public static final BooleanPropManager OUTPUT_EXPAND_WITH_CONTENT = new BooleanPropManager("output.expand-with-content", false);

	/**
	 * 改ページを禁止します。
	 */
	public static final BooleanPropManager OUTPUT_NO_PAGE_BREAK = new BooleanPropManager("output.no-page-break", false);

	/**
	 * 出力形式です。
	 */
	public static final StringPropManager OUTPUT_TYPE = new StringPropManager("output.type", "application/pdf");

	/**
	 * 含有パターンです。
	 */
	public static final String INPUT_INCLUDE = "input.include";

	/**
	 * 除外パターンです。
	 */
	public static final String INPUT_EXCLUDE = "input.exclude";

	/**
	 * ファイルサイズの限界値です。
	 */
	public static final LongPropManager OUTPUT_SIZE_LIMIT = new LongPropManager("output.size-limit", -1L);

	/**
	 * ページ数の限界値です。
	 */
	public static final IntegerPropManager OUTPUT_PAGE_LIMIT = new IntegerPropManager("output.page-limit", -1);

	/**
	 * ページ数の限界に達した場合の処理です。
	 */
	public static final CodePropManager<OutputPageLimitAbort> OUTPUT_PAGE_LIMIT_ABORT = new CodePropManager<>("output.page-limit.abort", OutputPageLimitAbort.class, OutputPageLimitAbort.FORCE);

	/**
	 * トンボの形式です。
	 */
	public static final CodePropManager<OutputMarks> OUTPUT_MARKS = new CodePropManager<>("output.marks", OutputMarks.class, OutputMarks.NONE);

	/**
	 * 適用するCSSのメディアタイプです。
	 */
	public static final StringPropManager OUTPUT_MEDIA_TYPES = new StringPropManager("output.media_types",
			"all print paged visual bitmap static");

	/**
	 * 表示できない画像の扱いです。
	 */
	public static final CodePropManager<OutputBrokenImage> OUTPUT_BROKEN_IMAGE = new CodePropManager<>("output.broken-image", OutputBrokenImage.class, OutputBrokenImage.NONE);

	/**
	 * カラー出力です。
	 */
	public static final CodePropManager<OutputColor> OUTPUT_COLOR = new CodePropManager<>("output.color", OutputColor.class, OutputColor.RGB);

	/**
	 * pxを計算する際の解像度です。
	 */
	public static final DoublePropManager OUTPUT_RESOLUTION = new DoublePropManager("output.resolution", 96.0);

	/**
	 * 画像出力解像度です。
	 */
	public static final DoublePropManager OUTPUT_IMAGE_RESOLUTION = new DoublePropManager("output.image.resolution",
			96.0);

	/**
	 * 画像のアンチエイリアスです。
	 */
	public static final BooleanPropManager OUTPUT_IMAGE_ANTIALIAS = new BooleanPropManager("output.image.antialias",
			true);

	/**
	 * メタ情報です。
	 */
	public static final String OUTPUT_META = "output.meta.";

	/**
	 * フォントの扱いです。
	 */
	public static final StringPropManager OUTPUT_PDF_FONTS_POLICY = new StringPropManager("output.pdf.fonts.policy",
			"cid-keyed");

	/**
	 * 全体の圧縮方法です。
	 */
	public static final CodePropManager<OutputPdfCompression> OUTPUT_PDF_COMPRESSION = new CodePropManager<>("output.pdf.compression", OutputPdfCompression.class, OutputPdfCompression.BINARY);

	/**
	 * 画像の圧縮方法です。
	 */
	public static final CodePropManager<OutputPdfImageCompression> OUTPUT_PDF_IMAGE_COMPRESSION = new CodePropManager<>("output.pdf.image.compression", OutputPdfImageCompression.class, OutputPdfImageCompression.FLATE);

	/**
	 * ロスレス圧縮を適用する画像サイズの閾値です。
	 */
	public static final IntegerPropManager OUTPUT_PDF_IMAGE_COMPRESSION_LOSSLESS = new IntegerPropManager(
			"output.pdf.image.compression.lossless", 200);

	/**
	 * 画像の最大幅（ピクセル数）です。
	 */
	public static final IntegerPropManager OUTPUT_PDF_IMAGE_MAX_WIDTH = new IntegerPropManager(
			"output.pdf.image.max-width", 0);

	/**
	 * 画像の最大高さ（ピクセル数）です。
	 */
	public static final IntegerPropManager OUTPUT_PDF_IMAGE_MAX_HEIGHT = new IntegerPropManager(
			"output.pdf.image.max-height", 0);

	/**
	 * 添付ファイル設定です。
	 */
	public static final String OUTPUT_PDF_ATTACHMENTS = "output.pdf.attachments.";

	/**
	 * PDFバージョンです。
	 */
	public static final CodePropManager<OutputPdfVersion> OUTPUT_PDF_VERSION = new CodePropManager<>("output.pdf.version", OutputPdfVersion.class, OutputPdfVersion.V1_5);

	/**
	 * 暗号化方法です。
	 */
	public static final CodePropManager<OutputPdfEncryption> OUTPUT_PDF_ENCRYPTION = new CodePropManager<>("output.pdf.encryption", OutputPdfEncryption.class, OutputPdfEncryption.NONE);

	/**
	 * タグ付き PDF（論理構造）を出力するかどうか。
	 */
	public static final BooleanPropManager OUTPUT_PDF_TAGGED = new BooleanPropManager("output.pdf.tagged", false);

	/**
	 * タグ付き PDF / PDF/UA の言語（BCP 47、例 "ja"）。
	 */
	public static final StringPropManager OUTPUT_PDF_TAGGED_LANG = new StringPropManager("output.pdf.tagged.lang", null);

	/**
	 * HTML フォーム部品（input/textarea/select）を入力可能な PDF フォーム
	 * フィールド（AcroForm）として出力するかどうか。有効にするとフォーム部品の
	 * 見た目が対話ウィジェットの外観に変わる（フォームを含まない文書の出力は不変）。
	 * PDF/X ではフォームが禁止されているため出力されない。
	 */
	public static final BooleanPropManager OUTPUT_PDF_FORMS = new BooleanPropManager("output.pdf.forms", false);

	/**
	 * 暗号のユーザーパスワードです。
	 */
	public static final StringPropManager OUTPUT_PDF_ENCRYPTION_USER_PASSWORD = new StringPropManager(
			"output.pdf.encryption.user-password", "");

	/**
	 * 暗号のオーナーパスワードです。
	 */
	public static final StringPropManager OUTPUT_PDF_ENCRYPTION_OWNER_PASSWORD = new StringPropManager(
			"output.pdf.encryption.owner-password", null);

	// PDFパーミッションの設定です。
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT = new BooleanPropManager(
			"output.pdf.encryption.permissions.print", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_MODIFY = new BooleanPropManager(
			"output.pdf.encryption.permissions.modify", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_COPY = new BooleanPropManager(
			"output.pdf.encryption.permissions.copy", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ADD = new BooleanPropManager(
			"output.pdf.encryption.permissions.add", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_FILL = new BooleanPropManager(
			"output.pdf.encryption.permissions.fill", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_EXTRACT = new BooleanPropManager(
			"output.pdf.encryption.permissions.extract", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ASSEMBLE = new BooleanPropManager(
			"output.pdf.encryption.permissions.assemble", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT_HIGH = new BooleanPropManager(
			"output.pdf.encryption.permissions.print-high", true);

	/**
	 * 暗号の長さです。
	 */
	public static final IntegerPropManager OUTPUT_PDF_ENCRYPTION_LENGTH = new IntegerPropManager(
			"output.pdf.encryption.length", 128);

	/**
	 * ブックマークです。
	 */
	public static final BooleanPropManager OUTPUT_PDF_BOOKMARKS = new BooleanPropManager("output.pdf.bookmarks", false);

	/**
	 * リンクです。
	 */
	public static final BooleanPropManager OUTPUT_PDF_HYPERLINKS = new BooleanPropManager("output.pdf.hyperlinks",
			false);

	/**
	 * リンクの方法です。
	 */
	public static final CodePropManager<OutputPdfHyperlinksHref> OUTPUT_PDF_HYPERLINKS_HREF = new CodePropManager<>("output.pdf.hyperlinks.href", OutputPdfHyperlinksHref.class, OutputPdfHyperlinksHref.RELATIVE);

	/**
	 * リンクの基点です。
	 */
	public static final StringPropManager OUTPUT_PDF_HYPERLINKS_BASE = new StringPropManager(
			"output.pdf.hyperlinks.base", null);

	/**
	 * ページ内リンクです。
	 */
	public static final BooleanPropManager OUTPUT_PDF_HYPERLINKS_FRAGMENT = new BooleanPropManager(
			"output.pdf.hyperlinks.fragment", true);

	/**
	 * JPEG画像の圧縮方法です。
	 */
	public static final CodePropManager<OutputPdfJpegImage> OUTPUT_PDF_JPEG_IMAGE = new CodePropManager<>("output.pdf.jpeg-image", OutputPdfJpegImage.class, OutputPdfJpegImage.RAW);

	/**
	 * PDF内部の名前リテラルのエンコーディングです。
	 */
	public static final StringPropManager OUTPUT_PDF_PLATFORM_ENCODING = new StringPropManager(
			"output.pdf.platform-encoding", "MS932");

	/**
	 * 処理回数です。
	 */
	public static final IntegerPropManager PROCESSING_PASS_COUNT = new IntegerPropManager("processing.pass-count", 1);

	/**
	 * 1文書の変換に許す最大経過時間(ミリ秒)です。0以下は無制限です。
	 */
	public static final LongPropManager PROCESSING_TIME_LIMIT = new LongPropManager("processing.time-limit", 0L);

	/**
	 * データを実際には生成しない、中間のパスを実行します。
	 */
	public static final BooleanPropManager PROCESSING_MIDDLE_PASS = new BooleanPropManager("processing.middle-pass",
			false);
	/**
	 * ページ参照を行います。
	 */
	public static final BooleanPropManager PROCESSING_PAGE_REFERENCES = new BooleanPropManager(
			"processing.page-references", false);

	/**
	 * エラー発生時は強制中断します。
	 */
	public static final BooleanPropManager PROCESSING_FAIL_ON_FATAL_ERROR = new BooleanPropManager(
			"processing.fail-on-fatal-error", true);


		/**
	 * レイアウトソース(LayoutSource)のテキストpayloadをheapへinline
	 * 保持する上限(bytes)です(既定8MB、2026-07-24新設——E-6増分3b-2)。
	 *
	 * <p>
	 * 保持中のinline bytesがこの予算を超える新規テキスト追記は一時
	 * ファイル(spillストア)へ書かれ、改ページ再生時にdecodeされる。
	 * 判定は設定値と累積bytesのみの決定的なもので、heap残量には依存
	 * しない。出力(display list)は予算値に関わらず完全に同一——変わる
	 * のはメモリ挙動のみ。既定値はコーパス実測(保持イベント数は数千
	 * 規模の文書が大半)より十分大きく、通常文書ではspillは起きない。
	 * </p>
	 */
	public static final LongPropManager PROCESSING_TEXT_SPILL_BUDGET = new LongPropManager(
			"processing.text-spill-budget", 8L * 1024L * 1024L);

	/**
	 * ファイルIDです。
	 */
	public static final StringPropManager OUTPUT_PDF_FILE_ID = new StringPropManager("output.pdf.file-id", null);

	/**
	 * 作成日時です。
	 */
	public static final StringPropManager OUTPUT_PDF_META_CREATION_DATE = new StringPropManager(
			"output.pdf.meta.creation-date", null);

	/**
	 * 更新日時です。
	 */
	public static final StringPropManager OUTPUT_PDF_META_MOD_DATE = new StringPropManager("output.pdf.meta.mod-date",
			null);

	/**
	 * 電子インボイス(Factur-X/ZUGFeRD)のXMP適合レベルです。設定すると
	 * fx:拡張スキーマがXMPへ出力されます(請求書XML自体は
	 * output.pdf.attachments.*でrelationship=alternativeとして添付する)。
	 */
	public static final StringPropManager OUTPUT_PDF_FACTURX_CONFORMANCE_LEVEL = new StringPropManager(
			"output.pdf.facturx.conformance-level", null);

	/**
	 * Factur-Xの文書種別です(既定INVOICE)。
	 */
	public static final StringPropManager OUTPUT_PDF_FACTURX_DOCUMENT_TYPE = new StringPropManager(
			"output.pdf.facturx.document-type", "INVOICE");

	/**
	 * Factur-Xの請求書XMLファイル名です(既定factur-x.xml。添付名と
	 * 一致させること)。
	 */
	public static final StringPropManager OUTPUT_PDF_FACTURX_DOCUMENT_FILE_NAME = new StringPropManager(
			"output.pdf.facturx.document-file-name", "factur-x.xml");

	/**
	 * Factur-Xのプロファイル版です(既定1.0)。
	 */
	public static final StringPropManager OUTPUT_PDF_FACTURX_VERSION = new StringPropManager(
			"output.pdf.facturx.version", "1.0");

	/**
	 * 出力インテントの出力条件識別名です(例: JC200103、FOGRA39)。
	 * 設定するとカタログへ/OutputIntentsを出力します(PDF/Xの適合要件)。
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_IDENTIFIER = new StringPropManager(
			"output.pdf.output-intent.identifier", null);

	/**
	 * 出力インテントの出力条件の人間可読名です。
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_CONDITION = new StringPropManager(
			"output.pdf.output-intent.condition", null);

	/**
	 * 出力インテントのレジストリ名です(既定はICC特性化レジストリ)。
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_REGISTRY = new StringPropManager(
			"output.pdf.output-intent.registry", "http://www.color.org");

	/**
	 * 出力インテントの補足説明です(未登録条件のPDF/Xで推奨)。
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_INFO = new StringPropManager(
			"output.pdf.output-intent.info", null);

	/**
	 * 出力インテントへ埋め込むICCプロファイルのURIです(DestOutputProfile)。
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE = new StringPropManager(
			"output.pdf.output-intent.icc-profile", null);

	/**
	 * 既定のレンダリングインテントです(perceptual, relative-colorimetric,
	 * saturation, absolute-colorimetricのいずれか)。
	 */
	public static final StringPropManager OUTPUT_PDF_RENDERING_INTENT = new StringPropManager(
			"output.pdf.rendering-intent", null);

	/**
	 * 背表紙幅です。
	 */
	public static final StringPropManager OUTPUT_MARKS_SPINE_WIDTH = new StringPropManager("output.marks.spine-width",
			null);

	/**
	 * CFM暗号化です。
	 */
	public static final CodePropManager<OutputPdfEncryptionV4CFM> OUTPUT_PDF_ENCRYPTION_V4_CFM = new CodePropManager<>("output.pdf.encryption.v4.cfm", OutputPdfEncryptionV4CFM.class, OutputPdfEncryptionV4CFM.V2);

	/**
	 * すかし画像です。
	 */
	public static final StringPropManager OUTPUT_PDF_WATERMARK_URI = new StringPropManager("output.pdf.watermark.uri",
			null);

	/**
	 * すかし画像の配置方法です。
	 */
	public static final CodePropManager<OutputPdfWatermarkMode> OUTPUT_PDF_WATERMARK_MODE = new CodePropManager<>("output.pdf.watermark.mode", OutputPdfWatermarkMode.class, OutputPdfWatermarkMode.BACK);

	/**
	 * すかし画像の不透明度です。
	 */
	public static final DoublePropManager OUTPUT_PDF_WATERMARK_OPACITY = new DoublePropManager(
			"output.pdf.watermark.opacity", 1);

	/**
	 * すかし画像を画面表示するか。
	 */
	public static final BooleanPropManager OUTPUT_PDF_WATERMARK_VIEW = new BooleanPropManager(
			"output.pdf.watermark.view", true);

	/**
	 * すかし画像を印刷するか。
	 */
	public static final BooleanPropManager OUTPUT_PDF_WATERMARK_PRINT = new BooleanPropManager(
			"output.pdf.watermark.print", true);

	// PDFの ViewerPreference の設定
	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_TOOLBAR = new BooleanPropManager(
			"output.pdf.viewer-preferences.hide-toolber", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_MENUBAR = new BooleanPropManager(
			"output.pdf.viewer-preferences.hide-menubar", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_WINDOWUI = new BooleanPropManager(
			"output.pdf.viewer-preferences.hide-windowUI", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_FIT_WINDOW = new BooleanPropManager(
			"output.pdf.viewer-preferences.fit-window", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_CENTER_WINDOW = new BooleanPropManager(
			"output.pdf.viewer-preferences.center-window", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_DISPLAY_DOC_TITLE = new BooleanPropManager(
			"output.pdf.viewer-preferences.display-doc-title", false);

	public static final CodePropManager<OutputPdfViewerPreferencesNonFullScreenPageMode> OUTPUT_PDF_VIEWER_PREFERENCES_NON_FULL_SCREEN_PAGE_MODE = new CodePropManager<>("output.pdf.viewer-preferences.non-full-screen-page-mode", OutputPdfViewerPreferencesNonFullScreenPageMode.class, OutputPdfViewerPreferencesNonFullScreenPageMode.USE_NONE);

	public static final CodePropManager<OutputPdfViewerPreferencesPrintScaling> OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING = new CodePropManager<>("output.pdf.viewer-preferences.print-scaling", OutputPdfViewerPreferencesPrintScaling.class, OutputPdfViewerPreferencesPrintScaling.APP_DEFAULT);

	public static final CodePropManager<OutputPdfViewerPreferencesDuplex> OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX = new CodePropManager<>("output.pdf.viewer-preferences.duplex", OutputPdfViewerPreferencesDuplex.class, OutputPdfViewerPreferencesDuplex.NONE);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_PICK_TRAY_BY_PDF_SIZE = new BooleanPropManager(
			"output.pdf.viewer-preferences.pick-tray-by-pdf-size", false);

	public static final StringPropManager OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_PAGE_RANGE = new StringPropManager(
			"output.pdf.viewer-preferences.print-page-range", null);

	public static final IntegerPropManager OUTPUT_PDF_VIEWER_PREFERENCES_NUM_COPIES = new IntegerPropManager(
			"output.pdf.viewer-preferences.num-copies", 0);

	/**
	 * PDFを開いた時のJavaScript。
	 */
	public static final StringPropManager OUTPUT_PDF_OPEN_ACTION_JAVA_SCRIPT = new StringPropManager(
			"output.pdf.open-action.java-script", null);

	/**
	 * 文書情報を設定するmeta, titleタグを解釈します。
	 */
	public static final BooleanPropManager OUTPUT_USE_META_INFO = new BooleanPropManager("output.use-meta-info", true);

	/**
	 * Paged SVGの共有資源を参照にするか埋め込みにするかです。
	 */
	public static final CodePropManager<PagedSvgResourceMode> OUTPUT_PAGED_SVG_RESOURCES = new CodePropManager<>(
			"output.paged-svg.resources", PagedSvgResourceMode.class, PagedSvgResourceMode.REFERENCE);

	/**
	 * Paged SVGのページSVGの書き出し方式です。
	 */
	public static final CodePropManager<PagedSvgWriter> OUTPUT_PAGED_SVG_WRITER = new CodePropManager<>(
			"output.paged-svg.writer", PagedSvgWriter.class, PagedSvgWriter.BATIK);

	/**
	 * Paged SVGのフォントサブセットを出力するかどうかです。
	 */
	public static final CodePropManager<PagedSvgResourcePolicy> OUTPUT_PAGED_SVG_FONTS = new CodePropManager<>(
			"output.paged-svg.fonts", PagedSvgResourcePolicy.class, PagedSvgResourcePolicy.EMIT);

	/**
	 * Paged SVGの画像を出力するかどうかです。
	 */
	public static final CodePropManager<PagedSvgResourcePolicy> OUTPUT_PAGED_SVG_IMAGES = new CodePropManager<>(
			"output.paged-svg.images", PagedSvgResourcePolicy.class, PagedSvgResourcePolicy.EMIT);

	private static final java.util.List<PropManager> ALL = java.util.List.of(
			INPUT_PROPERTY_PI,
			INPUT_FILTERS,
			INPUT_CHANGE_DEFAULT_NAMESPACE,
			INPUT_STYLESHEET_TITLES,
			INPUT_NORMALIZE_TEXT,
			INPUT_DEFAULT_ENCODING,
			INPUT_DEFAULT_STYLESHEET,
			INPUT_IMAGE_METRICS,
			INPUT_XSLT_DEFAULT_STYLESHEET,
			INPUT_HTTP_REFERER,
			INPUT_HTTP_CONNECTION_TIMEOUT,
			INPUT_HTTP_SOCKET_TIMEOUT,
			INPUT_HTTP_CACHE,
			INPUT_HTTP_CACHE_TTL,
			INPUT_HTTP_PROXY_HOST,
			INPUT_HTTP_PROXY_PORT,
			INPUT_HTTP_PROXY_AUTHENTICATION_USER,
			INPUT_HTTP_PROXY_AUTHENTICATION_PASSWORD,
			INPUT_HTTP_AUTHENTICATION_PREEMPTIVE,
			INPUT_VIEWPORT,
			OUTPUT_PAGE_WIDTH,
			OUTPUT_PAGE_HEIGHT,
			OUTPUT_PAGE_MARGINS,
			OUTPUT_PAPER_WIDTH,
			OUTPUT_PAPER_HEIGHT,
			OUTPUT_PRINT_MODE,
			OUTPUT_N_UP,
			OUTPUT_N_UP_ORDER,
			OUTPUT_HTRIM,
			OUTPUT_VTRIM,
			OUTPUT_TRIMS,
			OUTPUT_FIT_TO_PAPER,
			OUTPUT_AUTO_ROTATE,
			OUTPUT_CLIP,
			OUTPUT_DEFAULT_FONT_FAMILY,
			OUTPUT_TEXT_SIZE,
			OUTPUT_AUTO_HEIGHT,
			OUTPUT_EXPAND_WITH_CONTENT,
			OUTPUT_NO_PAGE_BREAK,
			OUTPUT_TYPE,
			OUTPUT_SIZE_LIMIT,
			OUTPUT_PAGE_LIMIT,
			OUTPUT_PAGE_LIMIT_ABORT,
			OUTPUT_MARKS,
			OUTPUT_MEDIA_TYPES,
			OUTPUT_BROKEN_IMAGE,
			OUTPUT_COLOR,
			OUTPUT_RESOLUTION,
			OUTPUT_IMAGE_RESOLUTION,
			OUTPUT_IMAGE_ANTIALIAS,
			OUTPUT_PDF_FONTS_POLICY,
			OUTPUT_PDF_COMPRESSION,
			OUTPUT_PDF_IMAGE_COMPRESSION,
			OUTPUT_PDF_IMAGE_COMPRESSION_LOSSLESS,
			OUTPUT_PDF_IMAGE_MAX_WIDTH,
			OUTPUT_PDF_IMAGE_MAX_HEIGHT,
			OUTPUT_PDF_VERSION,
			OUTPUT_PDF_ENCRYPTION,
			OUTPUT_PDF_TAGGED,
			OUTPUT_PDF_TAGGED_LANG,
			OUTPUT_PDF_FORMS,
			OUTPUT_PDF_ENCRYPTION_USER_PASSWORD,
			OUTPUT_PDF_ENCRYPTION_OWNER_PASSWORD,
			OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT,
			OUTPUT_PDF_ENCRYPTION_PERMISSIONS_MODIFY,
			OUTPUT_PDF_ENCRYPTION_PERMISSIONS_COPY,
			OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ADD,
			OUTPUT_PDF_ENCRYPTION_PERMISSIONS_FILL,
			OUTPUT_PDF_ENCRYPTION_PERMISSIONS_EXTRACT,
			OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ASSEMBLE,
			OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT_HIGH,
			OUTPUT_PDF_ENCRYPTION_LENGTH,
			OUTPUT_PDF_BOOKMARKS,
			OUTPUT_PDF_HYPERLINKS,
			OUTPUT_PDF_HYPERLINKS_HREF,
			OUTPUT_PDF_HYPERLINKS_BASE,
			OUTPUT_PDF_HYPERLINKS_FRAGMENT,
			OUTPUT_PDF_JPEG_IMAGE,
			OUTPUT_PDF_PLATFORM_ENCODING,
			PROCESSING_PASS_COUNT,
			PROCESSING_MIDDLE_PASS,
			PROCESSING_PAGE_REFERENCES,
			PROCESSING_FAIL_ON_FATAL_ERROR,
			PROCESSING_TEXT_SPILL_BUDGET,
			OUTPUT_PDF_FILE_ID,
			OUTPUT_PDF_META_CREATION_DATE,
			OUTPUT_PDF_META_MOD_DATE,
			OUTPUT_PDF_FACTURX_CONFORMANCE_LEVEL,
			OUTPUT_PDF_FACTURX_DOCUMENT_TYPE,
			OUTPUT_PDF_FACTURX_DOCUMENT_FILE_NAME,
			OUTPUT_PDF_FACTURX_VERSION,
			OUTPUT_PDF_OUTPUT_INTENT_IDENTIFIER,
			OUTPUT_PDF_OUTPUT_INTENT_CONDITION,
			OUTPUT_PDF_OUTPUT_INTENT_REGISTRY,
			OUTPUT_PDF_OUTPUT_INTENT_INFO,
			OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE,
			OUTPUT_PDF_RENDERING_INTENT,
			OUTPUT_MARKS_SPINE_WIDTH,
			OUTPUT_PDF_ENCRYPTION_V4_CFM,
			OUTPUT_PDF_WATERMARK_URI,
			OUTPUT_PDF_WATERMARK_MODE,
			OUTPUT_PDF_WATERMARK_OPACITY,
			OUTPUT_PDF_WATERMARK_VIEW,
			OUTPUT_PDF_WATERMARK_PRINT,
			OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_TOOLBAR,
			OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_MENUBAR,
			OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_WINDOWUI,
			OUTPUT_PDF_VIEWER_PREFERENCES_FIT_WINDOW,
			OUTPUT_PDF_VIEWER_PREFERENCES_CENTER_WINDOW,
			OUTPUT_PDF_VIEWER_PREFERENCES_DISPLAY_DOC_TITLE,
			OUTPUT_PDF_VIEWER_PREFERENCES_NON_FULL_SCREEN_PAGE_MODE,
			OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING,
			OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX,
			OUTPUT_PDF_VIEWER_PREFERENCES_PICK_TRAY_BY_PDF_SIZE,
			OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_PAGE_RANGE,
			OUTPUT_PDF_VIEWER_PREFERENCES_NUM_COPIES,
			OUTPUT_PDF_OPEN_ACTION_JAVA_SCRIPT,
			OUTPUT_USE_META_INFO,
			OUTPUT_PAGED_SVG_RESOURCES,
			OUTPUT_PAGED_SVG_WRITER,
			OUTPUT_PAGED_SVG_FONTS,
			OUTPUT_PAGED_SVG_IMAGES);

	/**
	 * 定義済みの全プロパティを返します。
	 */
	public static java.util.List<PropManager> all() {
		return ALL;
	}
}
