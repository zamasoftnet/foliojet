package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Determines cuts at text-block line boundaries. A pure function that chooses a cut position satisfying
 * orphans/widows constraints without touching boxes.
 *
 * <p>
 * Evaluates widows/orphans against the virtual line count obtained by dividing the target range's height
 * by line-height. If both constraints cannot be satisfied, moves the entire block to the next page.
 * At the page start (first), however, ignores orphans and leaves at least one line on the preceding page.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LineCutter {
	/**
	 * The cut decision result.
	 */
	public sealed interface Decision {
		/** The cut line is at or below the bottom edge; everything fits on the preceding page. */
		Decision KEEP = new Keep();

		/** Moves everything to the next page. */
		Decision MOVE = new Move();

		record Keep() implements Decision {
		}

		record Move() implements Decision {
		}

		/**
		 * Cuts immediately after line lastLine.
		 *
		 * @param lastLine index of the last line to leave on the preceding page
		 */
		record CutAfter(int lastLine) implements Decision {
		}
	}

	private LineCutter() {
		// utility
	}

	/**
	 * Returns whether fewer than two lines have non-negligible height
	 * (= no cut point exists at a line boundary).
	 *
	 * <p>
	 * In this case, {@link #decide} <b>unconditionally</b> returns {@link Decision#KEEP} at the fragment start.
	 * Line splitting therefore cannot make any progress, and content exceeding capacity is drawn overflowing.
	 * Rescue splitting (2026-07-25) replaces only this point of nonprogress, so it uses the same decision here
	 * to avoid defining the rule twice.
	 * </p>
	 *
	 * @param lineStarts top-edge position of each line
	 * @param lineEnds bottom-edge position of each line
	 * @return true if there is effectively at most one line
	 */
	public static boolean singleEffectiveLine(final double[] lineStarts, final double[] lineEnds) {
		int nonZeroLines = 0;
		for (int i = 0; i < lineStarts.length; ++i) {
			if (lineStarts[i] > 0 || lineEnds[i] - lineStarts[i] > 0) {
				if (++nonZeroLines >= 2) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Determines the cut position.
	 *
	 * @param pageLimit distance from the box's top edge to the cut line
	 * @param pageSize box extent in the page direction
	 * @param lineHeight line height used to calculate the virtual line count
	 * @param orphans minimum virtual line count to leave on the preceding page
	 * @param widows minimum virtual line count to send to the next page
	 * @param first whether the box is at the page start (FLAGS_FIRST)
	 * @param lineStarts top-edge position of each line
	 * @param lineEnds bottom-edge position of each line
	 * @return the cut decision
	 */
	public static Decision decide(final double pageLimit, final double pageSize, final double lineHeight,
			final int orphans, final int widows, final boolean first, final double[] lineStarts,
			final double[] lineEnds) {
		if (LayoutUtils.compare(pageLimit, pageSize) >= 0) {
			// No move if the cut line is at or below the bottom edge.
			return Decision.KEEP;
		}

		if (!singleEffectiveLine(lineStarts, lineEnds)) {
			if (!first && LayoutUtils.compare(pageLimit, lineEnds[0]) < 0) {
				// Move everything if the cut line is above the first line's bottom edge.
				return Decision.MOVE;
			}
		} else {
			// Single-line case
			return first ? Decision.KEEP : Decision.MOVE;
		}

		// Find the last line that can remain on the preceding page.
		int lastOrphan;
		for (lastOrphan = lineEnds.length - 1; lastOrphan > 0; --lastOrphan) {
			if (LayoutUtils.compare(pageLimit, lineEnds[lastOrphan]) >= 0) {
				break;
			}
		}

		// Constraint imposed by 'widows'
		while (lastOrphan >= 0) {
			final double virHeight = pageSize - lineEnds[lastOrphan];
			final int virWidows = (int) Math.round(virHeight / lineHeight);
			if (virWidows >= widows) {
				break;
			}
			--lastOrphan;
		}
		if (lastOrphan == -1) {
			if (!first) {
				return Decision.MOVE;
			}
			lastOrphan = 0;
		}
		if (!first) {
			// Constraint imposed by 'orphans'
			final int virOrphans = (int) Math.round(lineEnds[lastOrphan] / lineHeight);
			if (virOrphans < orphans) {
				return Decision.MOVE;
			}
		}
		return new Decision.CutAfter(lastOrphan);
	}
}
