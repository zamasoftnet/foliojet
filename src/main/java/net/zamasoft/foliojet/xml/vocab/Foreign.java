package net.zamasoft.foliojet.xml.vocab;

/**
 * HTML5の<b>foreign content</b>({@code <math>}と{@code <svg>})の名前空間です。
 *
 * <p>
 * <b>HTMLでは{@code xmlns}を書かないのが普通である。</b>HTML5はこの2つを
 * 構文解析の段階で正しい名前空間へ入れる(ブラウザは全部そうする)ので、
 * 著者は何も書かない。XMLの規則しか知らない経路を通すと、これらが
 * XHTMLの要素になって<b>MathMLが平らな文字列に、SVGがただの入れ子要素に
 * なる</b>。
 *
 * <p>
 * 2026-08-05に実地コーパス第11波で発覚した。arXivが今HTMLを出している形
 * (ar5iv/LaTeXML)がまさに{@code xmlns}無しで、{@code h_{t}} が
 * 「htsubscript … h_{t}」と出ていた——{@code <annotation>}の中の生のLaTeXまで
 * 一緒に流れていたためである。
 *
 * <p>
 * <b>2箇所で守る必要がある</b>。片方だけでは効かない:
 * <ul>
 * <li>{@code HTMLParser} —— {@code xmlns}が無い{@code <math>}/{@code <svg>}に
 * 名前空間を<b>与える</b>(NekoHTMLはforeign contentを実装していない)</li>
 * <li>{@code XHTMLNSFilter} —— 与えた名前空間を<b>XHTMLへ潰さない</b>。
 * この経路は「接頭辞の宣言が無く、局所名と修飾名が同じ」要素を一律XHTMLに
 * するので、素通しにすると{@code <math>}も巻き込まれる</li>
 * </ul>
 *
 * @author MIYABE Tatsuhiko
 */
public final class Foreign {
	public static final String MATHML_URI = "http://www.w3.org/1998/Math/MathML";

	public static final String SVG_URI = "http://www.w3.org/2000/svg";

	private Foreign() {
		// ユーティリティ
	}

	/** その名前空間はXHTMLへ潰してはいけないか。 */
	public static boolean is(String uri) {
		return MATHML_URI.equals(uri) || SVG_URI.equals(uri);
	}

	/** その要素名はforeign contentの入口か。入口でなければ{@code null}。 */
	public static String uriOf(String localName) {
		if ("math".equalsIgnoreCase(localName)) {
			return MATHML_URI;
		}
		if ("svg".equalsIgnoreCase(localName)) {
			return SVG_URI;
		}
		return null;
	}
}
