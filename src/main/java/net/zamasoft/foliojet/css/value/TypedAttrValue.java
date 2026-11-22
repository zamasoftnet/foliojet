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
 * {@link CSSStyle#get} substitutes an {@code attr()} that is the whole value of a property, on the element
 * that declares it, before the property computes its value; one inside another value is resolved by
 * {@link net.zamasoft.foliojet.css.util.ValueUtils#emExToAbsoluteLength}, the same place
 * that resolves {@code em}/{@code rem} and font-relative components of {@code calc()}.
 *
 * <p>
 * <b>What it resolves to is a value the receiving property takes</b>. The parser of the property says which
 * values those are ({@link Kind}, and whether a length may be negative); an attribute that does not hold one of
 * them (it is empty, not a number, a percentage where only a length is taken, negative where it must not be) gives
 * the fallback, which was checked the same way when the declaration was parsed. Without a fallback the declaration
 * is invalid at computed-value time and the property behaves as {@code unset}. HTML written by hand has such
 * attributes ({@code <table border="5%">}, {@code <hr noshade size>}, {@code <body text="">}); passed on as they
 * were, their values failed the casts of the properties and ended the conversion.
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
		/** A {@code <length>}. A percentage in the attribute is not a value of this type. */
		LENGTH,
		/**
		 * A {@code <length-percentage>}: a length, or a percentage as in HTML {@code width="50%"}. The type of the
		 * properties that take both (sizes, margins, padding); an {@code attr()} written with a unit or with
		 * {@code type(<length>)} has this type in their values.
		 */
		LENGTH_PERCENTAGE, COLOR, NUMBER, INTEGER
	}

	private final String name;
	private final Kind kind;
	/** For {@link Kind#LENGTH}, the unit to add to unitless numbers. */
	private final Unit unit;
	/** The value when the attribute is absent or cannot be parsed (if unspecified, null invalidates the declaration). */
	private final Value fallback;
	/** For lengths and percentages, whether the receiving property takes negative ones (margins and insets do). */
	private final boolean negative;

	/**
	 * @param kind     the type of the values the receiving property takes
	 * @param unit     for lengths, the unit of an attribute that is a bare number
	 * @param fallback the value when the attribute does not hold a value of the type, or null
	 * @param negative for lengths and percentages, whether the receiving property takes negative ones
	 */
	public static TypedAttrValue create(String name, Kind kind, Unit unit, Value fallback, boolean negative) {
		return new TypedAttrValue(name, kind, unit, fallback, negative);
	}

	private TypedAttrValue(String name, Kind kind, Unit unit, Value fallback, boolean negative) {
		this.name = name;
		this.kind = kind;
		this.unit = unit;
		this.fallback = fallback;
		this.negative = negative;
	}

	/**
	 * Reads the attribute and resolves it to a value. If it is absent or does not hold a value the receiving
	 * property takes, returns the fallback, or null if no fallback exists (<b>invalid at computed-value time</b>:
	 * the property behaves as {@code unset}).
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
		case LENGTH_PERCENTAGE:
		default: {
			final QuantityValue length = this.parseLength(raw, style);
			if (length != null && !this.negative && length.isNegative()) {
				// The property takes no negative length (a border width, a padding, a size)
				return null;
			}
			return length;
		}
		}
	}

	/** The length or, for {@link Kind#LENGTH_PERCENTAGE}, the percentage in the attribute; null if it holds neither. */
	private QuantityValue parseLength(String raw, CSSStyle style) {
		// **Accept percentages too** (2026-08-03). HTML width="50%" is common.
		if (raw.endsWith("%")) {
			if (this.kind != Kind.LENGTH_PERCENTAGE) {
				// The property takes lengths only (border="5%", cellspacing="5%")
				return null;
			}
			final double pct = parseNumber(raw.substring(0, raw.length() - 1).trim(), false);
			return Double.isNaN(pct) ? null : PercentageValue.create(pct);
		}
		// Honor an explicit unit, or supply the specified unit if absent.
		// (HTML width="200" means 200px.)
		final LengthValue length = net.zamasoft.foliojet.css.util.ValueUtils.toLength(style.getUserAgent(), false,
				raw);
		if (length != null) {
			// "1e999px" is a number with a unit to the parser, an infinite one
			if (length instanceof AbsoluteLengthValue absolute && !Double.isFinite(absolute.getLength())) {
				return null;
			}
			if (length instanceof RelativeLengthValue relative && !Double.isFinite(relative.getValue())) {
				return null;
			}
			return length;
		}
		final double v = parseNumber(raw, false);
		if (Double.isNaN(v)) {
			return null;
		}
		// **Do not construct font-relative units as absolute lengths** (2026-08-03).
		// em/ex/rem/ch need font sizes, so return relative lengths and let
		// the same entry point (emExToAbsoluteLength) resolve them.
		// Build the value as the length parser builds a dimension token of this unit, so that
		// every length unit resolves as it does in a style sheet (cap, rlh, viewport and container
		// units went to AbsoluteLengthValue.create, which threw IllegalArgumentException).
		// A unit that is not a length gives null: the fallback.
		return net.zamasoft.foliojet.css.util.ValueUtils.toLength(style.getUserAgent(),
				new net.zamasoft.foliojet.css.token.CssToken.Dim(v, this.unit, this.unit.name()));
	}

	/** The number in the attribute, or NaN if it is not one. A number that is not finite is not one either. */
	private static double parseNumber(String raw, boolean integer) {
		try {
			final double v = Double.parseDouble(raw);
			if (!Double.isFinite(v)) {
				// "Infinity", "1e999": a length made of it reached the layout as an infinite size
				return Double.NaN;
			}
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
