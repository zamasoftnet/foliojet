package net.zamasoft.foliojet.layout.rescue;

/**
 * The result of one step of visual rescue splitting
 * (introduced 2026-07-25, increment 1; design consultation §1/§2; not yet wired into production paths).
 *
 * <p>
 * Rescue splitting <b>geometrically</b> cuts an unsplittable box that still does not fit at the page start
 * and sends it to the next fragment. This differs from normal page-break splitting.
 * This type represents one of the following for that step:
 * </p>
 *
 * <ul>
 * <li>{@link None} — no rescue (= defer to existing behavior)</li>
 * <li>{@link Slice} — extract {@code sliceExtent} starting at {@code offset} </li>
 * </ul>
 *
 * <p>
 * Only the pure functions in {@link VisualRescuePlanner} create decisions
 * (no side effects; no boxes are touched).
 * </p>
 */
public sealed interface RescueDecision {

	/**
	 * The reason rescue was not performed. Carries a reason instead of a simple {@code false}
	 * so tests can enforce the intent of each branch.
	 */
	public enum Reason {
		/** Not at the fragment start (= there is still an opportunity to move to the next fragment). */
		NOT_FIRST,
		/** Absolute positioning (excluded by the agreed specification to preserve intentional overflow). */
		ABSOLUTE,
		/** Contains NaN, Infinity, or {@code LayoutUtils.NONE} (unresolved). */
		UNDEFINED_GEOMETRY,
		/** Invalid geometry, such as a negative offset or nonpositive original extent. */
		INVALID_GEOMETRY,
		/** The consumed offset has reached the original extent, leaving no remainder. */
		EXHAUSTED,
		/** Fits the capacity in the first fragment; no rescue needed (normal path). */
		FITS,
		/** Capacity is below {@link VisualRescuePlanner#MIN_RESCUE_ADVANCE} (cannot guarantee progress). */
		INSUFFICIENT_CAPACITY,
		/**
		 * Capacity satisfies the progress guarantee but is too small to be useful
		 * (below {@link VisualRescuePlanner#minUsefulSlice(double)} ).
		 * Rejects to avoid a succession of tiny-fragment pages that are effectively blank,
		 * and falls back to the existing terminal behavior (draw with overflow).
		 */
		SLIVER_CAPACITY,
		/**
		 * Capacity is sufficient, but <b>the amount of overflow</b> is too small to be useful
		 * (below {@link VisualRescuePlanner#MIN_RESCUE_SLICE} ).
		 * Adding a whole page to rescue a few pt of overflow would make that page effectively blank.
		 * This guards the tail against violating the absolute requirement to avoid unintended blank pages.
		 * Falls back to the existing terminal behavior (draw with overflow).
		 */
		SLIVER_REMAINDER,
		/** Rounding in addition prevents {@code offset} from strictly increasing (e.g. extremely large doubles). */
		NO_PROGRESS;
	}

	/**
	 * No rescue. The caller falls back to the existing terminal behavior:
	 * draw with overflow at the page start, or delegate to the next fragment otherwise.
	 *
	 * @param reason reason rescue was not performed
	 */
	public record None(Reason reason) implements RescueDecision {
		public None {
			if (reason == null) {
				throw new IllegalArgumentException("reason");
			}
		}
	}

	/**
	 * Extracts the interval {@code [offset, nextOffset)} as one fragment (an interval-split value type).
	 * Layout dimensions do not change; only the occupied page extent becomes {@code sliceExtent} .
	 *
	 * <p>
	 * Invariants (checked by the constructor):
	 * </p>
	 * <ul>
	 * <li>{@code offset >= 0}</li>
	 * <li>{@code sliceExtent > 0}</li>
	 * <li>{@code nextOffset > offset} (progress guarantee; equality would cause an infinite loop)</li>
	 * <li>{@code firstFragment == (offset == 0)}</li>
	 * </ul>
	 *
	 * @param offset page-direction position where this fragment starts (original box coordinates)
	 * @param sliceExtent page-direction extent occupied by this slice on the fragment
	 * @param nextOffset position where the next fragment starts ({@code offset + sliceExtent})
	 * @param firstFragment whether this is the first fragment (with top margin and top border)
	 * @param lastFragment whether this is the final fragment (with bottom border and bottom margin, no tail)
	 */
	public record Slice(
			double offset,
			double sliceExtent,
			double nextOffset,
			boolean firstFragment,
			boolean lastFragment) implements RescueDecision {

		public Slice {
			if (!(offset >= 0)) {
				throw new IllegalArgumentException("offset=" + offset);
			}
			if (!(sliceExtent > 0)) {
				throw new IllegalArgumentException("sliceExtent=" + sliceExtent);
			}
			if (!(nextOffset > offset)) {
				// Progress guarantee: violating this prevents the page-break loop from terminating.
				throw new IllegalArgumentException("前進しない: offset=" + offset + " nextOffset=" + nextOffset);
			}
			if (firstFragment != (offset == 0)) {
				throw new IllegalArgumentException("firstFragment=" + firstFragment + " offset=" + offset);
			}
		}

		/** Returns true for a continuation fragment (= the part emitted as a PDF artifact). */
		public boolean isContinuation() {
			return !this.firstFragment;
		}

		/** Returns true if a remainder fragment (tail) must be created after this fragment. */
		public boolean hasTail() {
			return !this.lastFragment;
		}
	}
}
