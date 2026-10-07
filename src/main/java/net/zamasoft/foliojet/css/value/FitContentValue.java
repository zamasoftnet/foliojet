package net.zamasoft.foliojet.css.value;

/**
 * {@code fit-content(<length-percentage>)} (css-sizing-3 §4.4, 2026-08-29).
 *
 * <p>
 * The argument is a limit that replaces the available inline size; the used value is
 * {@code min(max-content, max(min-content, argument))}. The argument-free
 * {@code fit-content} keyword is {@link KeywordValue#FIT_CONTENT}, which uses the available
 * size as its limit (the same as shrink-to-fit for floats).
 * </p>
 *
 * <p>
 * During parsing, the argument retains font-relative lengths such as {@code em}.
 * At the computed-value stage, {@code ValueUtils.emExToAbsoluteLength} resolves them
 * to absolute lengths (the same handling as font-relative components of {@code calc()}).
 * </p>
 *
 * @param argument the limit: {@code AbsoluteLengthValue}/{@code PercentageValue}/
 *                 {@code CalcLengthValue} (font-relative lengths are also allowed immediately after parsing)
 */
public record FitContentValue(Value argument) implements Value {
	public String toString() {
		return "fit-content(" + this.argument + ")";
	}
}
