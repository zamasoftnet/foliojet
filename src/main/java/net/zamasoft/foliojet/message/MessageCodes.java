package net.zamasoft.foliojet.message;

/**
 * メッセージコード一覧です。
 * 
 * @author MIYABE Tatsuhiko
 */
public interface MessageCodes {
	public static final short INFO_PAGE_NUMBER = 0x1801;
	public static final short INFO_HEADING_TITLE = 0x1802;
	public static final short INFO_PASS_REMAINDER = 0x1803;
	public static final short INFO_ANNOTATION = 0x1804;
	public static final short INFO_TITLE = 0x1805;
	public static final short INFO_PAGE_HEIGHT = 0x1806;
	public static final short INFO_PLUGIN = 0x18FF;

	public static final short WARN_BAD_CSS_SYNTAX = 0x2801;
	public static final short WARN_UNSUPPORTED_CSS_PROPERTY = 0x2802;
	public static final short WARN_MISSING_CSS_STYLESHEET = 0x2803;
	public static final short WARN_BAD_IO_PROPERTY = 0x2804;
	public static final short WARN_BAD_PI_SYNTAX = 0x2805;
	public static final short WARN_DEEP_IMPORT = 0x2806;
	public static final short WARN_LOOP_IMPORT = 0x2807;
	public static final short WARN_BAD_HTML_ATTRIBUTE = 0x2808;
	public static final short WARN_BAD_HEADER = 0x280A;
	public static final short WARN_BAD_URI_PATTERN = 0x280B;
	public static final short WARN_BAD_LINK_URI = 0x280C;
	public static final short WARN_SVG = 0x280D;
	public static final short WARN_MISSING_XSLT_STYLESHEET = 0x280E;
	public static final short WARN_CANNOT_OVERRIDE_PROPERTY = 0x280F;
	public static final short WARN_MISSING_ATTACHMENT = 0x2810;
	/**
	 * 画像を読み込めない警告です。引数は {0}=userinfoを除いたURI、
	 * {1}=失敗段階({@code resolve}、{@code fetch}、
	 * {@code fetch: HTTP nnn}、{@code decode})です。
	 */
	public static final short WARN_MISSING_IMAGE = 0x2811;
	public static final short WARN_UNSUPPORTED_PDF_CAPABILITY = 0x2812;
	public static final short WARN_BAD_INLINE_OBJECT = 0x2813;
	public static final short WARN_BLOCKED_RESOURCE = 0x2814;
	public static final short WARN_LICENSE_CONSTRAINT_CSS = 0x2815;
	public static final short WARN_BAD_CSS_ARGMENTS = 0x2816;
	public static final short WARN_BAD_INLINE_CSS = 0x2817;
	public static final short WARN_UNSUPPORTED_IO_PROPERTY = 0x2818;
	public static final short WARN_LICENSE_CONSTRAINT_IO = 0x281B;
	public static final short WARN_MISSING_PROFILE = 0x281C;
	public static final short WARN_UNSUPPORTED_ENCODING = 0x281D;
	public static final short WARN_MISSING_FONT_FILE = 0x281E;
	public static final short WARN_MISSING_FONT = 0x281F;
	public static final short WARN_MISSING_FONT_OUTLINE = 0x2820;
	/**
	 * 静的な組版に意味がないため<b>意図して対応しない</b>プロパティ
	 * (2026-08-28)。{@link #WARN_UNSUPPORTED_CSS_PROPERTY}(いずれ対応
	 * しうる未実装)と区別するために分けた——実サイトの警告を数えて
	 * 実装候補を選ぶとき、この2つが混ざっていると候補を絞れない。
	 */
	public static final short WARN_IGNORED_CSS_PROPERTY = 0x2821;
	/**
	 * 現在の出力形式では厳密に描けず<b>近似で描画した</b>機能(2026-08-29)。
	 * 引数は {0}=CSSプロパティ名、{1}=出力のMIME型、{2}=近似の内容。
	 * 描画時に、近似経路を実際に通ったときだけ、文書ごと・機能ごとに
	 * 1回出す。透明を使える通常PDFでは影だけをラスタ化して厳密にぼかすが、
	 * PDF/A-1など透明を使えないプロファイルやラスタ化を拒否した場合は近似へ
	 * 戻る。円錐グラデーション等も含め、同じ文書でも出力形式・プロファイルで
	 * 警告が変わる。
	 */
	public static final short WARN_APPROXIMATED_RENDERING = 0x2822;
	/**
	 * 宣言は解釈できたが、<b>この組み合わせでは効かない</b>指定(2026-08-29)。
	 * 引数は {0}=CSSプロパティ名、{1}=効かない理由。
	 * {@link #WARN_UNSUPPORTED_CSS_PROPERTY}(プロパティ自体が未実装)や
	 * {@link #WARN_IGNORED_CSS_PROPERTY}(静的組版では無意味)と違い、
	 * <b>単体なら効くのに文脈のせいで落ちる</b>ものを知らせる——利用者が
	 * 最も時間を溶かすのは「書いたのに効かない」なので、黙って捨てない。
	 */
	public static final short WARN_INEFFECTIVE_CSS_COMBINATION = 0x2823;
	public static final short WARN_PLUGIN = 0x28FF;

	public static final short ERROR_BAD_XSLT_STYLESHEET = 0x3801;
	public static final short ERROR_BAD_PAGE_SIZE = 0x3802;
	public static final short ERROR_BAD_XML_SYNTAX = 0x3803;
	public static final short ERROR_OUTPUT_FILE_TOO_LARGE = 0x3804;
	public static final short ERROR_OUT_OF_PAGE_LIMIT = 0x3805;
	public static final short ERROR_MISSING_SERVERSIDE_DOCUMENT = 0x3806;
	public static final short ERROR_INVALID_LICENSE = 0x3807;
	public static final short ERROR_XSLT_WARN = 0x3808;
	public static final short ERROR_XSLT_ERROR = 0x3809;
	public static final short ERROR_EXPIRED_LICENSE = 0x380B;
	public static final short ERROR_UNLICENSED = 0x380C;
	public static final short ERROR_NO_CONTENT = 0x380D;
	public static final short ERROR_PLUGIN = 0x38FF;

	public static final short FATAL_XSLT_FATAL = 0x4801;
	public static final short FATAL_PLUGIN = 0x48FF;
}
