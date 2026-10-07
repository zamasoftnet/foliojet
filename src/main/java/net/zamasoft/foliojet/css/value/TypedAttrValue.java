package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * An unresolved value for <b>typed {@code attr()}</b> (CSS Values 5; added on 2026-08-03).
 *
 * <p>
 * Allows element attributes in arbitrary properties, as in {@code width: attr(width px, auto)}.
 * Since the attribute belongs to the element, the value is determined at the computed-value stage:
 * {@link net.zamasoft.foliojet.css.util.ValueUtils#emExToAbsoluteLength}
 * (the single entry point used by 35 properties) resolves it. This is the same place
 * that resolves {@code em}/{@code rem} and font-relative components of {@code calc()}.
 *
 * <p>
 * <b>Why implement this</b>: all three engines map HTML presentational attributes
 * (such as {@code <td width=200>}) in C++ code. This product aims to express those mappings in CSS,
 * because defaults buried in Java remain unnoticed (encountered in practice on 2026-08-03
 * with {@code height:1em} on {@code <button>}). Chrome shipped typed {@code attr()} in 133,
 * so <b>both a real specification and a real implementation exist</b>.
 * The main reason vendors delayed it for so long (recalculation when attributes change dynamically)
 * does not apply to this engine, which has no scripting.
 *
 * <p>
 * <b>Cannot construct URLs</b> (as specified). It cannot be used inside {@code url()},
 * as that would provide a path for data exfiltration. Only lengths, colors, and numbers are handled here.
 */
public final class TypedAttrValue implements QuantityValue, PaintValue {
	/** The type to extract. */
	public enum Kind {
		LENGTH, COLOR, NUMBER, INTEGER,
		/**
		 * Interprets the attribute contents as <b>the font-family value itself</b> (a comma-separated list)
		 * (2026-08-03, for mapping {@code <font face>}). This proprietary type is absent from CSS Values 5
		 * and is used instead of {@code type(<custom-ident>+)}.
		 */
		FONT_FAMILY
	}

	private final String name;
	private final Kind kind;
	/** For {@link Kind#LENGTH}, the unit to add to unitless numbers. */
	private final Unit unit;
	/** The value when the attribute is absent or cannot be parsed (if unspecified, null invalidates the declaration). */
	private final Value fallback;

	public static TypedAttrValue create(String name, Kind kind, Unit unit, Value fallback) {
		return new TypedAttrValue(name, kind, unit, fallback);
	}

	private TypedAttrValue(String name, Kind kind, Unit unit, Value fallback) {
		this.name = name;
		this.kind = kind;
		this.unit = unit;
		this.fallback = fallback;
	}

	public String getName() {
		return this.name;
	}

	public Kind getKind() {
		return this.kind;
	}

	public Unit getUnit() {
		return this.unit;
	}

	/**
	 * Reads the attribute and resolves it to a value. If it is absent or cannot be parsed,
	 * returns the fallback, or null if no fallback exists (<b>invalid at used-value computation time</b>:
	 * the caller ignores this declaration).
	 */
	public Value resolve(CSSStyle style) {
		final CSSElement ce = style.getCSSElement();
		final String raw = ce == null || ce.atts == null ? null : ce.atts.getValue(this.name);
		if (raw != null) {
			final Value value = parse(raw.trim(), style);
			if (value != null) {
				return value;
			}
		}
		return this.fallback;
	}

	private Value parse(String raw, CSSStyle style) {
		if (raw.isEmpty()) {
			return null;
		}
		switch (this.kind) {
		case FONT_FAMILY:
			return net.zamasoft.foliojet.css.util.FontValueUtils.toFontFamily(raw);
		case COLOR: {
			// HTML bgcolor, etc. can contain "red", "#ff0000", or "ff0000".
			Value named = net.zamasoft.foliojet.css.util.ColorValueUtils.toColorValue(raw);
			if (named != null) {
				return named;
			}
			return net.zamasoft.foliojet.css.util.ColorValueUtils
					.parseRGBHexColor(raw.startsWith("#") ? raw.substring(1) : raw);
		}
		case INTEGER:
		case NUMBER: {
			final double v = parseNumber(raw, this.kind == Kind.INTEGER);
			return Double.isNaN(v) ? null : RealValue.create(v);
		}
		case LENGTH:
		default: {
			// **Accept percentages too** (2026-08-03). HTML width="50%" is common.
			if (raw.endsWith("%")) {
				final double pct = parseNumber(raw.substring(0, raw.length() - 1).trim(), false);
				return Double.isNaN(pct) ? null : PercentageValue.create(pct);
			}
			// Honor an explicit unit, or supply the specified unit if absent.
			// (HTML width="200" means 200px.)
			final Value length = net.zamasoft.foliojet.css.util.ValueUtils.toLength(style.getUserAgent(), false, raw);
			if (length != null) {
				return length;
			}
			final double v = parseNumber(raw, false);
			if (Double.isNaN(v)) {
				return null;
			}
			// **Do not construct font-relative units as absolute lengths** (2026-08-03).
			// em/ex/rem/ch need font sizes, so return relative lengths and let
			// the same entry point (emExToAbsoluteLength) resolve them.
			switch (this.unit) {
			case EM:
			case EX:
			case REM:
			case CH:
			case LH:
				return RelativeLengthValue.of(this.unit, v);
			default:
				return AbsoluteLengthValue.create(style.getUserAgent(), v, this.unit);
			}
		}
		}
	}

	private static double parseNumber(String raw, boolean integer) {
		try {
			final double v = Double.parseDouble(raw);
			if (integer && v != Math.floor(v)) {
				return Double.NaN;
			}
			return v;
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/**
	 * <b>Paint is unavailable before resolution</b>. Implements {@link PaintValue} so that this type
	 * can also be returned in color contexts. Reaching here means computed-value resolution
	 * has not run, which is an implementation error.
	 */
	public net.zamasoft.pdfg2d.gc.paint.Paint getPaint(java.awt.geom.Rectangle2D box) {
		throw new IllegalStateException("attr()が解決されないまま塗りとして使われた: " + this);
	}

	/** Whether the value is zero is unknown before resolution. */
	public boolean isZero() {
		return false;
	}

	/** Whether the value is negative is unknown before resolution (checked afterward in contexts that reject negatives). */
	public boolean isNegative() {
		return false;
	}

	public String toString() {
		return "attr(" + this.name + " " + this.kind + (this.fallback == null ? "" : ", " + this.fallback) + ")";
	}
}
