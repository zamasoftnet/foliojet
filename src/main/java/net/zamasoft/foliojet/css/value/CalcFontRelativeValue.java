package net.zamasoft.foliojet.css.value;

import java.util.function.IntToDoubleFunction;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * <b>Intermediate calc() result containing font-relative units</b> (added 2026-08-03).
 *
 * <p>
 * Expressions such as {@code calc(3.5rem - 26px)} cannot be resolved at parse time:
 * {@code em} requires the element's finalized {@code font-size}; {@code rem} requires
 * the root's. This value carries absolute, ratio, and font-relative components separately
 * to the computed-value stage, where
 * {@link net.zamasoft.foliojet.css.util.ValueUtils#emExToAbsoluteLength}
 * (the single entry point used by 35 properties at computed-value time) resolves it.
 *
 * <p>
 * <b>Until 2026-08-03, calc() containing font-relative units was entirely invalidated.</b>
 * As a result, {@code left: calc(-1 * (3.5rem - 26px))}, used to put W3C specification
 * self-link symbols (¶) in the left margin, did not work, and symbols overlapped body text.
 * calc() with {@code rem} is extremely common in modern CSS; discovered in the first
 * wave of full-scale document imports (PLAN §3).
 *
 * <p>
 * Components were named fields per unit, but adding {@code cap}/{@code rlh} brought the
 * positional argument count to nine, so they became an array indexed by {@link #UNITS}
 * (2026-08-30). Addition/subtraction act per component; multiplication/division by numbers
 * act on all components. Both are linear in font metrics, so multiplying by metrics later
 * is equivalent.
 *
 * <p>
 * Nonlinear min()/max()/clamp() parts are held separately as {@link Term} (2026-10-04).
 */
public final class CalcFontRelativeValue implements QuantityValue {
	/**
	 * Parts of min()/max() whose ordering depends on font metrics (2026-10-04, publishing
	 * report: {@code min(10mm, 3em)} was considered invalid).
	 *
	 * <p>
	 * For {@code min(10mm, 3em)}, the choice is unknown until the em dimension is known,
	 * so retain it separately from linear components and select during resolution.
	 * {@code unit} returns the length (pt) of one unit at index i in {@link #UNITS}.
	 * Always evaluate all arguments before selection to learn which units are used through
	 * queries ({@link #uses}).
	 * </p>
	 */
	@FunctionalInterface
	public interface Term {
		double length(IntToDoubleFunction unit);

		/** {@code absolute + Σ font[i]·unit(i) + term}. */
		static Term linear(double absolute, double[] font, Term term) {
			final double[] components = font.clone();
			return unit -> {
				double length = absolute + (term == null ? 0 : term.length(unit));
				for (int i = 0; i < components.length; ++i) {
					if (components[i] != 0) {
						length += components[i] * unit.applyAsDouble(i);
					}
				}
				return length;
			};
		}

		/** The smaller (min) or larger of a and b. */
		static Term extremum(boolean min, Term a, Term b) {
			return unit -> {
				final double x = a.length(unit);
				final double y = b.length(unit);
				return min ? Math.min(x, y) : Math.max(x, y);
			};
		}

		/** {@code a + sign·b}. If either is null, uses only the other. */
		static Term sum(Term a, Term b, double sign) {
			if (b == null) {
				return a;
			}
			if (a == null) {
				return scaled(b, sign);
			}
			return unit -> a.length(unit) + sign * b.length(unit);
		}

		static Term scaled(Term term, double factor) {
			return term == null ? null : unit -> factor * term.length(unit);
		}

		/** Whether unit index i is used. */
		static boolean uses(Term term, int i) {
			final boolean[] used = { false };
			term.length(j -> {
				used[0] |= j == i;
				return 1;
			});
			return used[0];
		}
	}


	/** Component array order. Look up indexes with {@link #indexOf}. */
	public static final Unit[] UNITS = { Unit.EM, Unit.EX, Unit.REM, Unit.CH, Unit.LH, Unit.CAP, Unit.RLH };

	/** Index of {@link Unit#LH} (special handling avoids self-reference in line-height itself). */
	private static final int LH = 4;

	/** Returns this unit's component index, or -1 if not font-relative. */
	public static int indexOf(Unit unit) {
		for (int i = 0; i < UNITS.length; ++i) {
			if (UNITS[i] == unit) {
				return i;
			}
		}
		return -1;
	}

	/** Creates an array with all components zero. */
	public static double[] newComponents() {
		return new double[UNITS.length];
	}

	private final double absolute;
	private final double ratio;
	private final double[] font;
	/** Nonlinear part, or null if absent. */
	private final Term term;

	public static Value create(double absolute, double ratio, double[] font) {
		return create(absolute, ratio, font, null);
	}

	public static Value create(double absolute, double ratio, double[] font, Term term) {
		return new CalcFontRelativeValue(absolute, ratio, font.clone(), term);
	}

	private CalcFontRelativeValue(double absolute, double ratio, double[] font, Term term) {
		this.absolute = absolute;
		this.ratio = ratio;
		this.font = font;
		this.term = term;
	}

	/**
	 * Resolves font-relative components using style and folds them into a value with
	 * only absolute and ratio components.
	 */
	public Value resolve(CSSStyle style) {
		double abs = this.absolute;
		for (int i = 0; i < UNITS.length; ++i) {
			if (this.font[i] != 0) {
				abs += RelativeLengthValue.of(UNITS[i], this.font[i]).toAbsoluteLength(style).getLength();
			}
		}
		if (this.term != null) {
			abs += this.term.length(i -> RelativeLengthValue.of(UNITS[i], 1).toAbsoluteLength(style).getLength());
		}
		return CalcLengthValue.create(style.getUserAgent(), abs, this.ratio);
	}

	/** Whether lh is used (to avoid self-reference in line-height itself). */
	public boolean usesLh() {
		return this.font[LH] != 0 || this.term != null && Term.uses(this.term, LH);
	}

	/** Value representing {@code 100% - <unit value>} (for &lt;position&gt; edge offsets). */
	public static Value fullMinus(Unit unit, double v) {
		final int i = indexOf(unit);
		if (i < 0) {
			return null;
		}
		final double[] font = newComponents();
		font[i] = -v;
		return new CalcFontRelativeValue(0, 1, font, null);
	}

	/** Returns {@code 100% - this value} (for &lt;position&gt; edge offsets). */
	public Value subtractedFromFull() {
		return new CalcFontRelativeValue(-this.absolute, 1 - this.ratio, negated(this.font),
				Term.scaled(this.term, -1));
	}

	/** Returns a value with the lh component folded into the absolute component using the given reference line-height. */
	public Value resolveLh(net.zamasoft.foliojet.ua.UserAgent ua, double lineHeight) {
		if (!this.usesLh()) {
			return this;
		}
		final double abs = this.absolute + this.font[LH] * lineHeight;
		final double[] font = this.font.clone();
		font[LH] = 0;
		if (this.term != null) {
			final Term term = this.term;
			return new CalcFontRelativeValue(abs, this.ratio, font,
					unit -> term.length(i -> i == LH ? lineHeight : unit.applyAsDouble(i)));
		}
		if (!hasFont(font)) {
			return CalcLengthValue.create(ua, abs, this.ratio);
		}
		return new CalcFontRelativeValue(abs, this.ratio, font, null);
	}

	/** Ratio component (2026-08-19, for decomposing transform translate%). */
	public double getRatio() {
		return this.ratio;
	}

	/**
	 * Returns the absolute component after <b>approximately resolving font-relative
	 * components with UA default font metrics (medium)</b> (2026-08-19).
	 * An approximation for parse-time contexts without element font-size (e.g. transform
	 * translate), matching em/rem in media queries
	 * ({@code CSSStyleSheetBuilder.mediaFontRelativeLength}). Treats ex/ch as half em
	 * by convention, cap as 0.7em, and lh/rlh as UA normal.
	 */
	public double approximateAbsolute(net.zamasoft.foliojet.ua.UserAgent ua) {
		final double medium = ua.getFontSize(net.zamasoft.foliojet.ua.AbsoluteFontSize.MEDIUM);
		double abs = this.absolute;
		for (int i = 0; i < UNITS.length; ++i) {
			abs += this.font[i] * medium * approximateRatio(UNITS[i], ua);
		}
		if (this.term != null) {
			abs += this.term.length(i -> medium * approximateRatio(UNITS[i], ua));
		}
		return abs;
	}

	private static double approximateRatio(Unit unit, net.zamasoft.foliojet.ua.UserAgent ua) {
		switch (unit) {
		case EM:
		case REM:
			return 1;
		case EX:
		case CH:
			return 0.5;
		case CAP:
			return 0.7;
		case LH:
		case RLH:
			return ua.getNormalLineHeight();
		default:
			return 0;
		}
	}

	/**
	 * Returns a value with only the absolute component scaled. The font-size property
	 * applies zoom ({@link net.zamasoft.foliojet.ua.UserAgent#getFontMagnification}) only
	 * to absolute lengths by convention; font-relative components already use scaled
	 * reference font metrics, so are not multiplied.
	 * <p>
	 * Also scales absolute lengths inside min()/max() terms. Multiplying both absolute
	 * lengths and unit lengths in a term by the same positive factor scales the result by
	 * that factor (preserved by min/max, addition/subtraction, and multiplication/division
	 * by numbers). Thus resolve with unit lengths divided by the factor, then multiply
	 * the result by it.
	 * </p>
	 */
	public Value scaleAbsolute(double factor) {
		if (factor == 1 || this.absolute == 0 && this.term == null) {
			return this;
		}
		final Term term = this.term;
		return new CalcFontRelativeValue(this.absolute * factor, this.ratio, this.font, term == null ? null
				: unit -> factor * term.length(i -> unit.applyAsDouble(i) / factor));
	}

	/**
	 * <b>Whether the value is zero is unknown until font metrics are known</b>, so reports
	 * zero only if every component is 0 (this type is only created for nonzero components
	 * in the first place).
	 */
	public boolean isZero() {
		return this.absolute == 0 && this.ratio == 0 && !hasFont(this.font) && this.term == null;
	}

	/**
	 * Returns true <b>only when the value is certainly negative</b>. Font metrics are always
	 * positive, so if all components are negative (or zero), the result is negative too.
	 * For mixed signs, the result is unknown until resolution, so returns false
	 * (same contract as CalcLengthValue).
	 */
	public boolean isNegative() {
		if (this.term != null || this.absolute > 0 || this.ratio > 0) {
			return false;
		}
		for (final double v : this.font) {
			if (v > 0) {
				return false;
			}
		}
		if (this.absolute < 0 || this.ratio < 0) {
			return true;
		}
		for (final double v : this.font) {
			if (v < 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasFont(double[] font) {
		for (final double v : font) {
			if (v != 0) {
				return true;
			}
		}
		return false;
	}

	private static double[] negated(double[] font) {
		final double[] result = new double[font.length];
		for (int i = 0; i < font.length; ++i) {
			result[i] = -font[i];
		}
		return result;
	}

	public String toString() {
		// Write negative zero (a zero component multiplied by -1) as 0 to avoid display variation
		final StringBuilder buff = new StringBuilder("calc(").append(z(this.absolute)).append("pt + ")
				.append(z(this.ratio * 100)).append('%');
		for (int i = 0; i < UNITS.length; ++i) {
			buff.append(" + ").append(z(this.font[i])).append(UNITS[i].name().toLowerCase(java.util.Locale.ROOT));
		}
		if (this.term != null) {
			buff.append(" + min/max(...)");
		}
		return buff.append(')').toString();
	}

	private static double z(double v) {
		return v == 0 ? 0.0 : v;
	}
}
