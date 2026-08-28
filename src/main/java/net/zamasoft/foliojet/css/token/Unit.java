package net.zamasoft.foliojet.css.token;

import java.util.Locale;

/**
 * 寸法トークンの単位です。
 */
public enum Unit {
	EM, EX, REM, CH, LH, PX, IN, CM, MM, PT, PC, DEG, GRAD, RAD, MS, S, HZ, KHZ,
	/** コンテナクエリ単位(段6、2026-08-15——css-contain-3)。 */
	CQW, CQI,
	/**
	 * ビューポート単位(2026-08-29)。印刷では動的ビューポートが無いので、
	 * {@code svw}/{@code lvw}/{@code dvw}(css-values-4のsmall/large/dynamic)は
	 * すべて{@code vw}へ、{@code vi}/{@code vb}(論理軸)は横書き前提で
	 * {@code vw}/{@code vh}へ畳む({@link #of}参照)。解決は
	 * {@code ViewportUnits}(ページの版面寸法の1%)。
	 */
	VW, VH, VMIN, VMAX,
	/** 上記以外(テキストは {@link CssToken.Dim#unitText()} で保持)。 */
	OTHER;

	/**
	 * 単位テキストから解決します。未知の単位は OTHER を返します。
	 */
	public static Unit of(String text) {
		switch (text.toLowerCase(Locale.ROOT)) {
		case "em":
			return EM;
		case "ex":
			return EX;
		case "rem":
			return REM;
		case "ch":
			return CH;
		case "lh":
			return LH;
		case "px":
			return PX;
		case "in":
			return IN;
		case "cm":
			return CM;
		case "mm":
			return MM;
		case "pt":
			return PT;
		case "pc":
			return PC;
		case "deg":
			return DEG;
		case "grad":
			return GRAD;
		case "rad":
			return RAD;
		case "ms":
			return MS;
		case "s":
			return S;
		case "hz":
			return HZ;
		case "khz":
			return KHZ;
		case "cqw":
			return CQW;
		case "cqi":
			return CQI;
		case "vw":
		case "svw":
		case "lvw":
		case "dvw":
		case "vi":
		case "svi":
		case "lvi":
		case "dvi":
			return VW;
		case "vh":
		case "svh":
		case "lvh":
		case "dvh":
		case "vb":
		case "svb":
		case "lvb":
		case "dvb":
			return VH;
		case "vmin":
		case "svmin":
		case "lvmin":
		case "dvmin":
			return VMIN;
		case "vmax":
		case "svmax":
		case "lvmax":
		case "dvmax":
			return VMAX;
		default:
			return OTHER;
		}
	}
}
