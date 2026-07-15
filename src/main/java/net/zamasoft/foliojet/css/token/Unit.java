package net.zamasoft.foliojet.css.token;

import java.util.Locale;

/**
 * 寸法トークンの単位です。
 */
public enum Unit {
	EM, EX, REM, CH, PX, IN, CM, MM, PT, PC, DEG, GRAD, RAD, MS, S, HZ, KHZ,
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
		default:
			return OTHER;
		}
	}
}
