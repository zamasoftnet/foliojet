package net.zamasoft.foliojet.layout.box.content;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.rescue.RescuePolicy;
import net.zamasoft.foliojet.layout.rescue.VisualRescueFloatBox;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * Branch-table tests for {@code Floatings.splitPageAxis} (added 2026-07-24,
 * P2-1 of exclusion area P2. Source of truth:
 * "P2-1 test matrix (minimum)" in the development record).
 *
 * <p>
 * Builds synthetic Floatings/Floating instances (stub boxes with scripted splits), calls
 * {@code splitPageAxis} directly, and locks down the result classification (KeepAll/MoveAll/Partition,
 * corresponding one-to-one to the old null/this/new sentinels as of P2-1), each float's destination,
 * the SPLIT remainder's coordinates (0,0) and inherited serial, and the case fall-through path
 * (branch table 4→5). This <b>locks down existing behavior</b>: expected values for every scenario
 * remain unchanged through P2-3's plan-driven commit and P2-5's unification with typed results.
 * </p>
 */
public class FloatingsSplitPageAxisTest extends TestCase {
	private static final byte NO_FLAGS = 0;
	private static final byte FIRST = IPageBreakableBox.FLAGS_FIRST;

	@Override
	protected void setUp() {
		ContinuationStats.reset();
	}

	// ------------------------------------------------------------------
	// Helpers: result classification (lock down the mapping to the old null/this/new sentinels).
	// ------------------------------------------------------------------

	/** Former null: everything stays in the previous fragment. */
	private static void assertKeepAll(final FloatSplitResult result) {
		assertTrue("KeepAllのはず: " + result, result instanceof FloatSplitResult.KeepAll);
	}

	/** Former this: everything goes to the next fragment (deferred representation; the source list is intact). */
	private static void assertMoveAll(final FloatSplitResult result) {
		assertTrue("MoveAllのはず: " + result, result instanceof FloatSplitResult.MoveAll);
	}

	/** Former new Floatings: partial movement. Returns the remainder ledger. */
	private static Floatings partitionRemainder(final FloatSplitResult result) {
		assertTrue("Partitionのはず: " + result, result instanceof FloatSplitResult.Partition);
		return ((FloatSplitResult.Partition) result).remainder();
	}

	// ------------------------------------------------------------------
	// Helpers: synthetic boxes.
	// ------------------------------------------------------------------

	private static BlockParams blockParams(final WritingMode flow, final PageBreakMode pageBreakInside) {
		final BlockParams params = new BlockParams();
		params.flow = flow;
		params.pageBreakInside = pageBreakInside;
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		return params;
	}

	/** The first argument (owner) to splitPageAxis. Only the writing direction is consulted. */
	private static AbstractContainerBox owner(final WritingMode flow) {
		return new FloatBlockBox(blockParams(flow, PageBreakMode.AUTO), new FloatPos());
	}

	/**
	 * A BLOCK float box with a scriptable page-axis size and split result.
	 * Scenarios still use the {@code SplitResult} type, translated into the actual split path
	 * ({@code splitFloatFragment}) introduced in A-3a-2. A Split becomes a PreparedFloatFragment
	 * with a recipe that returns the canned remainder as-is (one-shot materialization and serial
	 * inheritance use the real mechanism).
	 * Fails if a split is called in a scenario with scripted==null.
	 */
	private static class StubBlockFloat extends FloatBlockBox {
		private final double pageExtent;
		private final SplitResult scripted;
		int splitCalls;
		double seenPageLimit = Double.NaN;
		byte seenFlags = -1;
		BreakMode seenMode;

		StubBlockFloat(final BlockParams params, final double pageExtent, final SplitResult scripted) {
			super(params, new FloatPos());
			this.pageExtent = pageExtent;
			this.scripted = scripted;
		}

		@Override
		public net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit splitFloatFragment(final int serial,
				final double pageLimit, final BreakMode mode, final byte flags) {
			++this.splitCalls;
			this.seenPageLimit = pageLimit;
			this.seenMode = mode;
			this.seenFlags = flags;
			if (this.scripted == null) {
				throw new AssertionError("切断はこのシナリオでは呼ばれないはず");
			}
			return switch (this.scripted) {
			case SplitResult.Keep keep -> net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.KEEP;
			case SplitResult.Move move -> net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.MOVE;
			case SplitResult.Split(final IPageBreakableBox remainder) ->
				new net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Prepared(
						new net.zamasoft.foliojet.layout.fragment.PreparedFloatFragment(serial,
								(state, container) -> (net.zamasoft.foliojet.layout.box.AbstractBlockBox) remainder,
								null, null, 0));
			case SplitResult.Frame frame -> throw new AssertionError("Frameはfloatではスクリプトしない");
			};
		}

		@Override
		public double getPageExtent(final WritingMode flow) {
			return this.pageExtent;
		}
	}

	/** A REPLACED (atomic) float box. splitPageAxis does not cast REPLACED boxes. */
	private static final class StubReplacedFloat extends StubBlockFloat {
		StubReplacedFloat(final BlockParams params, final double pageExtent) {
			super(params, pageExtent, null);
		}

		@Override
		public BoxType getType() {
			return BoxType.REPLACED;
		}
	}

	private static StubBlockFloat block(final double pageExtent) {
		return new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), pageExtent, null);
	}

	private static StubBlockFloat blockSplitting(final double pageExtent, final SplitResult scripted) {
		return new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), pageExtent, scripted);
	}

	private static Floatings.Floating floating(final int serial, final StubBlockFloat box, final double pageAxis) {
		return new Floatings.Floating(serial, box, 5, pageAxis);
	}

	private static Floatings floatingsOf(final Floatings.Floating... floats) {
		final Floatings floatings = new Floatings();
		for (final Floatings.Floating f : floats) {
			floatings.addFloating(f);
		}
		return floatings;
	}

	// ------------------------------------------------------------------
	// Lock down result classification (mapping to the old nextFloatings state machine's sentinels).
	// ------------------------------------------------------------------

	/** Matrix: all KEEP → KeepAll (formerly null). */
	public void testAllKeepReturnsKeepAll() {
		final Floatings.Floating f0 = floating(1, block(10), 0);
		final Floatings.Floating f1 = floating(2, block(10), 20);
		final Floatings floatings = floatingsOf(f0, f1);
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		// The source list remains intact.
		assertEquals(2, floatings.getCount());
		assertSame(f0, floatings.getFloating(0));
		assertSame(f1, floatings.getFloating(1));
	}

	/** Matrix: all MOVE → MoveAll (formerly this; deferred representation leaves the source list untouched). */
	public void testAllMoveReturnsMoveAll() {
		final Floatings.Floating f0 = floating(1, block(10), 150);
		final Floatings.Floating f1 = floating(2, block(10), 160);
		final Floatings floatings = floatingsOf(f0, f1);
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		// MoveAll is a deferred representation: floats remain in the source list.
		assertEquals(2, floatings.getCount());
		assertSame(f0, floatings.getFloating(0));
		assertSame(f1, floatings.getFloating(1));
	}

	/** Matrix: KEEP then MOVE (a MOVE after the initial KEEP produces Partition). */
	public void testKeepThenMovePartition() {
		final Floatings.Floating keep = floating(1, block(50), 0);
		final Floatings.Floating move = floating(2, block(10), 150);
		final Floatings floatings = floatingsOf(keep, move);
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertNotSame(floatings, next);
		assertEquals(1, floatings.getCount());
		assertSame(keep, floatings.getFloating(0));
		assertEquals(1, next.getCount());
		assertSame(move, next.getFloating(0));
	}

	/** Matrix: MOVE prefix then KEEP (transfer the preceding MOVEs; the core of the transfer logic). */
	public void testMovePrefixThenKeepTransfersPrefix() {
		final Floatings.Floating move0 = floating(1, block(10), 150);
		final Floatings.Floating move1 = floating(2, block(10), 160);
		final Floatings.Floating keep = floating(3, block(50), 0);
		final Floatings floatings = floatingsOf(move0, move1, keep);
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertNotSame(floatings, next);
		// The preceding MOVEs go to next in their original order; KEEP stays in the source.
		assertEquals(2, next.getCount());
		assertSame(move0, next.getFloating(0));
		assertSame(move1, next.getFloating(1));
		assertEquals(1, floatings.getCount());
		assertSame(keep, floatings.getFloating(0));
	}

	/** Matrix: SPLIT alone; the original stays in this, and the remainder goes to next at (0,0), inheriting serial. */
	public void testSplitSingleKeepsOriginalAndSendsRemainder() {
		final StubBlockFloat remainder = block(60);
		final StubBlockFloat box = blockSplitting(100, new SplitResult.Split(remainder));
		final Floatings.Floating f = floating(7, box, 40);
		final Floatings floatings = floatingsOf(f);
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertNotSame(floatings, next);
		// The original Floating stays in this.
		assertEquals(1, floatings.getCount());
		assertSame(f, floatings.getFloating(0));
		assertSame(box, floatings.getFloating(0).box);
		// The remainder is at (0,0), the start of the next fragment, and inherits serial.
		assertEquals(1, next.getCount());
		final Floatings.Floating rf = next.getFloating(0);
		assertSame(remainder, rf.box);
		assertEquals(7, rf.serial);
		assertEquals(0.0, rf.lineAxis, 0);
		assertEquals(0.0, rf.pageAxis, 0);
		// The split call uses the cut line in the float's coordinate system and FLAGS_SPLIT.
		assertEquals(1, box.splitCalls);
		assertEquals(60.0, box.seenPageLimit, 0);
		assertEquals(IPageBreakableBox.FLAGS_SPLIT, box.seenFlags);
		assertSame(BreakMode.DEFAULT_BREAK_MODE, box.seenMode);
	}

	/** Matrix: mixed MOVE+SPLIT+KEEP (combines prefix transfer, SPLIT, and KEEP). */
	public void testMoveSplitKeepMixed() {
		final Floatings.Floating move = floating(11, block(10), 150);
		final StubBlockFloat remainder = block(60);
		final StubBlockFloat splitBox = blockSplitting(100, new SplitResult.Split(remainder));
		final Floatings.Floating split = floating(22, splitBox, 40);
		final Floatings.Floating keep = floating(33, block(50), 0);
		final Floatings floatings = floatingsOf(move, split, keep);
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertNotSame(floatings, next);
		// In next: [original Floating moved by MOVE, SPLIT remainder], in the original order.
		assertEquals(2, next.getCount());
		assertSame(move, next.getFloating(0));
		assertSame(remainder, next.getFloating(1).box);
		assertEquals(22, next.getFloating(1).serial);
		// In this: [SPLIT original (left in place), KEEP], in the original order.
		assertEquals(2, floatings.getCount());
		assertSame(split, floatings.getFloating(0));
		assertSame(keep, floatings.getFloating(1));
	}

	// ------------------------------------------------------------------
	// Classification: first × avoid × writing axis × BLOCK/REPLACED, boundaries, split results.
	// ------------------------------------------------------------------

	/** Branch table 3: a non-first BLOCK without avoid and with a matching writing axis splits with FLAGS_SPLIT. */
	public void testNonFirstBlockSplitUsesSplitFlag() {
		final StubBlockFloat box = blockSplitting(100, new SplitResult.Split(block(60)));
		final Floatings floatings = floatingsOf(floating(1, box, 40));
		floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS);
		assertEquals(1, box.splitCalls);
		assertEquals(IPageBreakableBox.FLAGS_SPLIT, box.seenFlags);
		assertEquals(60.0, box.seenPageLimit, 0);
	}

	/** Branch table 3: a first BLOCK (FLAGS_FIRST and physically at the fragment start) splits with FLAGS_FIRST. */
	public void testFirstBlockSplitUsesFirstFlag() {
		final StubBlockFloat box = blockSplitting(200, new SplitResult.Split(block(100)));
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
		assertEquals(1, box.splitCalls);
		assertEquals(IPageBreakableBox.FLAGS_FIRST, box.seenFlags);
		assertEquals(100.0, box.seenPageLimit, 0);
		assertNotSame(floatings, next);
	}

	/**
	 * Branch table 4→5 fall-through: a non-first BLOCK with avoid is treated as REPLACED and MOVEd whole, without
	 * split.
	 */
	public void testAvoidNonFirstFallsThroughToMove() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AVOID), 100, null);
		final Floatings floatings = floatingsOf(floating(1, box, 40));
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, box.splitCalls);
	}

	/** Branch table 3: a physically first box overrides the avoid hint and actually splits (2026-07-23 rule). */
	public void testAvoidFirstOverridesAvoidAndSplits() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AVOID), 200,
				new SplitResult.Split(block(100)));
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST);
		assertEquals(1, box.splitCalls);
		assertEquals(IPageBreakableBox.FLAGS_FIRST, box.seenFlags);
	}

	/** Branch table 4→5 fall-through: a non-first BLOCK with a mismatched writing axis is atomic and MOVEd whole. */
	public void testAxisMismatchNonFirstFallsThroughToMove() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.RL, PageBreakMode.AUTO), 100, null);
		final Floatings floatings = floatingsOf(floating(1, box, 40));
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, box.splitCalls);
	}

	/**
	 * Branch table 4→5 fall-through: a first BLOCK with a mismatched writing axis allows overflow and KEEPs;
	 * this is the <b>previous</b> behavior with rescue splitting disabled.
	 */
	public void testAxisMismatchFirstKeepsOverflowing() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.RL, PageBreakMode.AUTO), 200, null);
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		try (RescuePolicy.Scope scope = RescuePolicy.DISABLED.scoped()) {
			assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
		}
		assertEquals(0, box.splitCalls);
	}

	/** Branch table 5: a non-first REPLACED box is MOVEd whole. */
	public void testReplacedNonFirstMoves() {
		final StubReplacedFloat box = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 100);
		final Floatings floatings = floatingsOf(floating(1, box, 40));
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, box.splitCalls);
	}

	/**
	 * Branch table 5: a first REPLACED box allows overflow and KEEPs;
	 * this is the <b>previous</b> behavior with rescue splitting disabled.
	 */
	public void testReplacedFirstKeepsOverflowing() {
		final StubReplacedFloat box = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 200);
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		try (RescuePolicy.Scope scope = RescuePolicy.DISABLED.scoped()) {
			assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
		}
		assertEquals(0, box.splitCalls);
	}

	// ------------------------------------------------------------------
	// Branch table 5-R: rescue splitting (2026-07-25, increment 7).
	// ------------------------------------------------------------------

	/**
	 * Branch table 5-R: if a first REPLACED box still overflows (currently the only point with no progress
	 * that falls back to drawing with overflow), slice it geometrically instead of using Keep.
	 * The source ledger receives the head (occupied extent = capacity); the remainder ledger receives the
	 * tail (all remaining content, coordinates (0,0), inherited serial). The original box is <b>not split</b>
	 * (split is not called).
	 */
	public void testReplacedFirstIsRescued() {
		final StubReplacedFloat box = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 200);
		final Floatings.Floating f = floating(9, box, 0);
		final Floatings floatings = floatingsOf(f);
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
		assertEquals(0, box.splitCalls);
		// head: keeps the original position and occupies only the available capacity.
		assertEquals(1, floatings.getCount());
		final Floatings.Floating head = floatings.getFloating(0);
		assertNotSame(f, head);
		assertEquals(9, head.serial);
		assertEquals(0.0, head.pageAxis, 0);
		assertEquals(5.0, head.lineAxis, 0);
		final VisualRescueFloatBox headBox = (VisualRescueFloatBox) head.box;
		assertSame(box, headBox.getSource());
		assertEquals(0.0, headBox.getOffset(), 0);
		assertEquals(100.0, headBox.getSliceExtent(), 0);
		assertEquals(200.0, headBox.getSourcePageExtent(), 0);
		assertTrue(headBox.isFirstFragment());
		assertFalse(headBox.isLastFragment());
		// tail: coordinates (0,0), the start of the next fragment; inherits serial and contains all remaining content.
		assertEquals(1, next.getCount());
		final Floatings.Floating tail = next.getFloating(0);
		assertEquals(9, tail.serial);
		assertEquals(0.0, tail.pageAxis, 0);
		assertEquals(0.0, tail.lineAxis, 0);
		final VisualRescueFloatBox tailBox = (VisualRescueFloatBox) tail.box;
		assertSame(box, tailBox.getSource());
		assertEquals(100.0, tailBox.getOffset(), 0);
		assertEquals(100.0, tailBox.getSliceExtent(), 0);
		assertTrue(tailBox.isContinuation());
		assertTrue(tailBox.isLastFragment());
	}

	/** Branch table 5-R: a first BLOCK with a mismatched writing axis has the same lack of progress and is rescued. */
	public void testAxisMismatchFirstIsRescued() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.RL, PageBreakMode.AUTO), 200, null);
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
		// Do not touch the original box at all (the normal split is not called).
		assertEquals(0, box.splitCalls);
		assertTrue(floatings.getFloating(0).box instanceof VisualRescueFloatBox);
		assertTrue(next.getFloating(0).box instanceof VisualRescueFloatBox);
	}

	/**
	 * Branch table 5-R: a fragment continuation goes through the same check, and <b>no fragment of a fragment
	 * is created</b> (the interval is represented only by offset/sliceExtent).
	 */
	public void testRescueFragmentIsSlicedAgainWithoutNesting() {
		final StubReplacedFloat box = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 500);
		final VisualRescueFloatBox fragment = new VisualRescueFloatBox(box, WritingMode.TB, 500, 100, 400);
		final Floatings floatings = floatingsOf(new Floatings.Floating(3, fragment, 5, 0));
		final Floatings next = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
		final VisualRescueFloatBox head = (VisualRescueFloatBox) floatings.getFloating(0).box;
		final VisualRescueFloatBox tail = (VisualRescueFloatBox) next.getFloating(0).box;
		// References the original box directly, without nesting.
		assertSame(box, head.getSource());
		assertSame(box, tail.getSource());
		assertEquals(100.0, head.getOffset(), 0);
		assertEquals(100.0, head.getSliceExtent(), 0);
		assertEquals(200.0, tail.getOffset(), 0);
		assertEquals(300.0, tail.getSliceExtent(), 0);
	}

	/**
	 * Branch table 5-R: a non-first box is MOVEd whole as before (no rescue), because the normal option
	 * of sending it to the next fragment is still available.
	 */
	public void testNonFirstIsNotRescued() {
		final StubReplacedFloat box = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 200);
		final Floatings floatings = floatingsOf(floating(1, box, 40));
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
	}

	/**
	 * Branch table 5-R: do not rescue if the overflow is too small to be useful (less than 20 pt).
	 * Adding a page for a few points would make that page effectively blank; this guards the trailing
	 * side of the absolute requirement to avoid unintended blank pages.
	 */
	public void testTinyOverflowIsNotRescued() {
		final StubReplacedFloat box = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 110);
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, FIRST));
	}

	/**
	 * Branch table 5-R: also do not rescue if the fragmentainer has too little usable space
	 * (a guard on the leading side against successive pages with tiny fragments).
	 */
	public void testSliverCapacityIsNotRescued() {
		final StubReplacedFloat box = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 200);
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		// Capacity 15 pt < max(20 pt, 15 pt*0.25).
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 15, FIRST));
	}

	/**
	 * "first" is physical: even with FLAGS_FIRST, a pageAxis away from the start is not first (MOVE in branch
	 * table 2).
	 */
	public void testFirstFlagRequiresPhysicalHead() {
		final StubBlockFloat box = new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO), 200, null);
		final Floatings floatings = floatingsOf(floating(1, box, 10));
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 5, FIRST));
		assertEquals(0, box.splitCalls);
	}

	/** Branch table 1 boundary: overflow within the LayoutUtils.compare tolerance (0.5) KEEPs. */
	public void testBoundaryWithinToleranceKeeps() {
		// Exact fit.
		final StubBlockFloat exact = block(100);
		final Floatings f1 = floatingsOf(floating(1, exact, 0));
		assertKeepAll(f1.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, exact.splitCalls);
		// An overflow of 0.4 (<0.5) is also treated as equal.
		final StubBlockFloat nearly = block(100.4);
		final Floatings f2 = floatingsOf(floating(2, nearly, 0));
		assertKeepAll(f2.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(0, nearly.splitCalls);
	}

	/** Branch table 1 boundary: overflow of 0.5 or more is subject to splitting (proceed to branch table 3). */
	public void testBoundaryBeyondToleranceSplits() {
		final StubBlockFloat box = blockSplitting(100.5, SplitResult.KEEP);
		final Floatings floatings = floatingsOf(floating(1, box, 0));
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, box.splitCalls);
	}

	/** Branch table 3: a Keep split result leaves everything in the previous fragment (KeepAll). */
	public void testSplitResultKeepLeavesAllInPlace() {
		final StubBlockFloat box = blockSplitting(100, SplitResult.KEEP);
		final Floatings.Floating f = floating(1, box, 40);
		final Floatings floatings = floatingsOf(f);
		assertKeepAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, box.splitCalls);
		assertEquals(1, floatings.getCount());
		assertSame(f, floatings.getFloating(0));
	}

	/** Branch table 3: a Move split result MOVEs everything (MoveAll; source list intact). */
	public void testSplitResultMoveMovesWhole() {
		final StubBlockFloat box = blockSplitting(100, SplitResult.MOVE);
		final Floatings.Floating f = floating(1, box, 40);
		final Floatings floatings = floatingsOf(f);
		assertMoveAll(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, box.splitCalls);
		assertEquals(1, floatings.getCount());
		assertSame(f, floatings.getFloating(0));
	}

	// ------------------------------------------------------------------
	// P2-1/P2-2: pure data (FloatMeasurement) and pure decision logic (FloatSplitPlan).
	// ------------------------------------------------------------------

	/** FloatMeasurement captures each float's decision inputs as read-only data. */
	public void testMeasureFixesPerFloatInputs() {
		final StubBlockFloat blockBox = new StubBlockFloat(blockParams(WritingMode.RL, PageBreakMode.AVOID), 100, null);
		final StubReplacedFloat replacedBox = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO),
				30);
		final Floatings floatings = floatingsOf(floating(5, blockBox, 40), floating(6, replacedBox, 0));
		final List<FloatMeasurement> measurements = floatings.measure(WritingMode.TB);
		assertEquals(2, measurements.size());
		final FloatMeasurement m0 = measurements.get(0);
		assertEquals(0, m0.ordinal());
		assertEquals(5, m0.serial());
		assertSame(blockBox, m0.box());
		assertEquals(40.0, m0.pageStart(), 0);
		assertEquals(100.0, m0.pageExtent(), 0);
		assertEquals(140.0, m0.pageEnd(), 0);
		assertFalse(m0.sameWritingAxis()); // owner TB vs float RL
		assertFalse(m0.fragmentHead());
		assertEquals(BoxType.BLOCK, m0.boxType());
		assertEquals(PageBreakMode.AVOID, m0.pageBreakInside());
		final FloatMeasurement m1 = measurements.get(1);
		assertEquals(1, m1.ordinal());
		assertEquals(6, m1.serial());
		assertSame(replacedBox, m1.box());
		assertTrue(m1.sameWritingAxis()); // REPLACED is always true (it bypasses the axis check).
		assertTrue(m1.fragmentHead());
		assertEquals(BoxType.REPLACED, m1.boxType());
		assertNull(m1.pageBreakInside());
	}

	/** Pure decision logic maps branch table 1, 2, 3 (mark only), and 4→5 to KEEP/MOVE/SPLIT_ON_COMMIT. */
	public void testPlanDirectClassifiesBranchTableRows() {
		final Floatings.Floating keep = floating(1, block(50), 0);
		final Floatings.Floating move = floating(2, block(10), 150);
		final Floatings.Floating split = floating(3, blockSplitting(100, null), 40);
		final Floatings floatings = floatingsOf(keep, move, split);
		final FloatSplitPlan plan = FloatSplitPlan.planDirect(floatings, WritingMode.TB, 100, NO_FLAGS);
		assertSame(floatings, plan.expectedSource());
		assertEquals(100.0, plan.pageLimit(), 0);
		assertEquals(NO_FLAGS, plan.flags());
		assertEquals(3, plan.direct().size());
		assertTrue(plan.direct().get(0) instanceof FloatSplitPlan.FloatItemPlan.Keep);
		assertEquals(1, plan.direct().get(0).expected().serial());
		assertTrue(plan.direct().get(1) instanceof FloatSplitPlan.FloatItemPlan.Move);
		assertEquals(2, plan.direct().get(1).expected().serial());
		// Branch table 3 only marks SPLIT_ON_COMMIT: it does not predict the result,
		// but retains only innerLimit (the cut line in float coordinates) and splitFlags.
		final FloatSplitPlan.FloatItemPlan.SplitOnCommit sc = (FloatSplitPlan.FloatItemPlan.SplitOnCommit) plan
				.direct().get(2);
		assertEquals(3, sc.expected().serial());
		assertEquals(60.0, sc.innerLimit(), 0);
		assertEquals(IPageBreakableBox.FLAGS_SPLIT, sc.splitFlags());
	}

	/** first cases in pure decision logic: avoid override, axis mismatch, and REPLACED fall-through classification. */
	public void testClassifyFirstAndFallthroughRows() {
		final double pageLimit = 100;
		// avoid + first → SPLIT_ON_COMMIT (FLAGS_FIRST): override the avoid hint.
		final StubBlockFloat avoidBox = new StubBlockFloat(blockParams(WritingMode.TB, PageBreakMode.AVOID), 200, null);
		final FloatMeasurement avoidFirst = FloatMeasurement.of(0, floating(1, avoidBox, 0), WritingMode.TB);
		final FloatSplitPlan.FloatItemPlan.SplitOnCommit sc = (FloatSplitPlan.FloatItemPlan.SplitOnCommit) FloatSplitPlan
				.classify(avoidFirst, pageLimit, FIRST);
		assertEquals(IPageBreakableBox.FLAGS_FIRST, sc.splitFlags());
		assertEquals(100.0, sc.innerLimit(), 0);
		// avoid + non-first → MOVE via 4→5 fall-through.
		final FloatMeasurement avoidCrossing = FloatMeasurement.of(0, floating(2, avoidBox, 40), WritingMode.TB);
		assertTrue(FloatSplitPlan.classify(avoidCrossing, pageLimit,
				NO_FLAGS) instanceof FloatSplitPlan.FloatItemPlan.Move);
		// Axis mismatch + first → 4→5 fall-through. KEEP as before with rescue disabled;
		// with rescue enabled, branch table 5-R yields RESCUE_ON_COMMIT (2026-07-25, increment 7).
		final StubBlockFloat rlBox = new StubBlockFloat(blockParams(WritingMode.RL, PageBreakMode.AUTO), 200, null);
		final FloatMeasurement axisMismatchFirst = FloatMeasurement.of(0, floating(3, rlBox, 0), WritingMode.TB);
		try (RescuePolicy.Scope scope = RescuePolicy.DISABLED.scoped()) {
			assertTrue(FloatSplitPlan.classify(axisMismatchFirst, pageLimit,
					FIRST) instanceof FloatSplitPlan.FloatItemPlan.Keep);
		}
		final FloatSplitPlan.FloatItemPlan.RescueOnCommit rescue = (FloatSplitPlan.FloatItemPlan.RescueOnCommit) FloatSplitPlan
				.classify(axisMismatchFirst, pageLimit, FIRST);
		assertEquals(3, rescue.expected().serial());
		assertEquals(0.0, rescue.slice().offset(), 0);
		assertEquals(100.0, rescue.slice().sliceExtent(), 0);
		assertEquals(100.0, rescue.slice().nextOffset(), 0);
		assertTrue(rescue.slice().firstFragment());
		assertFalse(rescue.slice().lastFragment());
		// REPLACED + non-first (straddling the boundary) → MOVE.
		final StubReplacedFloat replacedBox = new StubReplacedFloat(blockParams(WritingMode.TB, PageBreakMode.AUTO),
				100);
		final FloatMeasurement replacedCrossing = FloatMeasurement.of(0, floating(4, replacedBox, 40), WritingMode.TB);
		assertTrue(FloatSplitPlan.classify(replacedCrossing, pageLimit,
				NO_FLAGS) instanceof FloatSplitPlan.FloatItemPlan.Move);
	}

	/** P2-3/P2-5: jointly lock down the three typed result classes and ledger contents (mixed scenario). */
	public void testTypedResultMatchesSentinelContract() {
		// KeepAll (formerly null): source list intact.
		final Floatings allKeep = floatingsOf(floating(1, block(10), 0));
		assertKeepAll(allKeep.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, allKeep.getCount());
		// MoveAll (formerly this): deferred representation, source list intact.
		final Floatings allMove = floatingsOf(floating(2, block(10), 150));
		assertMoveAll(allMove.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(1, allMove.getCount());
		// Partition (formerly new Floatings): remainder gets MOVE+SPLIT remainders; source gets KEEP+SPLIT originals.
		final Floatings.Floating move = floating(1, block(10), 150);
		final Floatings.Floating split = floating(2, blockSplitting(100, new SplitResult.Split(block(60))), 40);
		final Floatings.Floating keep = floating(3, block(50), 0);
		final Floatings floatings = floatingsOf(move, split, keep);
		final Floatings remainder = partitionRemainder(floatings.splitPageAxis(owner(WritingMode.TB), 100, NO_FLAGS));
		assertEquals(2, remainder.getCount());
		assertSame(move, remainder.getFloating(0));
		assertEquals(2, remainder.getFloating(1).serial);
		assertEquals(2, floatings.getCount());
		assertSame(split, floatings.getFloating(0));
		assertSame(keep, floatings.getFloating(1));
	}
}
