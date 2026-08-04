package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code row-gap} / {@code column-gap} / {@code gap} の値を読む窓口です。
 *
 * <p>
 * <b>3箇所が同じ形を読む</b>({@code RowGap}・{@code ColumnGap}・
 * {@code GapShorthand})ので、変換をここへ集める。散らばっていたために
 * <b>calc() を受けるようにしたつもりで1箇所だけ直す</b>という間違いが
 * 起きやすい形だった。
 *
 * <p>
 * <b>calc() を必ず通すこと</b>(2026-08-04、実地コーパス第9波)。それまで
 * {@code gap} は素の長さしか読まず、{@code calc()} を書くと宣言ごと捨てて
 * いた。**Tailwind CSS v4 は {@code gap-4} を
 * {@code gap: calc(var(--spacing) * 4)} に展開する**ので、
 * <b>Tailwind v4 のページは間隔が軒並みゼロ</b>になる。同じ {@code calc()}
 * が {@code margin}・{@code padding} では効いていたぶん気づきにくかった。
 * 回帰は {@code files/unittest/0510-flex/gap-calc.html}。
 *
 * @author MIYABE Tatsuhiko
 */
public final class GapValueUtils {

	private GapValueUtils() {
		// ユーティリティ
	}

	/**
	 * 間隔の値({@code <length>} か {@code calc()})を読みます。負の値と
	 * 単位なしの数値は無効(仕様どおり)。読めなければ{@code null}。
	 */
	public static Value toGap(UserAgent ua, CssToken token) {
		final Value calc = CalcValueUtils.toCalc(ua, token);
		if (calc != null) {
			// <length>文脈なので単位なし数値のcalc()結果(例: calc(1 + 2))は無効
			if (calc instanceof net.zamasoft.foliojet.css.value.RealValue) {
				return null;
			}
			if (calc instanceof net.zamasoft.foliojet.css.value.QuantityValue quantity && quantity.isNegative()) {
				return null;
			}
			return calc;
		}
		final Value value = ValueUtils.toLength(ua, token);
		if (value instanceof net.zamasoft.foliojet.css.value.QuantityValue quantity && quantity.isNegative()) {
			return null;
		}
		return value;
	}
}
