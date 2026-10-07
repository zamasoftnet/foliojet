package net.zamasoft.foliojet.layout.sizing;

/**
 * Intrinsic sizes (sizes derived from content).
 * <ul>
 * <li>minContent — Min-content size (line-axis size of the longest unbreakable run)</li>
 * <li>maxContent — Max-content size (line-axis size when laid out without wrapping)</li>
 * <li>minPage — Minimum page-axis size</li>
 * <li>columnInflated — Whether {@code minContent} includes <b>multiplication by the column count</b></li>
 * </ul>
 *
 * <p>
 * <b>Why {@code columnInflated} is needed</b> (2026-07-28).
 * The min-content size of multi-column layout is "column count × content's min-content size + gaps";
 * nesting multiplies these factors. Using this as the {@code fit-content} lower bound can
 * <b>exceed the paper's line-axis size without limit</b>: four columns alone multiply it by four.
 * But <b>columns can be narrowed</b> (simply divide the line-axis space by the column count again),
 * so this lower bound need not be honored.
 * </p>
 *
 * <p>
 * In contrast, min-content sizes from <b>indivisible boxes explicitly specified by the author</b>,
 * such as an image with {@code height:150mm}, should be honored: shrinking to fit the paper
 * only makes their content overflow further. The values alone cannot distinguish these cases,
 * so <b>measurement records and carries whether multiplication by the column count took effect</b>.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public record IntrinsicSizes(double minContent, double maxContent, double minPage, boolean columnInflated) {
	public static final IntrinsicSizes ZERO = new IntrinsicSizes(0, 0, 0);

	/** Intrinsic sizes without multiplication by the column count ({@code columnInflated = false}). */
	public IntrinsicSizes(final double minContent, final double maxContent, final double minPage) {
		this(minContent, maxContent, minPage, false);
	}
}
