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
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * calc()・min()・max()・clamp()とCSS Values 4の数学関数を評価します。
 * <p>
 * 対応する被演算子は {@code <number>}・絶対単位の{@code <length>}(px/pt/in/cm/mm/Q/pc)・
 * {@code <percentage>}です。絶対長さと割合が混在する結果(例:
 * {@code calc(50% + 10px)})は{@link CalcLengthValue}として返し、実際の解決は
 * レイアウト時({@link net.zamasoft.foliojet.layout.box.params.LengthType#MIXED}経由)
 * に行います。
 * </p>
 * <p>
 * <b>現時点で非対応(評価失敗としてnullを返す)</b>: em/ex/rem/ch等のフォント相対単位
 * (CSSStyleが定まるまで解決できないため。用途に応じ将来
 * {@link net.zamasoft.foliojet.css.value.RelativeLengthValue}と同様の
 * 「getComputedValue時に解決する」経路を追加する余地がある)、var()(カスケード時
 * 解決が必要な別アーキテクチャのため別途対応)、絶対長さと割合が静的に比較できない
 * min()/max()/clamp()(例: {@code min(10px, 50%)}。基準値が定まる使用値計算時まで
 * 大小が確定しないため)。
 * </p>
 */
public final class CalcValueUtils {
	private CalcValueUtils() {
		// utility
	}

	/**
	 * 関数呼び出し(calc()等)のネスト深さの上限。calc(min(calc(...)))のような
	 * 関数境界をまたぐネストでのみ増える(1つのcalc()内の項数では増えない)。
	 * この深さはCSS作者が実際に書いた構文の入れ子段数そのものであり、
	 * HTML文書のような外部データ由来の非有界な深さとは性質が異なるため
	 * (無限再帰の心配はなく、上限は純粋な安全弁)、ここに限り再帰呼び出しを
	 * 使う。ただし上限を明示することでStackOverflowErrorは構造的に起こり得ない。
	 */
	private static final int MAX_FUNCTION_DEPTH = 32;

	/**
	 * トークンがcalc()/min()/max()/clamp()関数呼び出しであれば評価し、その結果の
	 * Value(RealValue/AngleValue/AbsoluteLengthValue/PercentageValue/CalcLengthValueの
	 * いずれか)を返します。それ以外のトークン、または評価に失敗した場合はnullを
	 * 返します(呼び出し側は「解釈できないトークン」として通常のフォールバック
	 * 処理を続行できる)。
	 */
	public static Value toCalc(UserAgent ua, CssToken token) {
		if (!(token instanceof CssToken.Func func)) {
			return null;
		}
		Quantity result = evaluateFunc(ua, func, 0);
		return result != null ? result.toValue(ua) : null;
	}

	/**
	 * 内部評価通貨: {@code <number>}か{@code <length-percentage>}のいずれか。
	 *
	 * <p>
	 * <b>フォント相対単位(em/ex/rem/ch)は解析時には解けない</b>ので、単位ごとの
	 * 係数として別に持つ(2026-08-03)。加減は成分ごと、数との乗除は全成分に効く
	 * ——どちらもフォント寸法に対して線形なので、後で寸法を掛けても等価である。
	 */
	private static final class Quantity {
		private enum Kind {
			NUMBER, LENGTH, ANGLE
		}

		final Kind kind;
		final double number;
		final double absolute;
		final double ratio;
		/** フォント相対成分。{@link CalcFontRelativeValue#UNITS}と同じ並び。 */
		final double[] font;

		static Quantity number(double v) {
			return Double.isFinite(v) ? new Quantity(Kind.NUMBER, v, 0, 0, CalcFontRelativeValue.newComponents())
					: null;
		}

		static Quantity angle(double degrees) {
			return Double.isFinite(degrees)
					? new Quantity(Kind.ANGLE, degrees, 0, 0, CalcFontRelativeValue.newComponents())
					: null;
		}

		static Quantity length(double absolute, double ratio) {
			return length(absolute, ratio, CalcFontRelativeValue.newComponents());
		}

		static Quantity length(double absolute, double ratio, double[] font) {
			if (!Double.isFinite(absolute) || !Double.isFinite(ratio)) {
				return null;
			}
			for (final double v : font) {
				if (!Double.isFinite(v)) {
					return null;
				}
			}
			return new Quantity(Kind.LENGTH, 0, absolute, ratio, font);
		}

		/** フォント相対単位1つ分。 */
		static Quantity font(Unit unit, double v) {
			final int i = CalcFontRelativeValue.indexOf(unit);
			if (i < 0) {
				return null;
			}
			final double[] font = CalcFontRelativeValue.newComponents();
			font[i] = v;
			return length(0, 0, font);
		}

		private Quantity(Kind kind, double number, double absolute, double ratio, double[] font) {
			this.kind = kind;
			this.number = number;
			this.absolute = absolute;
			this.ratio = ratio;
			this.font = font;
		}

		boolean hasFont() {
			for (final double v : this.font) {
				if (v != 0) {
					return true;
				}
			}
			return false;
		}

		/** 成分ごとに二項演算した配列を返します。 */
		static double[] zip(double[] a, double[] b, java.util.function.DoubleBinaryOperator op) {
			final double[] result = new double[a.length];
			for (int i = 0; i < a.length; ++i) {
				result[i] = op.applyAsDouble(a[i], b[i]);
			}
			return result;
		}

		/** 全成分を定数倍(除算はfactorに逆数を渡す)した配列を返します。 */
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
				// フォント寸法が定まる計算値の段階で解く
				return CalcFontRelativeValue.create(this.absolute, this.ratio, this.font);
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
	 * 数値を返す1引数の数学関数(css-values-4)です。
	 *
	 * <p>
	 * {@code sqrt()} {@code exp()} は{@code <number>}を取って{@code <number>}を返し、
	 * {@code sin()} {@code cos()} {@code tan()} は{@code <angle>}または
	 * {@code <number>}(ラジアン)を取って{@code <number>}を返します。
	 * 定義域外(例: {@code sqrt(-1)})やオーバーフローは、既存の評価失敗と同じく
	 * {@code null}(＝不正値)にします——NaN/Infinityを版面の寸法へ流さないため。
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

	/** 数値を返す2引数の数学関数({@code pow()})です。 */
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

	/** 逆三角関数で、結果はdegの{@code <angle>}として保持する。 */
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

	/** {@code log(A)}(自然対数)と{@code log(A, B)}(底B)です。 */
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
	 * {@code hypot()}です。引数は{@code <number>}のみ受け付けます
	 * (仕様は同じ型の長さ等も取れますが、Quantityの各成分ごとの二乗和は
	 * 型変換が必要になるため、数値に限っています)。
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

	/** 引数を{@code <number>}として評価します。数値でなければnull。 */
	private static Double numberArg(UserAgent ua, TokenStream group, int depth) {
		final Quantity q = evaluateSingleArg(ua, group, depth);
		if (q == null || q.kind != Quantity.Kind.NUMBER) {
			return null;
		}
		return q.number;
	}

	/**
	 * 三角関数の引数をラジアンとして評価します。{@code <number>}はそのまま
	 * ラジアン、{@code deg}/{@code grad}/{@code rad}は換算します。
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

	/** 有限値だけをQuantityにします。NaN/Infinityは評価失敗(null)。 */
	private static Quantity finite(double v) {
		return Double.isFinite(v) ? Quantity.number(v) : null;
	}

	/**
	 * calc()の中身(逆ポーランド記法、{@link net.zamasoft.foliojet.css.token.Tokens}が
	 * 変換済み)を明示的スタックで評価します(この段自体には再帰を使わない。
	 * 関数呼び出しの葉に当たった場合のみ{@link #evaluateFunc}を介して
	 * {@link #MAX_FUNCTION_DEPTH}で上限を切った再帰に入る)。
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
				// CSSでは単位なしの0はどちらの側でも中立元として扱ってよい
				// (例: calc(0 + 10px)・calc(10px + 0))。それ以外の型混在は無効。
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
							Quantity.zip(a.font, b.font, (x, y) -> x + y));
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
							Quantity.zip(a.font, b.font, (x, y) -> x - y));
		case TIMES:
			if (a.kind == Quantity.Kind.NUMBER && b.kind == Quantity.Kind.NUMBER) {
				return Quantity.number(a.number * b.number);
			}
			if (a.kind == Quantity.Kind.NUMBER) {
				if (b.kind == Quantity.Kind.ANGLE) {
					return Quantity.angle(b.number * a.number);
				}
				return Quantity.length(b.absolute * a.number, b.ratio * a.number, b.scaled(a.number));
			}
			if (b.kind == Quantity.Kind.NUMBER) {
				if (a.kind == Quantity.Kind.ANGLE) {
					return Quantity.angle(a.number * b.number);
				}
				return Quantity.length(a.absolute * b.number, a.ratio * b.number, a.scaled(b.number));
			}
			// length同士の掛け算はCSS仕様上も無効
			return null;
		case SLASH:
			if (b.kind != Quantity.Kind.NUMBER || b.number == 0) {
				return null;
			}
			return a.kind == Quantity.Kind.NUMBER ? Quantity.number(a.number / b.number)
					: a.kind == Quantity.Kind.ANGLE ? Quantity.angle(a.number / b.number)
					: Quantity.length(a.absolute / b.number, a.ratio / b.number, a.scaled(1 / b.number));
		default:
			return null;
		}
	}

	/** calc()数式木の葉(数値・寸法・パーセント・入れ子の関数呼び出し)を評価する。 */
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
				// **フォント相対単位は係数として持ち越す**(2026-08-03)。
				// 未知の単位はここでもnull(評価失敗)
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
		// 仕様どおり clamp(MIN, VAL, MAX) = max(MIN, min(VAL, MAX))
		Quantity innerMin = pick(val, max, true);
		if (innerMin == null) {
			return null;
		}
		return pick(min, innerMin, false);
	}

	private static Quantity evaluateSingleArg(UserAgent ua, TokenStream group, int depth) {
		CssToken token = group.next();
		if (token == null || group.hasNext()) {
			// min()/max()/clamp()の各引数は単一の値または関数呼び出しでなければならない
			return null;
		}
		return evaluateLeaf(ua, token, depth);
	}

	/**
	 * aとbを比較できる場合のみ小さい方(isMin=true)/大きい方(isMin=false)を返します。
	 * 比較できない場合はnull。
	 */
	private static Quantity pick(Quantity a, Quantity b, boolean isMin) {
		Integer cmp = compare(a, b);
		if (cmp == null) {
			return null;
		}
		if (isMin) {
			return cmp <= 0 ? a : b;
		}
		return cmp >= 0 ? a : b;
	}

	/**
	 * 静的に比較できる場合のみ大小関係を返します(a&lt;b:負、a&gt;b:正、等しい:0)。
	 * numberはnumber同士のみ、length-percentageは「絶対長さ同士(割合成分が
	 * いずれも0)」または「割合同士(絶対成分がいずれも0)」の場合のみ、基準値refに
	 * 依存せず静的に比較できる。それ以外(pxと%の混在等)は使用値計算時まで
	 * 大小が確定しないため、現時点では非対応としてnullを返す。
	 */
	private static Integer compare(Quantity a, Quantity b) {
		if (a.kind != b.kind) {
			return null;
		}
		if (a.kind == Quantity.Kind.NUMBER || a.kind == Quantity.Kind.ANGLE) {
			return Double.compare(a.number, b.number);
		}
		// フォント相対成分(em/ex/rem/ch/lh)が残っている値は、フォント寸法が
		// 定まるまで大小が確定しない(2026-08-27。従来は成分を無視して
		// absolute/ratioだけで比較しており、max(1em, 1px)が1pxになっていた)
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
