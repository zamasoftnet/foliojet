package net.zamasoft.foliojet.layout.rescue;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Unit tests for visual rescue split decisions and the <b>forward-progress guarantee</b>
 * (added 2026-07-25, increment 1).
 *
 * <p>
 * The absolute requirements for this feature are no crashes, infinite loops, or leaks.
 * Of these, the absence of infinite loops is guaranteed solely by properties of the pure decision
 * logic in {@link VisualRescuePlanner}, rather than a retry counter. These tests lock down those
 * properties directly:
 * </p>
 *
 * <ul>
 * <li>A fragment that creates a tail always consumes at least
 * {@link VisualRescuePlanner#MIN_RESCUE_ADVANCE}.</li>
 * <li>If NaN, Infinity, zero capacity, capacity below 1 pt, negative values, or rounding of extremely
 * large doubles prevents {@code offset} from increasing strictly, always return "no rescue."</li>
 * <li>Repeated decisions strictly increase {@code offset} and terminate after finitely many steps.</li>
 * </ul>
 */
public class VisualRescuePlannerTest extends TestCase {

	private static RescueDecision.Reason reasonOf(final RescueDecision decision) {
		assertTrue("救済しない判定のはず: " + decision, decision instanceof RescueDecision.None);
		return ((RescueDecision.None) decision).reason();
	}

	private static RescueDecision.Slice sliceOf(final RescueDecision decision) {
		assertTrue("断片を作る判定のはず: " + decision, decision instanceof RescueDecision.Slice);
		return (RescueDecision.Slice) decision;
	}

	// ------------------------------------------------------------------
	// Constants.
	// ------------------------------------------------------------------

	/** The minimum advance is equivalent to 1 pt (= 2 * LayoutUtils.THRESHOLD). */
	public void testMinRescueAdvanceIsTwiceThreshold() {
		assertEquals(2 * LayoutUtils.THRESHOLD, VisualRescuePlanner.MIN_RESCUE_ADVANCE, 0);
		assertEquals(1.0, VisualRescuePlanner.MIN_RESCUE_ADVANCE, 0);
	}

	// ------------------------------------------------------------------
	// Basic splitting.
	// ------------------------------------------------------------------

	/** If it does not fit at the start, cut a full-capacity slice and continue with a tail. */
	public void testHeadSliceTakesAvailableAndLeavesTail() {
		final RescueDecision.Slice slice = sliceOf(VisualRescuePlanner.plan(true, 100, 250, 0));
		assertEquals(0.0, slice.offset(), 0);
		assertEquals(100.0, slice.sliceExtent(), 0);
		assertEquals(100.0, slice.nextOffset(), 0);
		assertTrue(slice.firstFragment());
		assertFalse(slice.lastFragment());
		assertTrue(slice.hasTail());
		assertFalse(slice.isContinuation());
	}

	/** If the remainder fits within capacity, it becomes the last fragment and creates no tail. */
	public void testFinalSliceTakesTheRemainder() {
		final RescueDecision.Slice slice = sliceOf(VisualRescuePlanner.plan(true, 100, 250, 200));
		assertEquals(200.0, slice.offset(), 0);
		assertEquals(50.0, slice.sliceExtent(), 0);
		assertEquals(250.0, slice.nextOffset(), 0);
		assertFalse(slice.firstFragment());
		assertTrue(slice.lastFragment());
		assertFalse(slice.hasTail());
		assertTrue(slice.isContinuation());
	}

	/** If it fits at the start to begin with, do not rescue (normal path). */
	public void testFittingContentIsNotRescued() {
		assertEquals(RescueDecision.Reason.FITS, reasonOf(VisualRescuePlanner.plan(true, 300, 250, 0)));
		// Exactly at the threshold also counts as fitting.
		assertEquals(RescueDecision.Reason.FITS, reasonOf(VisualRescuePlanner.plan(true, 250, 250, 0)));
	}

	/** Do not rescue away from the fragment start (sending it to the next fragment is still an option). */
	public void testNotAtFragmentStartIsNotRescued() {
		assertEquals(RescueDecision.Reason.NOT_FIRST, reasonOf(VisualRescuePlanner.plan(false, 100, 250, 0)));
		assertEquals(RescueDecision.Reason.NOT_FIRST, reasonOf(VisualRescuePlanner.plan(false, 100, 250, 100)));
	}

	// ------------------------------------------------------------------
	// Excluding absolute positioning.
	// ------------------------------------------------------------------

	/** Explicitly reject absolute positioning (agreed specification; preserve intentional overflow). */
	public void testAbsolutePositioningIsRejected() {
		assertFalse(VisualRescuePlanner.isRescuablePos(PosType.ABSOLUTE));
		assertEquals(RescueDecision.Reason.ABSOLUTE,
				reasonOf(VisualRescuePlanner.plan(PosType.ABSOLUTE, true, 100, 250, 0)));
	}

	/** Anything other than absolute positioning can be eligible. */
	public void testOtherPositioningIsRescuable() {
		for (final PosType posType : PosType.values()) {
			if (posType == PosType.ABSOLUTE) {
				continue;
			}
			assertTrue(posType.name(), VisualRescuePlanner.isRescuablePos(posType));
			assertTrue(posType.name(),
					VisualRescuePlanner.plan(posType, true, 100, 250, 0) instanceof RescueDecision.Slice);
		}
	}

	/** Do not exclude {@code null} (unknown positioning). */
	public void testNullPosTypeIsRescuable() {
		assertTrue(VisualRescuePlanner.isRescuablePos(null));
	}

	// ------------------------------------------------------------------
	// Forward-progress guarantee: all pathological inputs yield "no rescue."
	// ------------------------------------------------------------------

	/** Do not rescue if any input is NaN. */
	public void testNaNIsNotRescued() {
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, Double.NaN, 250, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, Double.NaN, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, 250, Double.NaN)));
	}

	/** Do not rescue if any input is Infinity. */
	public void testInfinityIsNotRescued() {
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, Double.POSITIVE_INFINITY, 250, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, Double.NEGATIVE_INFINITY, 250, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, Double.POSITIVE_INFINITY, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, 250, Double.POSITIVE_INFINITY)));
	}

	/** Do not handle unresolved values ({@code LayoutUtils.NONE}), even though they are numerically finite. */
	public void testUndefinedMagicValueIsNotRescued() {
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, LayoutUtils.NONE, 250, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, LayoutUtils.NONE, 0)));
	}

	/** Do not rescue at zero capacity (cannot advance 1 pt, so cannot satisfy the forward-progress guarantee). */
	public void testZeroCapacityIsNotRescued() {
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.plan(true, 0, 250, 0)));
	}

	/** Do not rescue with capacity below 1 pt (delegate to the outer fragmentainer). */
	public void testCapacityBelowMinimumAdvanceIsNotRescued() {
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.plan(true, 0.9, 250, 0)));
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.plan(true, VisualRescuePlanner.MIN_RESCUE_ADVANCE - 1e-9, 250, 0)));
		// Rescue at exactly the minimum.
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.plan(true, VisualRescuePlanner.MIN_RESCUE_ADVANCE, 250, 0));
		assertEquals(VisualRescuePlanner.MIN_RESCUE_ADVANCE, slice.sliceExtent(), 0);
	}

	/** Do not rescue with negative capacity. */
	public void testNegativeCapacityIsNotRescued() {
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.plan(true, -100, 250, 0)));
	}

	/** Do not rescue with a negative offset or nonpositive source size. */
	public void testInvalidGeometryIsNotRescued() {
		assertEquals(RescueDecision.Reason.INVALID_GEOMETRY, reasonOf(VisualRescuePlanner.plan(true, 100, 250, -1)));
		assertEquals(RescueDecision.Reason.INVALID_GEOMETRY, reasonOf(VisualRescuePlanner.plan(true, 100, 0, 0)));
		assertEquals(RescueDecision.Reason.INVALID_GEOMETRY, reasonOf(VisualRescuePlanner.plan(true, 100, -250, 0)));
	}

	/** Do not rescue if everything is already consumed. */
	public void testExhaustedSourceIsNotRescued() {
		assertEquals(RescueDecision.Reason.EXHAUSTED, reasonOf(VisualRescuePlanner.plan(true, 100, 250, 250)));
		assertEquals(RescueDecision.Reason.EXHAUSTED, reasonOf(VisualRescuePlanner.plan(true, 100, 250, 300)));
	}

	/**
	 * Extremely large doubles can produce {@code offset + chunk == offset}.
	 * Never rescue if rounding prevents progress.
	 */
	public void testHugeDoubleThatCannotAdvanceIsNotRescued() {
		// Adding 100 to 1e300 does not change the value (double-precision spacing is orders of magnitude larger).
		final double offset = 1e300;
		assertEquals(offset, offset + 100.0, 0);
		final double sourcePageExtent = 1e301;
		assertEquals(RescueDecision.Reason.NO_PROGRESS,
				reasonOf(VisualRescuePlanner.plan(true, 100, sourcePageExtent, offset)));
	}

	/**
	 * Do not rescue any combination that stalls due to rounding of extremely large doubles
	 * (the reason for stopping does not matter; the requirement is to create no fragment).
	 */
	public void testHugeDoubleCombinationsNeverProduceAStalledSlice() {
		final double[] offsets = { 1e290, 1e300, Double.MAX_VALUE / 4, Double.MAX_VALUE / 2 };
		final double[] availables = { 1, 100, 1e6, 1e200 };
		for (final double offset : offsets) {
			for (final double available : availables) {
				for (final double extent : new double[] { offset * 2, offset * 10, Double.MAX_VALUE / 2 }) {
					final RescueDecision decision = VisualRescuePlanner.plan(true, available, extent, offset);
					if (decision instanceof RescueDecision.Slice slice) {
						assertTrue("offset=" + offset + " available=" + available + " extent=" + extent,
								slice.nextOffset() > slice.offset());
					}
				}
			}
		}
	}

	/**
	 * Even for a final fragment that consumes the remainder as-is, create no fragment if rounding
	 * prevents progress (the same guard as the value-type invariant).
	 */
	public void testFinalSliceGuardRejectsNonAdvancingInterval() {
		// An interval where adding remaining to offset leaves the value unchanged cannot form a Slice.
		final double offset = 1e300;
		try {
			new RescueDecision.Slice(offset, 100, offset + 100, false, true);
			fail("前進しない最終断片は作れないはず");
		} catch (final IllegalArgumentException expected) {
			// As expected.
		}
	}

	/** Whenever a fragment is created, {@code offset} has strictly increased. */
	public void testEverySliceStrictlyAdvancesTheOffset() {
		final double[] availables = { 1, 1.5, 7, 100, 1e6, 1e300 };
		final double[] extents = { 1.0001, 2, 250, 1e7, 1e301 };
		final double[] offsets = { 0, 0.5, 1, 123, 1e6, 1e299 };
		for (final double available : availables) {
			for (final double extent : extents) {
				for (final double offset : offsets) {
					final RescueDecision decision = VisualRescuePlanner.plan(true, available, extent, offset);
					if (decision instanceof RescueDecision.Slice slice) {
						final String at = "available=" + available + " extent=" + extent + " offset=" + offset;
						assertTrue(at, slice.nextOffset() > slice.offset());
						assertEquals(at, offset, slice.offset(), 0);
						assertTrue(at, slice.sliceExtent() > 0);
						if (slice.hasTail()) {
							// A fragment that creates a tail always consumes at least 1 pt.
							assertTrue(at, slice.sliceExtent() >= VisualRescuePlanner.MIN_RESCUE_ADVANCE);
						}
						// Do not extend beyond the source box.
						assertTrue(at, LayoutUtils.compare(slice.nextOffset(), extent) <= 0);
					}
				}
			}
		}
	}

	/**
	 * A loop of repeated decisions always terminates in finitely many steps
	 * (direct verification that the structure rules out infinite loops).
	 */
	public void testRepeatedPlanningTerminates() {
		final double sourcePageExtent = 10000;
		final double available = 1;
		double offset = 0;
		int steps = 0;
		while (true) {
			final RescueDecision decision = VisualRescuePlanner.plan(true, available, sourcePageExtent, offset);
			if (!(decision instanceof RescueDecision.Slice slice)) {
				break;
			}
			assertTrue("前進しなければ停止しない", slice.nextOffset() > offset);
			offset = slice.nextOffset();
			++steps;
			assertTrue("ページ数の上限を大きく超えた: " + steps, steps <= 20000);
			if (slice.lastFragment()) {
				break;
			}
		}
		assertEquals(sourcePageExtent, offset, 0);
		assertEquals((int) (sourcePageExtent / available), steps);
	}

	/** Even on a page with insufficient capacity, the decision always terminates (without starting rescue). */
	public void testTinyCapacityTerminatesImmediately() {
		double offset = 0;
		int steps = 0;
		while (VisualRescuePlanner.plan(true, 0.4, 250, offset) instanceof RescueDecision.Slice slice) {
			offset = slice.nextOffset();
			++steps;
			assertTrue(steps < 10);
		}
		assertEquals(0, steps);
	}

	// ------------------------------------------------------------------
	// Avoid tiny fragments (effectively blank pages); added 2026-07-25 in increment 4.
	// ------------------------------------------------------------------

	/**
	 * The practical minimum is the larger of an absolute 20 pt (={@code BreakableBuilder.MIN_PAGE_LIMIT},
	 * the only existing threshold the engine itself uses to identify degeneration) and one quarter
	 * of the fragmentainer capacity.
	 */
	public void testMinUsefulSliceCombinesAbsoluteAndProportionalFloors() {
		assertEquals(20.0, VisualRescuePlanner.MIN_RESCUE_SLICE, 0);
		assertEquals(0.25, VisualRescuePlanner.MIN_RESCUE_FRACTION, 0);
		// The absolute value governs small pages.
		assertEquals(20.0, VisualRescuePlanner.minUsefulSlice(40), 0);
		// The ratio governs large pages.
		assertEquals(200.0, VisualRescuePlanner.minUsefulSlice(800), 0);
		// Use only the absolute value if capacity is unknown or nonpositive.
		assertEquals(20.0, VisualRescuePlanner.minUsefulSlice(0), 0);
		assertEquals(20.0, VisualRescuePlanner.minUsefulSlice(Double.NaN), 0);
		assertEquals(20.0, VisualRescuePlanner.minUsefulSlice(LayoutUtils.NONE), 0);
	}

	/**
	 * <b>Do not start</b> rescue when the available space is extremely small (prevent successive pages
	 * with fragments of a few points, effectively mass-producing blank pages). Lock down rejection
	 * even for values that satisfy the forward-progress guarantee alone.
	 */
	public void testSliverCapacityDoesNotStartARescue() {
		// Satisfies the forward-progress guarantee (1 pt), but falls below the practical minimum (20 pt).
		assertTrue(VisualRescuePlanner.plan(true, 10, 5000, 0) instanceof RescueDecision.Slice);
		assertEquals(RescueDecision.Reason.SLIVER_CAPACITY,
				reasonOf(VisualRescuePlanner.planInFragmentainer(null, true, 800, 10, 5000, 0)));
		// Meets the absolute minimum, but not one quarter (200 pt) of the 800 pt capacity.
		assertEquals(RescueDecision.Reason.SLIVER_CAPACITY,
				reasonOf(VisualRescuePlanner.planInFragmentainer(null, true, 800, 100, 5000, 0)));
		// Rescue if the ratio is satisfied.
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 800, 200, 5000, 0));
		assertEquals(200.0, slice.sliceExtent(), 0);
	}

	/**
	 * Continue slicing a fragment once slicing has started, even if the available space is small.
	 * Stopping here because it is too small would lose the remaining content
	 * (= overflow and clipping, as before).
	 */
	public void testSliverGuardDoesNotAbandonAnStartedRescue() {
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 800, 10, 5000, 200));
		assertEquals(200.0, slice.offset(), 0);
		assertEquals(10.0, slice.sliceExtent(), 0);
		assertTrue(slice.isContinuation());
	}

	/**
	 * Even after rescue starts, the chain ends if the destination has less free space than
	 * {@link VisualRescuePlanner#MIN_RESCUE_ADVANCE} (2026-07-25, independent review finding).
	 *
	 * <p>
	 * This is the <b>only exception</b> to "always continue slicing once started," and is an intentional
	 * termination. Delegating to the outer fragmentainer here would give the same decision on the next
	 * page, because capacity does not vary between pages, causing an infinite loop.
	 * Only degenerate fragmentainers with capacity below 1 pt reach this case: normal rescue cannot start
	 * unless the leading-side minimum (at least 20 pt) is met.
	 * </p>
	 */
	public void testStartedRescueEndsWhenTheNextFragmentainerIsDegenerate() {
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.planInFragmentainer(null, true, 0.4, 0.4, 5000, 200)));
		// Continue slicing at exactly the forward-progress minimum (2×THRESHOLD): lock down the boundary.
		final RescueDecision.Slice slice = sliceOf(VisualRescuePlanner.planInFragmentainer(null, true,
				VisualRescuePlanner.MIN_RESCUE_ADVANCE, VisualRescuePlanner.MIN_RESCUE_ADVANCE, 5000, 200));
		assertTrue(slice.isContinuation());
		assertEquals(VisualRescuePlanner.MIN_RESCUE_ADVANCE, slice.sliceExtent(), 0);
	}

	/**
	 * <b>Do not start</b> rescue for an overflow that is too small to be useful (2026-07-25, increment 6).
	 * Adding an entire page to rescue a few points of overflow would make that page effectively blank;
	 * this guards the trailing side of the requirement to avoid unintended blank pages.
	 */
	public void testSliverRemainderDoesNotStartARescue() {
		// Overflow of 10 pt (less than 20 pt) → no rescue.
		assertEquals(RescueDecision.Reason.SLIVER_REMAINDER,
				reasonOf(VisualRescuePlanner.planInFragmentainer(null, true, 200, 200, 210, 0)));
		// Rescue an overflow of exactly 20 pt (inclusive boundary).
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 200, 200, 220, 0));
		assertEquals(200.0, slice.sliceExtent(), 0);
		assertFalse(slice.lastFragment());
	}

	/**
	 * Do not impose a <b>ratio</b> on the trailing-side minimum: that would reject a slightly too-tall
	 * image placed on A4 (842 pt), which is exactly the intended use case.
	 */
	public void testSliverRemainderUsesTheAbsoluteFloorOnly() {
		// Capacity 800 pt, overflow 100 pt. Imposing the ratio (200 pt) would reject this.
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 800, 800, 900, 0));
		assertEquals(800.0, slice.sliceExtent(), 0);
	}

	/**
	 * The trailing-side minimum also applies <b>only at the start</b>. Once slicing starts, continue
	 * to the end even if the remainder is small (stopping would lose content).
	 */
	public void testSliverRemainderGuardDoesNotAbandonAnStartedRescue() {
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 200, 200, 405, 200));
		assertEquals(200.0, slice.offset(), 0);
		assertEquals(200.0, slice.sliceExtent(), 0);
		assertFalse("残り5ptでも続ける", slice.lastFragment());
	}

	/** Absolute positioning is excluded before the capacity check. */
	public void testFragmentainerPlanStillRejectsAbsolute() {
		assertEquals(RescueDecision.Reason.ABSOLUTE,
				reasonOf(VisualRescuePlanner.planInFragmentainer(PosType.ABSOLUTE, true, 800, 800, 5000, 0)));
	}

	/**
	 * Treat a remainder at or below {@code LayoutUtils.THRESHOLD} as consumed.
	 * A plain {@code remaining > 0} would create an effectively blank fragment page when rounding
	 * leaves a remainder such as 0.1 pt.
	 */
	public void testNegligibleRemainderIsExhaustedNotADegenerateFragment() {
		assertEquals(RescueDecision.Reason.EXHAUSTED,
				reasonOf(VisualRescuePlanner.plan(true, 100, 250, 249.9)));
		// A remainder at or above THRESHOLD becomes the last fragment (LayoutUtils.compare treats
		// a difference below THRESHOLD as equal; the boundary itself is significant).
		assertEquals(LayoutUtils.THRESHOLD,
				sliceOf(VisualRescuePlanner.plan(true, 100, 250, 250 - LayoutUtils.THRESHOLD)).sliceExtent(), 1e-9);
		assertEquals(0.6, sliceOf(VisualRescuePlanner.plan(true, 100, 250, 249.4)).sliceExtent(), 1e-9);
	}

	/**
	 * Do not leave a degenerate, tiny tail after cutting a full-capacity slice
	 * (if the remainder exceeds capacity by THRESHOLD or less, fit it in a single final fragment).
	 */
	public void testNoDegenerateTailAfterAFullSlice() {
		// Remainder 200.3, capacity 200 → treated as fitting (last fragment).
		final RescueDecision.Slice slice = sliceOf(VisualRescuePlanner.plan(true, 200, 400.3, 200));
		assertTrue(slice.lastFragment());
		assertFalse(slice.hasTail());
	}

	/**
	 * Even repeated decisions with fragmentainer capacity converge to a reasonable page count
	 * (because each consumes the full capacity).
	 */
	public void testFragmentainerPlanningConsumesFullCapacity() {
		final double capacity = 200, sourcePageExtent = 1000;
		double offset = 0;
		int steps = 0;
		while (VisualRescuePlanner.planInFragmentainer(PosType.FLOW, true, capacity, capacity, sourcePageExtent,
				offset) instanceof RescueDecision.Slice slice) {
			assertTrue("前進しなければ停止しない", slice.nextOffset() > offset);
			offset = slice.nextOffset();
			++steps;
			assertTrue("断片が多すぎる: " + steps, steps <= 10);
			if (slice.lastFragment()) {
				break;
			}
		}
		assertEquals(5, steps);
		assertEquals(sourcePageExtent, offset, 0);
	}

	// ------------------------------------------------------------------
	// Value-type invariants.
	// ------------------------------------------------------------------

	/** Cannot construct a Slice that makes no progress. */
	public void testSliceRejectsNonAdvancingInterval() {
		try {
			new RescueDecision.Slice(10, 5, 10, false, false);
			fail("前進しないSliceは作れないはず");
		} catch (final IllegalArgumentException expected) {
			// As expected.
		}
	}

	/** Cannot construct a nonpositive fragment. */
	public void testSliceRejectsNonPositiveExtent() {
		try {
			new RescueDecision.Slice(0, 0, 1, true, false);
			fail("寸法0のSliceは作れないはず");
		} catch (final IllegalArgumentException expected) {
			// As expected.
		}
	}

	/** {@code firstFragment} must agree with {@code offset == 0}. */
	public void testSliceRejectsInconsistentFirstFlag() {
		try {
			new RescueDecision.Slice(10, 5, 15, true, false);
			fail("offset>0でfirstFragmentは作れないはず");
		} catch (final IllegalArgumentException expected) {
			// As expected.
		}
	}
}
