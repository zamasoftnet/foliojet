package net.zamasoft.foliojet.ua.props;

/**
 * 単一SVG出力で文字をどう書くかです(B-1、2026-08-29の利用者要望)。
 *
 * <p>
 * ページ分割SVGは以前から{@code <text>}＋WOFF2サブセットで書いています。
 * 同じ仕組みを、1枚で完結するSVGでも選べるようにしたものです。
 * </p>
 */
public enum SvgTextMode implements PropCode {
	/**
	 * 字形をアウトライン(path)にします(既定)。
	 *
	 * <p>
	 * どこで開いても同じ絵になりますが、文字は図形なので選べません。
	 * </p>
	 */
	OUTLINE,

	/**
	 * {@code <text>}のまま残し、サブセットしたWOFF2をSVGへ埋め込みます。
	 *
	 * <p>
	 * 1枚で完結させるため、フォントも画像も{@code data:}でSVGの中に入ります。
	 * ページ分割SVGのように共有できないので、ページ数が多いと総量は増えます。
	 * </p>
	 */
	KEEP;
}
