package net.zamasoft.foliojet.css.token;

import java.util.Locale;

/**
 * Units for dimension tokens.
 */
public enum Unit {
	EM, EX, REM, CH, LH, PX, IN, CM, MM, Q, PT, PC, DEG, GRAD, RAD, MS, S, HZ, KHZ,
	/**
	 * Additional font-relative units (2026-08-30; css-values-4).
	 * {@code cap} is the first available font's cap-height; {@code rlh} is the root element's
	 * computed line-height. {@code ic} (advance of the ideograph "水", U+6C34) is
	 * <b>folded into {@code em} using the specification's fallback</b>: a full-width
	 * ideograph's advance is effectively 1em. {@link #of} maps {@code ic}/{@code ric}
	 * to {@link #EM}/{@link #REM}.
	 */
	CAP, RLH,
	/** Container query units (stage 6, 2026-08-15; css-contain-3). */
	CQW, CQI,
	/**
	 * Viewport units (2026-08-29). Print has no dynamic viewport, so fold
	 * {@code svw}/{@code lvw}/{@code dvw} (css-values-4 small/large/dynamic) into
	 * {@code vw}, and {@code vi}/{@code vb} (logical axes) into {@code vw}/{@code vh}
	 * assuming horizontal writing (see {@link #of}). Resolved by {@code ViewportUnits}
	 * (1% of the page's type area dimensions).
	 */
	VW, VH, VMIN, VMAX,
	/** Other units (text retained by {@link CssToken.Dim#unitText()}). */
	OTHER;

	/**
	 * Resolves unit text. Returns OTHER for unknown units.
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
		case "cap":
			return CAP;
		case "rlh":
			return RLH;
		case "ic":
			return EM;
		case "ric":
			return REM;
		case "px":
			return PX;
		case "in":
			return IN;
		case "cm":
			return CM;
		case "mm":
			return MM;
		case "q":
			return Q;
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
