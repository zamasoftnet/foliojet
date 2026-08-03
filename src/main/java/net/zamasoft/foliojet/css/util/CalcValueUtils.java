package net.zamasoft.foliojet.css.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * calc()・min()・max()・clamp() を評価します。
 * <p>
 * 対応する被演算子は {@code <number>}・絶対単位の{@code <length>}(px/pt/in/cm/mm/pc)・
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
	 * Value(RealValue/AbsoluteLengthValue/PercentageValue/CalcLengthValueの
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
		final boolean isNumber;
		final double number;
		final double absolute;
		final double ratio;
		final double em, ex, rem, ch;

		static Quantity number(double v) {
			return new Quantity(true, v, 0, 0, 0, 0, 0, 0);
		}

		static Quantity length(double absolute, double ratio) {
			return new Quantity(false, 0, absolute, ratio, 0, 0, 0, 0);
		}

		static Quantity length(double absolute, double ratio, double em, double ex, double rem, double ch) {
			return new Quantity(false, 0, absolute, ratio, em, ex, rem, ch);
		}

		/** フォント相対単位1つ分。 */
		static Quantity font(Unit unit, double v) {
			switch (unit) {
			case EM:
				return length(0, 0, v, 0, 0, 0);
			case EX:
				return length(0, 0, 0, v, 0, 0);
			case REM:
				return length(0, 0, 0, 0, v, 0);
			case CH:
				return length(0, 0, 0, 0, 0, v);
			default:
				return null;
			}
		}

		private Quantity(boolean isNumber, double number, double absolute, double ratio, double em, double ex,
				double rem, double ch) {
			this.isNumber = isNumber;
			this.number = number;
			this.absolute = absolute;
			this.ratio = ratio;
			this.em = em;
			this.ex = ex;
			this.rem = rem;
			this.ch = ch;
		}

		boolean hasFont() {
			return this.em != 0 || this.ex != 0 || this.rem != 0 || this.ch != 0;
		}

		Value toValue(UserAgent ua) {
			if (this.isNumber) {
				return RealValue.create(this.number);
			}
			if (this.hasFont()) {
				// フォント寸法が定まる計算値の段階で解く
				return CalcFontRelativeValue.create(this.absolute, this.ratio, this.em, this.ex, this.rem, this.ch);
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
		default:
			return null;
		}
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
			if (a.isNumber != b.isNumber) {
				// CSSでは単位なしの0はどちらの側でも中立元として扱ってよい
				// (例: calc(0 + 10px)・calc(10px + 0))。それ以外の型混在は無効。
				if (a.isNumber && a.number == 0) {
					return b;
				}
				if (b.isNumber && b.number == 0) {
					return a;
				}
				return null;
			}
			return a.isNumber ? Quantity.number(a.number + b.number)
					: Quantity.length(a.absolute + b.absolute, a.ratio + b.ratio, a.em + b.em, a.ex + b.ex,
							a.rem + b.rem, a.ch + b.ch);
		case MINUS:
			if (a.isNumber != b.isNumber) {
				if (b.isNumber && b.number == 0) {
					return a;
				}
				return null;
			}
			return a.isNumber ? Quantity.number(a.number - b.number)
					: Quantity.length(a.absolute - b.absolute, a.ratio - b.ratio, a.em - b.em, a.ex - b.ex,
							a.rem - b.rem, a.ch - b.ch);
		case TIMES:
			if (a.isNumber && b.isNumber) {
				return Quantity.number(a.number * b.number);
			}
			if (a.isNumber) {
				return Quantity.length(b.absolute * a.number, b.ratio * a.number, b.em * a.number, b.ex * a.number,
						b.rem * a.number, b.ch * a.number);
			}
			if (b.isNumber) {
				return Quantity.length(a.absolute * b.number, a.ratio * b.number, a.em * b.number, a.ex * b.number,
						a.rem * b.number, a.ch * b.number);
			}
			// length同士の掛け算はCSS仕様上も無効
			return null;
		case SLASH:
			if (!b.isNumber || b.number == 0) {
				return null;
			}
			return a.isNumber ? Quantity.number(a.number / b.number)
					: Quantity.length(a.absolute / b.number, a.ratio / b.number, a.em / b.number, a.ex / b.number,
							a.rem / b.number, a.ch / b.number);
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
		if (a.isNumber != b.isNumber) {
			return null;
		}
		if (a.isNumber) {
			return Double.compare(a.number, b.number);
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
