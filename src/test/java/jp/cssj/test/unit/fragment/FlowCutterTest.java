package jp.cssj.test.unit.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.FlowCutter;

/**
 * Tests for pushback caused by avoiding page breaks between blocks (avoid).
 *
 * <p>
 * In particular, fix the interaction between keep and a float crossing the cut line:
 * a splittable crossing float cancels keep retreat (the float itself is split independently),
 * whereas an unsplittable crossing float (a replaced element or page-break-inside:avoid)
 * does not prevent keep retreat. These branches correspond to the pair of integration fixtures
 * (float-split-in-chain / float-uncut-before-prefix).
 * </p>
 */
public class FlowCutterTest extends TestCase {
	// Two flows: [0]=0..60 (filler), [1]=60..80 (connected by avoid-before).
	// For cut line 100, successful pushback returns to before [1] (around 60).
	private static final double[] FLOW_STARTS = { 0, 60 };
	private static final double[] FLOW_EXTENTS = { 60, 20 };
	private static final boolean[] AVOID_BEFORE = { false, true };
	private static final boolean[] AVOID_AFTER = { false, false };
	private static final double[] FLOW_END_FRAMES = { 0, 0 };

	private static FlowCutter.AvoidPushback pushback(final double[] floatStarts, final double[] floatExtents,
			final boolean[] floatUncut) {
		return FlowCutter.avoidPushback(1, 100, FLOW_STARTS, FLOW_EXTENTS, AVOID_BEFORE, AVOID_AFTER, FLOW_END_FRAMES,
				floatStarts, floatExtents, floatUncut);
	}

	public void testAvoidPushbackWithoutFloats() {
		// Without a float, push back as specified by the avoid chain.
		final FlowCutter.AvoidPushback p = pushback(null, null, null);
		assertNotNull(p);
		assertEquals(60, p.newPageLimit(), 1);
	}

	public void testCuttableCrossingFloatCancelsAvoidPushback() {
		// A splittable float (60..107) crossing the cut line (100) cancels keep retreat.
		// (splitFloatings splits the float itself independently.)
		assertNull(pushback(new double[] { 60 }, new double[] { 47 }, new boolean[] { false }));
	}

	public void testUncutCrossingFloatPreservesAvoidPushback() {
		// An unsplittable crossing float (replaced element / page-break-inside:avoid)
		// does not prevent keep retreat.
		final FlowCutter.AvoidPushback p = pushback(new double[] { 60 }, new double[] { 47 },
				new boolean[] { true });
		assertNotNull(p);
		assertEquals(60, p.newPageLimit(), 1);
	}

	public void testFloatBeyondCutLineCancelsAvoidPushback() {
		// A float starting at or beyond the cut line also cancels keep (faithfully fixes the current rule).
		assertNull(pushback(new double[] { 100 }, new double[] { 10 }, new boolean[] { false }));
	}

	public void testFloatBeforeCutLineIsIrrelevant() {
		// A float ending at or before the cut line is irrelevant.
		final FlowCutter.AvoidPushback p = pushback(new double[] { 0 }, new double[] { 40 },
				new boolean[] { false });
		assertNotNull(p);
	}

	// ---- stepFlags (flag calculation in the automatic page-break main loop, two-phase separation, increment 1) ----

	private static final byte FIRST = net.zamasoft.foliojet.layout.box.IPageBreakableBox.FLAGS_FIRST;
	private static final byte LAST = net.zamasoft.foliojet.layout.box.IPageBreakableBox.FLAGS_LAST;
	private static final byte SPLIT = net.zamasoft.foliojet.layout.box.IPageBreakableBox.FLAGS_SPLIT;

	public void testStepFlagsHeadFlowKeepsFirst() {
		// Flow touching the start edge (pageAxis=0): do not clear FIRST.
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 0, 0, 3, false, (byte) (FIRST | LAST | SPLIT));
		assertEquals(100.0, s.splitLine(), 0);
		assertTrue((s.positionMask() & FIRST) != 0);
		// Not the last flow, so clear LAST.
		assertTrue((s.positionMask() & LAST) == 0);
		assertEquals((byte) (FIRST | SPLIT), s.splitFlags());
	}

	public void testStepFlagsDetachedFlowDropsFirst() {
		// Flow away from the start edge (pageAxis>0): clear FIRST and make splitLine local.
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 40, 1, 3, false, (byte) (FIRST | SPLIT));
		assertEquals(60.0, s.splitLine(), 0);
		assertTrue((s.positionMask() & FIRST) == 0);
		assertEquals(SPLIT, s.splitFlags());
	}

	public void testStepFlagsTailFlowOfForeignBreakKeepsLast() {
		// Last flow, and the automatic page-break target is not this container: retain LAST.
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 40, 2, 3, false, (byte) (FIRST | LAST));
		assertTrue((s.positionMask() & LAST) != 0);
		assertEquals(LAST, s.splitFlags());
	}

	public void testStepFlagsOwnerTargetDropsLastEvenAtTail() {
		// If this container itself is the automatic page-break target, clear LAST even for the last flow.
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 0, 2, 3, true, (byte) (FIRST | LAST));
		assertTrue((s.positionMask() & LAST) == 0);
		assertEquals(FIRST, s.splitFlags());
	}

	public void testStepFlagsPassesThroughOtherBits() {
		// positionMask starts at 0xFF, so bits other than FIRST/LAST (SPLIT, etc.)
		// always pass through unchanged from the outer flags.
		final byte others = (byte) (0xFF & ~(FIRST | LAST));
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 40, 1, 3, false, others);
		assertEquals(others, s.splitFlags());
	}

	// ---- resolveKeep (resolve Keep observations, two-phase separation, increment 3) ----

	public void testResolveKeepAtPageTailKeepsAll() {
		assertEquals(FlowCutter.KeepResolution.KEEP_ALL, FlowCutter.resolveKeep(2, 1, LAST));
	}

	public void testResolveKeepUntowedExaminesNext() {
		// i >= lastOrphan: not pulled back → advance to the next flow.
		assertEquals(FlowCutter.KeepResolution.EXAMINE_NEXT, FlowCutter.resolveKeep(2, 1, (byte) 0));
	}

	public void testResolveKeepTowedBecomesMove() {
		// i < lastOrphan: pulled by break avoidance → treat as Move (the most counterintuitive rule).
		assertEquals(FlowCutter.KeepResolution.TREAT_AS_MOVE, FlowCutter.resolveKeep(0, 2, (byte) 0));
	}

	// ---- resolveMove (resolve Move observations, two-phase separation, increment 4) ----

	private static FlowCutter.MoveResolution resolveMove(final byte positionMask, final byte outerFlags,
			final int index, final int lastOrphan, final boolean ignoreAvoid) {
		return resolveMove(positionMask, outerFlags, index, lastOrphan, ignoreAvoid, -1, 100);
	}

	private static FlowCutter.MoveResolution resolveMove(final byte positionMask, final byte outerFlags,
			final int index, final int lastOrphan, final boolean ignoreAvoid, final int relaxInsideIndex,
			final double fragmentCapacity) {
		// Reuse the FLOW_STARTS and related fixtures (two flows, [1] connected by avoid-before)
		// as pushback input. pageLimit=100.
		return FlowCutter.resolveMove(positionMask, outerFlags, index, lastOrphan, ignoreAvoid, relaxInsideIndex,
				90, 100, fragmentCapacity, FLOW_STARTS, FLOW_EXTENTS, AVOID_BEFORE, AVOID_AFTER, FLOW_END_FRAMES,
				null, null, null);
	}

	public void testResolveMovePhysicalFirstWithOuterSplitCutsHead() {
		final FlowCutter.MoveResolution r = resolveMove(FIRST, SPLIT, 0, 0, false);
		assertTrue(r instanceof FlowCutter.MoveResolution.Terminal t
				&& t.action() instanceof FlowCutter.PreDecision.CutHead c && c.atLimit() == 90);
	}

	public void testResolveMovePhysicalFirstTowedRestartsIgnoringAvoid() {
		// Physical FIRST + outer FIRST + i<lastOrphan: ignore avoid and rerun from lastOrphan.
		final FlowCutter.MoveResolution r = resolveMove(FIRST, FIRST, 0, 2, false);
		assertTrue(r instanceof FlowCutter.MoveResolution.RestartIgnoringAvoid restart
				&& restart.nextIndex() == 2);
	}

	public void testResolveMovePhysicalFirstTowedKeepsWholeAtAvoidProbe() {
		// At an avoid probe of the parent (2026-10-09): there is no break that keeps the avoids, so the box stays whole
		// and the parent relaxes its avoid.
		final FlowCutter.MoveResolution r = resolveMove(FIRST,
				(byte) (FIRST | net.zamasoft.foliojet.layout.box.IPageBreakableBox.FLAGS_AVOID_PROBE), 0, 2, false);
		assertTrue(r instanceof FlowCutter.MoveResolution.Terminal t
				&& t.action() instanceof FlowCutter.PreDecision.KeepFloats);
	}

	public void testResolveMovePhysicalFirstTowedRelaxesInsideWhenChainExceedsEmptyFragmentainer() {
		final FlowCutter.MoveResolution r = resolveMove(FIRST, FIRST, 0, 1, false, 1, 70);
		assertTrue(r instanceof FlowCutter.MoveResolution.RelaxInside relax && relax.index() == 1
				&& relax.fallbackIndex() == 1);
	}

	public void testResolveMovePhysicalFirstTowedKeepsBoundaryFallbackWhenChainFitsEmptyFragmentainer() {
		final FlowCutter.MoveResolution r = resolveMove(FIRST, FIRST, 0, 1, false, 1, 100);
		assertTrue(r instanceof FlowCutter.MoveResolution.RestartIgnoringAvoid restart
				&& restart.nextIndex() == 1);
	}

	public void testResolveMovePhysicalFirstAtPageHeadTailCutsTail() {
		final FlowCutter.MoveResolution r = resolveMove(FIRST, (byte) (FIRST | LAST), 1, 1, false);
		assertTrue(r instanceof FlowCutter.MoveResolution.Terminal t
				&& t.action() instanceof FlowCutter.PreDecision.CutTail);
	}

	public void testResolveMovePhysicalFirstAtPageHeadKeepsFloats() {
		final FlowCutter.MoveResolution r = resolveMove(FIRST, FIRST, 1, 1, false);
		assertTrue(r instanceof FlowCutter.MoveResolution.Terminal t
				&& t.action() instanceof FlowCutter.PreDecision.KeepFloats);
	}

	public void testResolveMovePhysicalFirstElsewhereMovesAll() {
		final FlowCutter.MoveResolution r = resolveMove(FIRST, (byte) 0, 0, 0, false);
		assertTrue(r instanceof FlowCutter.MoveResolution.Terminal t
				&& t.action() instanceof FlowCutter.PreDecision.MoveAll);
	}

	public void testResolveMoveNonFirstWithAvoidPushesBack() {
		// Fixture [1] is connected by avoid-before: returns pushback.
		final FlowCutter.MoveResolution r = resolveMove((byte) 0, (byte) 0, 1, 1, false);
		assertTrue(r instanceof FlowCutter.MoveResolution.Pushback);
		assertEquals(60, ((FlowCutter.MoveResolution.Pushback) r).newPageLimit(), 1);
	}

	public void testResolveMoveIgnoreAvoidSkipsPushback() {
		// Do not reapply the avoid check during an ignoreAvoid rerun.
		final FlowCutter.MoveResolution r = resolveMove((byte) 0, (byte) 0, 1, 1, true);
		assertTrue(r instanceof FlowCutter.MoveResolution.Partition);
	}

	public void testResolveMoveNonFirstBeyondOrphanPartitions() {
		// i > lastOrphan: outside the pushback scope → partition.
		final FlowCutter.MoveResolution r = resolveMove((byte) 0, (byte) 0, 1, 0, false);
		assertTrue(r instanceof FlowCutter.MoveResolution.Partition);
	}
}
