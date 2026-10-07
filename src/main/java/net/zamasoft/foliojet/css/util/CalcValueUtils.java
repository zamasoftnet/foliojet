package net.zamasoft.foliojet.css.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.AngleValue;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue.Term;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Evaluates calc(), min(), max(), clamp(), and CSS Values 4 math functions.
 * <p>
 * Supported operands are {@code <number>}, absolute-unit {@code <length>}
 * (px/pt/in/cm/mm/Q/pc), and {@code <percentage>}. Results mixing absolute lengths
 * and percentages (e.g. {@code calc(50% + 10px)}) are returned as {@link CalcLengthValue};
 * actual resolution occurs during layout
 * (via {@link net.zamasoft.foliojet.layout.box.params.LengthType#MIXED}).
 * </p>
 * <p>
 * Font-relative units (em/rem, etc.) are carried as coefficients and resolved at the
 * computed-value stage ({@link CalcFontRelativeValue}). min()/max()/clamp() comparing
 * absolute lengths and font-relative units (e.g. {@code min(10mm, 3em)}) also select
 * the smaller/larger value at that stage (2026-10-04).
 * </p>
 * <p>
 * <b>Currently unsupported (returns null as evaluation failure)</b>: var() (handled
 * separately because it requires a different architecture for cascade-time resolution);
 * min()/max()/clamp() comparing percentages and other lengths (e.g.
 * {@code min(10px, 50%)}, since ordering is unknown until used-value computation
 * establishes the reference value).
 * </p>
 */
public final class CalcValueUtils {
	private CalcValueUtils() {
		// utility
	}

	/**
	 * Maximum nesting depth of function calls (calc(), etc.). Increases only for nesting
	 * across function boundaries, e.g. calc(min(calc(...))), not for the number of terms
	 * within one calc(). This is the syntax nesting depth actually written by the CSS
	 * author, unlike unbounded depth from external data such as HTML documents (no risk
	 * of infinite recursion; the limit is purely a safeguard), so recursion is used only
	 * here. The explicit limit makes StackOverflowError structurally impossible.
	 */
	private static final int MAX_FUNCTION_DEPTH = 32;

	/**
	 * Evaluates a token if it is a calc()/min()/max()/clamp() call and returns the resulting
	 * Value (RealValue/AngleValue/AbsoluteLengthValue/PercentageValue/CalcLengthValue).
	 * Returns null for other tokens or evaluation failures (the caller can continue normal
	 * fallback processing for an uninterpretable token).
	 */
	public static Value toCalc(UserAgent ua, CssToken token) {
		if (!(token instanceof CssToken.Func func)) {
			return null;
		}
		Quantity result = evaluateFunc(ua, func, 0);
		return result != null ? result.toValue(ua) : null;
	}

	/**
	 * Internal evaluation representation: either {@code <number>} or {@code <length-percentage>}.
	 *
	 * <p>
	 * <b>Font-relative units (em/ex/rem/ch) cannot be resolved at parse time</b>, so retain
	 * separate coefficients per unit (2026-08-03). Addition/subtraction act per component;
	 * multiplication/division by numbers act on all components. Both are linear in font
	 * metrics, so multiplying by metrics later is equivalent.
	 */
	private static final class Quantity {
		private enum Kind {
			NUMBER, LENGTH, ANGLE
		}

		final Kind kind;
		final double number;
		final double absolute;
		final double ratio;
		/** Font-relative components, in the same order as {@link CalcFontRelativeValue#UNITS}. */
		final double[] font;
		/** Parts of min()/max() whose ordering depends on font metrics, or null if none. */
		final Term term;

		static Quantity number(double v) {
			return Double.isFinite(v)
					? new Quantity(Kind.NUMBER, v, 0, 0, CalcFontRelativeValue.newComponents(), null)
					: null;
		}

		static Quantity angle(double degrees) {
			return Double.isFinite(degrees)
					? new Quantity(Kind.ANGLE, degrees, 0, 0, CalcFontRelativeValue.newComponents(), null)
					: null;
		}

		static Quantity length(double absolute, double ratio) {
			return length(absolute, ratio, CalcFontRelativeValue.newComponents());
		}

		static Quantity length(double absolute, double ratio, double[] font) {
			return length(absolute, ratio, font, null);
		}

		static Quantity length(double absolute, double ratio, double[] font, Term term) {
			if (!Double.isFinite(absolute) || !Double.isFinite(ratio)) {
				return null;
			}
			for (final double v : font) {
				if (!Double.isFinite(v)) {
					return null;
				}
			}
			return new Quantity(Kind.LENGTH, 0, absolute, ratio, font, term);
		}

		/** One font-relative unit. */
		static Quantity font(Unit unit, double v) {
			final int i = CalcFontRelativeValue.indexOf(unit);
			if (i < 0) {
				return null;
			}
			final double[] font = CalcFontRelativeValue.newComponents();
			font[i] = v;
			return length(0, 0, font);
		}

		private Quantity(Kind kind, double number, double absolute, double ratio, double[] font, Term term) {
			this.kind = kind;
			this.number = number;
			this.absolute = absolute;
			this.ratio = ratio;
			this.font = font;
			this.term = term;
		}

		/** Whether any part remains unresolved until font metrics are known. */
		boolean hasFont() {
			if (this.term != null) {
				return true;
			}
			for (final double v : this.font) {
				if (v != 0) {
					return true;
				}
			}
			return false;
		}

		/** Returns an array with a binary operation applied component-wise. */
		static double[] zip(double[] a, double[] b, java.util.function.DoubleBinaryOperator op) {
			final double[] result = new double[a.length];
			for (int i = 0; i < a.length; ++i) {
				result[i] = op.applyAsDouble(a[i], b[i]);
			}
			return result;
		}

		/** Returns an array with all components multiplied by a constant (pass its reciprocal as factor for division). */
		double[] scaled(double factor) {
			final double[] result = new double[this.font.length];
			for (int i = 0; i < this.font.length; ++i) {
				result[i] = this.font[i] * factor;
			}
			return result;
		}

		Value toValue(UserAgent ua) {
			if (this.kind == Kind.NUMBER) {
				return RealValue.create(this.number);
			}
			if (this.kind == Kind.ANGLE) {
				return AngleValue.create(this.number);
			}
			if (this.hasFont()) {
				// Resolve at the computed-value stage, when font metrics are known
				return CalcFontRelativeValue.create(this.absolute, this.ratio, this.font, this.term);
			}
			return CalcLengthValue.create(ua, this.absolute, this.ratio);
		}
	}

	private static Quantity evaluateFunc(UserAgent ua, CssToken.Func func, int depth) {
		if (depth > MAX_FUNCTION_DEPTH) {
			return null;
		}
		String name = func.name().toLowerCase(Locale.ROOT);
		switch (name) {
		case "calc":
			return evaluateCalcRpn(ua, func.args(), depth);
		case "min":
			return evaluateMinMax(ua, func, depth, true);
		case "max":
			return evaluateMinMax(ua, func, depth, false);
		case "clamp":
			return evaluateClamp(ua, func, depth);
		case "sqrt":
		case "exp":
		case "sin":
		case "cos":
		case "tan":
			return evaluateMath1(ua, func, depth, name);
		case "pow":
			return evaluateMath2(ua, func, depth, name);
		case "log":
			return evaluateLog(ua, func, depth);
		case "hypot":
			return evaluateHypot(ua, func, depth);
		case "asin":
		case "acos":
		case "atan":
		case "atan2":
			return evaluateInverseTrig(ua, func, depth, name);
		default:
			return null;
		}
	}

	/**
	 * Single-argument math functions returning numbers (css-values-4).
	 *
	 * <p>
	 * {@code sqrt()} and {@code exp()} take {@code <number>} and return {@code <number>};
	 * {@code sin()}, {@code cos()}, and {@code tan()} take {@code <angle>} or
	 * {@code <number>} (radians) and return {@code <number>}. Out-of-domain inputs
	 * (e.g. {@code sqrt(-1)}) and overflow yield {@code null} (=invalid value), like existing
	 * evaluation failures, to keep NaN/Infinity out of type area dimensions.
	 * </p>
	 */
	private static Quantity evaluateMath1(UserAgent ua, CssToken.Func func, int depth, String name) {
		List<TokenStream> groups = func.argStream().splitComma();
		if (groups.size() != 1) {
			return null;
		}
		final Double a = switch (name) {
		case "sin", "cos", "tan" -> radiansArg(ua, groups.get(0), depth);
		default -> numberArg(ua, groups.get(0), depth);
		};
		if (a == null) {
			return null;
		}
		final double r = switch (name) {
		case "sqrt" -> Math.sqrt(a);
		case "exp" -> Math.exp(a);
		case "sin" -> Math.sin(a);
		case "cos" -> Math.cos(a);
		case "tan" -> Math.tan(a);
		default -> Double.NaN;
		};
		return finite(r);
	}

	/** Two-argument math function returning a number ({@code pow()}). */
	private static Quantity evaluateMath2(UserAgent ua, CssToken.Func func, int depth, String name) {
		List<TokenStream> groups = func.argStream().splitComma();
		if (groups.size() != 2) {
			return null;
		}
		final Double a = numberArg(ua, groups.get(0), depth);
		final Double b = numberArg(ua, groups.get(1), depth);
		if (a == null || b == null) {
			return null;
		}
		return finite("pow".equals(name) ? Math.pow(a, b) : Double.NaN);
	}

	/** Inverse trigonometric functions; retains results as {@code <angle>} in deg. */
	private static Quantity evaluateInverseTrig(UserAgent ua, CssToken.Func func, int depth, String name) {
		List<TokenStream> groups = func.argStream().splitComma();
		int expected = "atan2".equals(name) ? 2 : 1;
		if (groups.size() != expected) {
			return null;
		}
		Double a = numberArg(ua, groups.get(0), depth);
		Double b = expected == 2 ? numberArg(ua, groups.get(1), depth) : null;
		if (a == null || expected == 2 && b == null) {
			return null;
		}
		double radians = switch (name) {
		case "asin" -> Math.asin(a);
		case "acos" -> Math.acos(a);
		case "atan" -> Math.atan(a);
		case "atan2" -> Math.atan2(a, b);
		default -> Double.NaN;
		};
		return Quantity.angle(Math.toDegrees(radians));
	}

	/** {@code log(A)} (natural logarithm) and {@code log(A, B)} (base B). */
	private static Quantity evaluateLog(UserAgent ua, CssToken.Func func, int depth) {
		List<TokenStream> groups = func.argStream().splitComma();
		if (groups.isEmpty() || groups.size() > 2) {
			return null;
		}
		final Double a = numberArg(ua, groups.get(0), depth);
		if (a == null) {
			return null;
		}
		if (groups.size() == 1) {
			return finite(Math.log(a));
		}
		final Double b = numberArg(ua, groups.get(1), depth);
		if (b == null) {
			return null;
		}
		return finite(Math.log(a) / Math.log(b));
	}

	/**
	 * {@code hypot()}. Accepts only {@code <number>} arguments (the specification also
	 * allows lengths, etc. of the same type, but a sum of squares for Quantity's individual
	 * components requires type conversion, so this is limited to numbers).
	 */
	private static Quantity evaluateHypot(UserAgent ua, CssToken.Func func, int depth) {
		List<TokenStream> groups = func.argStream().splitComma();
		if (groups.isEmpty()) {
			return null;
		}
		double result = 0;
		for (TokenStream group : groups) {
			final Double v = numberArg(ua, group, depth);
			if (v == null) {
				return null;
			}
			result = Math.hypot(result, v);
		}
		return finite(result);
	}

	/** Evaluates an argument as {@code <number>}. Returns null if it is not numeric. */
	private static Double numberArg(UserAgent ua, TokenStream group, int depth) {
		final Quantity q = evaluateSingleArg(ua, group, depth);
		if (q == null || q.kind != Quantity.Kind.NUMBER) {
			return null;
		}
		return q.number;
	}

	/**
	 * Evaluates a trigonometric argument in radians. {@code <number>} is already radians;
	 * converts {@code deg}/{@code grad}/{@code rad}.
	 */
	private static Double radiansArg(UserAgent ua, TokenStream group, int depth) {
		final CssToken token = group.next();
		if (token == null || group.hasNext()) {
			return null;
		}
		if (token instanceof CssToken.Dim dim) {
			Double radians = switch (dim.unit()) {
			case DEG -> Math.toRadians(dim.value());
			case GRAD -> dim.value() * Math.PI / 200.0;
			case RAD -> (double) dim.value();
			default -> "turn".equalsIgnoreCase(dim.unitText()) ? dim.value() * Math.PI * 2.0 : null;
			};
			return radians != null && Double.isFinite(radians) ? radians : null;
		}
		final Quantity q = evaluateLeaf(ua, token, depth);
		if (q == null) {
			return null;
		}
		if (q.kind == Quantity.Kind.NUMBER) {
			return q.number;
		}
		return q.kind == Quantity.Kind.ANGLE ? Math.toRadians(q.number) : null;
	}

	/** Converts only finite values to Quantity. NaN/Infinity means evaluation failure (null). */
	private static Quantity finite(double v) {
		return Double.isFinite(v) ? Quantity.number(v) : null;
	}

	/**
	 * Evaluates calc() content (RPN, already converted by
	 * {@link net.zamasoft.foliojet.css.token.Tokens}) using an explicit stack.
	 * This stage itself is not recursive; only a function-call leaf enters recursion
	 * via {@link #evaluateFunc}, bounded by {@link #MAX_FUNCTION_DEPTH}.
	 */
	private static Quantity evaluateCalcRpn(UserAgent ua, List<CssToken> rpn, int depth) {
		if (rpn.isEmpty()) {
			return null;
		}
		Deque<Quantity> stack = new ArrayDeque<Quantity>();
		for (CssToken token : rpn) {
			if (token instanceof CssToken.Op op) {
				if (stack.size() < 2) {
					return null;
				}
				Quantity b = stack.pop();
				Quantity a = stack.pop();
				Quantity result = applyOp(op, a, b);
				if (result == null) {
					return null;
				}
				stack.push(result);
				continue;
			}
			Quantity leaf = evaluateLeaf(ua, token, depth);
			if (leaf == null) {
				return null;
			}
			stack.push(leaf);
		}
		return stack.size() == 1 ? stack.pop() : null;
	}

	private static Quantity applyOp(CssToken.Op op, Quantity a, Quantity b) {
		switch (op) {
		case PLUS:
			if (a.kind != b.kind) {
				// In CSS, unitless zero can act as the identity on either side
				// (e.g. calc(0 + 10px), calc(10px + 0)). Other type mixtures are invalid.
				if (a.kind == Quantity.Kind.NUMBER && a.number == 0) {
					return b;
				}
				if (b.kind == Quantity.Kind.NUMBER && b.number == 0) {
					return a;
				}
				return null;
			}
			return a.kind == Quantity.Kind.NUMBER ? Quantity.number(a.number + b.number)
					: a.kind == Quantity.Kind.ANGLE ? Quantity.angle(a.number + b.number)
					: Quantity.length(a.absolute + b.absolute, a.ratio + b.ratio,
							Quantity.zip(a.font, b.font, (x, y) -> x + y), Term.sum(a.term, b.term, 1));
		case MINUS:
			if (a.kind != b.kind) {
				if (b.kind == Quantity.Kind.NUMBER && b.number == 0) {
					return a;
				}
				return null;
			}
			return a.kind == Quantity.Kind.NUMBER ? Quantity.number(a.number - b.number)
					: a.kind == Quantity.Kind.ANGLE ? Quantity.angle(a.number - b.number)
					: Quantity.length(a.absolute - b.absolute, a.ratio - b.ratio,
							Quantity.zip(a.font, b.font, (x, y) -> x - y), Term.sum(a.term, b.term, -1));
		case TIMES:
			if (a.kind == Quantity.Kind.NUMBER && b.kind == Quantity.Kind.NUMBER) {
				return Quantity.number(a.number * b.number);
			}
			if (a.kind == Quantity.Kind.NUMBER) {
				if (b.kind == Quantity.Kind.ANGLE) {
					return Quantity.angle(b.number * a.number);
				}
				return Quantity.length(b.absolute * a.number, b.ratio * a.number, b.scaled(a.number),
						Term.scaled(b.term, a.number));
			}
			if (b.kind == Quantity.Kind.NUMBER) {
				if (a.kind == Quantity.Kind.ANGLE) {
					return Quantity.angle(a.number * b.number);
				}
				return Quantity.length(a.absolute * b.number, a.ratio * b.number, a.scaled(b.number),
						Term.scaled(a.term, b.number));
			}
			// Multiplying lengths is also invalid under the CSS specification
			return null;
		case SLASH:
			if (b.kind != Quantity.Kind.NUMBER || b.number == 0) {
				return null;
			}
			return a.kind == Quantity.Kind.NUMBER ? Quantity.number(a.number / b.number)
					: a.kind == Quantity.Kind.ANGLE ? Quantity.angle(a.number / b.number)
					: Quantity.length(a.absolute / b.number, a.ratio / b.number, a.scaled(1 / b.number),
							Term.scaled(a.term, 1 / b.number));
		default:
			return null;
		}
	}

	/** Evaluates a calc() expression-tree leaf (number, dimension, percentage, or nested function call). */
	private static Quantity evaluateLeaf(UserAgent ua, CssToken token, int depth) {
		if (token instanceof CssToken.Num num) {
			return Quantity.number(num.value());
		}
		if (token instanceof CssToken.Percent percent) {
			return Quantity.length(0, percent.value() / 100.0);
		}
		if (token instanceof CssToken.Dim dim) {
			Quantity angle = toAngle(dim);
			if (angle != null) {
				return angle;
			}
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, token);
			if (length == null) {
				// **Carry font-relative units as coefficients** (2026-08-03).
				// Unknown units yield null (evaluation failure) here too.
				return Quantity.font(dim.unit(), dim.value());
			}
			return Quantity.length(length.getLength(), 0);
		}
		if (token instanceof CssToken.Func func) {
			return evaluateFunc(ua, func, depth + 1);
		}
		return null;
	}

	private static Quantity toAngle(CssToken.Dim dim) {
		return switch (dim.unit()) {
		case DEG -> Quantity.angle(dim.value());
		case GRAD -> Quantity.angle(dim.value() * 0.9);
		case RAD -> Quantity.angle(Math.toDegrees(dim.value()));
		default -> "turn".equalsIgnoreCase(dim.unitText()) ? Quantity.angle(dim.value() * 360.0) : null;
		};
	}

	private static Quantity evaluateMinMax(UserAgent ua, CssToken.Func func, int depth, boolean isMin) {
		List<TokenStream> groups = func.argStream().splitComma();
		if (groups.isEmpty()) {
			return null;
		}
		Quantity result = evaluateSingleArg(ua, groups.get(0), depth);
		if (result == null) {
			return null;
		}
		for (int i = 1; i < groups.size(); ++i) {
			Quantity next = evaluateSingleArg(ua, groups.get(i), depth);
			if (next == null) {
				return null;
			}
			result = pick(result, next, isMin);
			if (result == null) {
				return null;
			}
		}
		return result;
	}

	private static Quantity evaluateClamp(UserAgent ua, CssToken.Func func, int depth) {
		List<TokenStream> groups = func.argStream().splitComma();
		if (groups.size() != 3) {
			return null;
		}
		Quantity min = evaluateSingleArg(ua, groups.get(0), depth);
		Quantity val = evaluateSingleArg(ua, groups.get(1), depth);
		Quantity max = evaluateSingleArg(ua, groups.get(2), depth);
		if (min == null || val == null || max == null) {
			return null;
		}
		// As specified: clamp(MIN, VAL, MAX) = max(MIN, min(VAL, MAX))
		Quantity innerMin = pick(val, max, true);
		if (innerMin == null) {
			return null;
		}
		return pick(min, innerMin, false);
	}

	private static Quantity evaluateSingleArg(UserAgent ua, TokenStream group, int depth) {
		CssToken token = group.next();
		if (token == null || group.hasNext()) {
			// Each min()/max()/clamp() argument must be a single value or function call
			return null;
		}
		return evaluateLeaf(ua, token, depth);
	}

	/**
	 * Returns the smaller (isMin=true) or larger (isMin=false) of a and b only if comparable.
	 * Returns null otherwise.
	 */
	private static Quantity pick(Quantity a, Quantity b, boolean isMin) {
		Integer cmp = compare(a, b);
		if (cmp == null) {
			return pickLater(a, b, isMin);
		}
		if (isMin) {
			return cmp <= 0 ? a : b;
		}
		return cmp >= 0 ? a : b;
	}

	/**
	 * Converts min()/max() comparing absolute lengths and font-relative units to a value
	 * selected at the computed-value stage, when font metrics are known (2026-10-04,
	 * publishing report: {@code min(10mm, 3em)} was considered invalid). Cannot evaluate
	 * arguments containing percentages (null), whose reference length is unknown until layout.
	 */
	private static Quantity pickLater(Quantity a, Quantity b, boolean isMin) {
		if (a.kind != Quantity.Kind.LENGTH || b.kind != Quantity.Kind.LENGTH || a.ratio != 0 || b.ratio != 0) {
			return null;
		}
		return Quantity.length(0, 0, CalcFontRelativeValue.newComponents(), Term.extremum(isMin,
				Term.linear(a.absolute, a.font, a.term), Term.linear(b.absolute, b.font, b.term)));
	}

	/**
	 * Returns ordering only when statically comparable (a&lt;b: negative, a&gt;b: positive, equal: 0).
	 * Numbers can only be compared with numbers. Length-percentages can be compared statically,
	 * independently of reference value ref, only for two absolute lengths (both ratio components 0)
	 * or two percentages (both absolute components 0). Other cases (mixed px and %, etc.) have
	 * no known ordering until used-value computation, so are currently unsupported and return null.
	 */
	private static Integer compare(Quantity a, Quantity b) {
		if (a.kind != b.kind) {
			return null;
		}
		if (a.kind == Quantity.Kind.NUMBER || a.kind == Quantity.Kind.ANGLE) {
			return Double.compare(a.number, b.number);
		}
		// Values with remaining font-relative components (em/ex/rem/ch/lh) cannot be ordered
		// until font metrics are known (2026-08-27: previously these components were ignored
		// and only absolute/ratio compared, making max(1em, 1px) yield 1px).
		if (a.hasFont() || b.hasFont()) {
			return null;
		}
		if (a.ratio == 0 && b.ratio == 0) {
			return Double.compare(a.absolute, b.absolute);
		}
		if (a.absolute == 0 && b.absolute == 0) {
			return Double.compare(a.ratio, b.ratio);
		}
		return null;
	}
}
