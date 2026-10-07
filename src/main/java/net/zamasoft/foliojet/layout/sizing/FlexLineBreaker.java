package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.List;

/**
 * A pure calculation of Flex line breaks (css-flexbox-1 §9.3 step 5; Flex F2a,
 * 2026-08-02: consult-codex-2026-08-02-flexbox.txt). Collects items into lines using outer
 * hypothetical main sizes and main gaps, breaking when the next item would exceed the container's
 * inner main size. Each line contains at least one item (an item that overflows alone gets its own line).
 *
 * <p>
 * Breaks only when the size exceeds the limit (&gt;); an exact fit (==) stays on the same line.
 * Near floating-point equality, {@code EPSILON} favors "exact fit"
 * (the consultation's validation condition: floating-point error must not break an exact-fit line).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlexLineBreaker {

	/** Tolerance for exact-fit detection (pt). */
	private static final double EPSILON = 1e-6;

	private FlexLineBreaker() {
	}

	/** The range of one line (from inclusive, to exclusive; source order). */
	public record Line(int from, int to) {
		public int count() {
			return this.to - this.from;
		}
	}

	/**
	 * @param items Item measurements in source order
	 * @param innerMainSize Container's inner main size
	 * @param mainGap Gap between items
	 * @return List of lines (empty for empty input)
	 */
	public static List<Line> breakLines(final List<FlexItemMetrics> items, final double innerMainSize,
			final double mainGap) {
		final List<Line> lines = new ArrayList<>();
		int from = 0;
		double used = 0;
		for (int i = 0; i < items.size(); ++i) {
			final double outer = items.get(i).outerHypotheticalMain();
			final double candidate = (i > from ? used + mainGap : 0) + outer;
			if (i > from && candidate > innerMainSize + EPSILON) {
				lines.add(new Line(from, i));
				from = i;
				used = outer;
			} else {
				used = candidate;
			}
		}
		if (from < items.size()) {
			lines.add(new Line(from, items.size()));
		}
		return lines;
	}
}
