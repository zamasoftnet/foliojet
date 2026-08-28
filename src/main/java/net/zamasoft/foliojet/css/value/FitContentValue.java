package net.zamasoft.foliojet.css.value;

/**
 * {@code fit-content(<length-percentage>)}(css-sizing-3 §4.4、2026-08-29)です。
 *
 * <p>
 * 引数は行方向の利用可能寸法の代わりになる上限で、使用値は
 * {@code min(max-content, max(min-content, 引数))}。引数無しの
 * {@code fit-content}キーワードは{@link KeywordValue#FIT_CONTENT}で、
 * 利用可能寸法を上限にする(浮動体のshrink-to-fitと同じ)。
 * </p>
 *
 * <p>
 * 引数は解析時には{@code em}等のフォント相対長さのまま持ち、計算値の
 * 段階で{@code ValueUtils.emExToAbsoluteLength}が絶対長さへ解く
 * ({@code calc()}のフォント相対成分と同じ運び方)。
 * </p>
 *
 * @param argument 上限。{@code AbsoluteLengthValue}/{@code PercentageValue}/
 *                 {@code CalcLengthValue}(解析直後はフォント相対長さも可)
 */
public record FitContentValue(Value argument) implements Value {
	public String toString() {
		return "fit-content(" + this.argument + ")";
	}
}
