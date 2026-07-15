package net.zamasoft.foliojet.ua.props;

/**
 * 利用可能なパラメータ名です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: UAProps.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public interface UAProps {
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
	 * デフォルトのXSLTスタイルシートです。
	 */
	public static final StringPropManager INPUT_XSLT_DEFAULT_STYLESHEET = new StringPropManager(
			"input.xslt.default-stylesheet", null);

	/**
	 * Refererヘッダの送信。
	 */
	public static final BooleanPropManager INPUT_HTTP_REFERER = new BooleanPropManager("input.http.referer", true);

	/**
	 * 接続タイムアウトです。
	 */
	public static final IntegerPropManager INPUT_HTTP_CONNECTION_TIMEOUT = new IntegerPropManager(
			"input.http.connection.timeout", 0);

	/**
	 * ソケットタイムアウトです。
	 */
	public static final IntegerPropManager INPUT_HTTP_SOCKET_TIMEOUT = new IntegerPropManager(
			"input.http.socket.timeout", 0);

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
	public static final CodePropManager OUTPUT_PRINT_MODE = new CodePropManager("output.print-mode",
			new String[] { "single-side", "double-side", "left-side", "right-side" }, OutputPrintMode.DOUBLE_SIDE);

	/**
	 * 1枚の用紙に面付けする論理ページ数(N-up)です。1で面付けなし。
	 */
	public static final IntegerPropManager OUTPUT_N_UP = new IntegerPropManager("output.n-up", 1);

	/**
	 * N-up面付けのページの並び順です。
	 */
	public static final CodePropManager OUTPUT_N_UP_ORDER = new CodePropManager("output.n-up.order",
			new String[] { "horizontal", "horizontal-reverse", "vertical", "vertical-reverse" },
			OutputNUpOrder.HORIZONTAL);

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
	public static final CodePropManager OUTPUT_FIT_TO_PAPER = new CodePropManager("output.fit-to-paper",
			new String[] { "false", "true", "preserve-aspect-ratio" }, OutputFitToPaper.FALSE);

	/**
	 * 内容または用紙を自動回転します
	 */
	public static final CodePropManager OUTPUT_AUTO_ROTATE = new CodePropManager("output.auto-rotate",
			new String[] { "none", "content", "paper" }, OutputAutoRotate.NONE);

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
	public static final CodePropManager OUTPUT_PAGE_LIMIT_ABORT = new CodePropManager("output.page-limit.abort",
			new String[] { "force", "normal" }, OutputPageLimitAbort.FORCE);

	/**
	 * トンボの形式です。
	 */
	public static final CodePropManager OUTPUT_MARKS = new CodePropManager("output.marks",
			new String[] { "none", "crop", "cross", "both", "hidden" }, OutputMarks.NONE);

	/**
	 * 適用するCSSのメディアタイプです。
	 */
	public static final StringPropManager OUTPUT_MEDIA_TYPES = new StringPropManager("output.media_types",
			"all print paged visual bitmap static");

	/**
	 * 表示できない画像の扱いです。
	 */
	public static final CodePropManager OUTPUT_BROKEN_IMAGE = new CodePropManager("output.broken-image",
			new String[] { "none", "hidden", "cross", "annotation" }, OutputBrokenImage.NONE);

	/**
	 * カラー出力です。
	 */
	public static final CodePropManager OUTPUT_COLOR = new CodePropManager("output.color",
			new String[] { "rgb", "gray", "cmyk" }, OutputColor.RGB);

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
	public static final CodePropManager OUTPUT_PDF_COMPRESSION = new CodePropManager("output.pdf.compression",
			new String[] { "none", "ascii", "binary" }, OutputPdfCompression.BINARY);

	/**
	 * 画像の圧縮方法です。
	 */
	public static final CodePropManager OUTPUT_PDF_IMAGE_COMPRESSION = new CodePropManager(
			"output.pdf.image.compression", new String[] { "flate", "jpeg", "jpeg2000" },
			OutputPdfImageCompression.FLATE);

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
	public static final CodePropManager OUTPUT_PDF_VERSION = new CodePropManager("output.pdf.version",
			new String[] { "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.4A-1", "1.4X-1",
					"1.7A-2", "1.7A-2u", "1.7A-2a", "1.7A-3", "1.7A-3a", "2.0A-4",
					"1.6X-4", "2.0X-6", "1.7UA-1", "2.0" },
			OutputPdfVersion.V1_5);

	/**
	 * 暗号化方法です。
	 */
	public static final CodePropManager OUTPUT_PDF_ENCRYPTION = new CodePropManager("output.pdf.encryption",
			new String[] { "none", "v1", "v2", "v4", "v5" }, OutputPdfEncryption.NONE);

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
	public static final CodePropManager OUTPUT_PDF_HYPERLINKS_HREF = new CodePropManager("output.pdf.hyperlinks.href",
			new String[] { "relative", "absolute" }, OutputPdfHyperlinksHref.RELATIVE);

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
	public static final CodePropManager OUTPUT_PDF_JPEG_IMAGE = new CodePropManager("output.pdf.jpeg-image",
			new String[] { "raw", "to-flate", "recompress" }, OutputPdfJpegImage.RAW);

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
	 * 背表紙幅です。
	 */
	public static final StringPropManager OUTPUT_MARKS_SPINE_WIDTH = new StringPropManager("output.marks.spine-width",
			null);

	/**
	 * CFM暗号化です。
	 */
	public static final CodePropManager OUTPUT_PDF_ENCRYPTION_V4_CFM = new CodePropManager(
			"output.pdf.encryption.v4.cfm", new String[] { "v2", "aesv2" }, OutputPdfEncryptionV4CFM.V2);

	/**
	 * すかし画像です。
	 */
	public static final StringPropManager OUTPUT_PDF_WATERMARK_URI = new StringPropManager("output.pdf.watermark.uri",
			null);

	/**
	 * すかし画像の配置方法です。
	 */
	public static final CodePropManager OUTPUT_PDF_WATERMARK_MODE = new CodePropManager("output.pdf.watermark.mode",
			new String[] { "back", "front" }, OutputPdfWatermarkMode.BACK);

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

	public static final CodePropManager OUTPUT_PDF_VIEWER_PREFERENCES_NON_FULL_SCREEN_PAGE_MODE = new CodePropManager(
			"output.pdf.viewer-preferences.non-full-screen-page-mode",
			new String[] { "use-none", "use-outlines", "use-thumbs", "use-oc" },
			OutputPdfViewerPreferencesNoneFullScreenPageMode.USE_NONE);

	public static final CodePropManager OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING = new CodePropManager(
			"output.pdf.viewer-preferences.print-scaling", new String[] { "scaling-none", "app-default" },
			OutputPdfViewerPreferencesPrintScaling.APP_DEFAULT);

	public static final CodePropManager OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX = new CodePropManager(
			"output.pdf.viewer-preferences.duplex",
			new String[] { "none", "simplex", "flip-short-edge", "flip-long-edge", },
			OutputPdfViewerPreferencesDuplex.NONE);

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
}