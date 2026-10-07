package jp.cssj.test.unit.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.fragment.TableCutter;

/**
 * Tests for table cut decisions (C4-T1). Fix the pure decisions extracted from the
 * TableBox / TableRowGroupBox cut loops without a box tree.
 */
public class TableCutterTest extends TestCase {
	public void testKeepOrMoveAll() {
		assertSame(SplitResult.KEEP, TableCutter.keepOrMoveAll(IPageBreakableBox.FLAGS_FIRST));
		assertSame(SplitResult.MOVE, TableCutter.keepOrMoveAll((byte) 0));
	}

	public void testReserveNonBreakable() {
		// No header or footer, boundary does not reach the bottom margin: only the start frame.
		assertEquals(90, TableCutter.reserveNonBreakable(100, 120, 10, 5, 8, -1, -1), 0.01);
		// With a header: reserve its size.
		assertEquals(70, TableCutter.reserveNonBreakable(100, 120, 10, 5, 8, 20, -1), 0.01);
		// With a footer: reserve footer + end frame (ignore margin).
		assertEquals(65, TableCutter.reserveNonBreakable(100, 120, 10, 5, 8, -1, 20), 0.01);
		// No footer, boundary inside the bottom margin (over=5 < margin=8): trim the margin.
		assertEquals(87, TableCutter.reserveNonBreakable(100, 105, 5, 5, 8, -1, -1), 0.01);
	}

	public void testGroupBreakAvoid() {
		final PageBreakMode a = PageBreakMode.AUTO, v = PageBreakMode.AVOID;
		assertFalse(TableCutter.groupBreakAvoid(a, a, a, a));
		assertTrue(TableCutter.groupBreakAvoid(v, a, a, a));
		assertTrue(TableCutter.groupBreakAvoid(a, v, a, a));
		// avoid on a row touching the boundary also prohibits a break.
		assertTrue(TableCutter.groupBreakAvoid(a, a, v, a));
		assertTrue(TableCutter.groupBreakAvoid(a, a, a, v));
	}

	public void testRowBreakAvoidByPosition() {
		final PageBreakMode a = PageBreakMode.AUTO, v = PageBreakMode.AVOID;
		final boolean[] none = {};
		assertFalse(TableCutter.rowBreakAvoid(2, false, a, a, none, none, none));
		assertTrue(TableCutter.rowBreakAvoid(2, false, v, a, none, none, none));
		assertTrue(TableCutter.rowBreakAvoid(2, false, a, v, none, none, none));
	}

	public void testRowBreakAvoidByExtendedCell() {
		final PageBreakMode a = PageBreakMode.AUTO;
		// Spanning splittable cells does not prohibit a break.
		assertFalse(TableCutter.rowBreakAvoid(2, false, a, a, new boolean[] { true }, new boolean[] { true },
				new boolean[] { true }));
		// Spanning unsplittable cells (avoid-inside, etc.) prohibits a break.
		assertTrue(TableCutter.rowBreakAvoid(2, false, a, a, new boolean[] { false }, new boolean[] { true },
				new boolean[] { true }));
		// Do not prohibit a break if the span does not extend.
		assertFalse(TableCutter.rowBreakAvoid(2, false, a, a, new boolean[] { false }, new boolean[] { false },
				new boolean[] { true }));
	}

	public void testRowBreakAvoidFirstRowsSpecialCase() {
		final PageBreakMode a = PageBreakMode.AUTO, v = PageBreakMode.AVOID;
		// Rows 1–2 at the top of the page: spans with matching writing modes lift the prohibition.
		// (Overrides even row-position avoid: behavior of the old implementation.)
		assertFalse(TableCutter.rowBreakAvoid(1, true, v, a, new boolean[] { true }, new boolean[] { true },
				new boolean[] { true }));
		// Spans with different writing modes always prohibit a break.
		assertTrue(TableCutter.rowBreakAvoid(1, true, a, a, new boolean[] { false }, new boolean[] { true },
				new boolean[] { false }));
		// Without a span, row-position avoid remains unchanged.
		assertTrue(TableCutter.rowBreakAvoid(1, true, v, a, new boolean[] { true }, new boolean[] { false },
				new boolean[] { true }));
	}

	public void testMixedFlowKeep() {
		assertFalse(TableCutter.mixedFlowKeep(new boolean[] { true, true }));
		assertTrue(TableCutter.mixedFlowKeep(new boolean[] { true, false }));
		assertFalse(TableCutter.mixedFlowKeep(new boolean[] {}));
	}

	public void testCellFragmentStateHorizontal() {
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = new net.zamasoft.foliojet.layout.part.AbsoluteRectFrame(
				net.zamasoft.foliojet.layout.box.params.RectFrame.NULL_FRAME);
		final net.zamasoft.foliojet.layout.box.params.Dimension size = net.zamasoft.foliojet.layout.box.params.Dimension
				.create(50, 80, net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE,
						net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE);
		final net.zamasoft.foliojet.layout.box.params.Dimension auto = net.zamasoft.foliojet.layout.box.params.Dimension
				.create(0, 0, net.zamasoft.foliojet.layout.box.params.LengthType.AUTO,
						net.zamasoft.foliojet.layout.box.params.LengthType.AUTO);
		final net.zamasoft.foliojet.layout.fragment.TableCutter.CellFragmentState state = net.zamasoft.foliojet.layout.fragment.TableCutter
				.cellFragmentState(false, size, auto, frame, 100, 30);
		// The specified page-direction size is split into an absolute remaining size (100-30).
		assertEquals(70, state.nextSize().getHeight(), 0.01);
		assertEquals(net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE, state.nextSize().getHeightType());
		assertEquals(50, state.nextSize().getWidth(), 0.01);
		// The AUTO minimum size remains unchanged.
		assertSame(auto, state.nextMinSize());
		// The remaining size cannot be negative.
		assertEquals(0, net.zamasoft.foliojet.layout.fragment.TableCutter
				.cellFragmentState(false, size, auto, frame, 100, 120).nextSize().getHeight(), 0.01);
	}

	public void testTableFragmentFrames() {
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = new net.zamasoft.foliojet.layout.part.AbsoluteRectFrame(
				net.zamasoft.foliojet.layout.box.params.RectFrame.NULL_FRAME);
		// If headers/footers repeat, do not cut the frame (same reference).
		final net.zamasoft.foliojet.layout.fragment.TableCutter.TableFragmentFrames repeat = net.zamasoft.foliojet.layout.fragment.TableCutter
				.tableFragmentFrames(false, true, true, frame);
		assertSame(frame, repeat.nextFrame());
		assertSame(frame, repeat.prevFrame());
	}

	public void testRowPreDecideNotPageFirst() {
		final double[] extents = { 30 };
		final boolean[] match = { true };
		final boolean[] noAvoid = { false };
		final boolean[] noCollapse = { false };
		// Below the cut line → move the whole row.
		assertSame(SplitResult.MOVE,
				TableCutter.rowPreDecide(false, false, -1, 30, false, extents, match, noAvoid, noCollapse, -1));
		// Above the cut line (spanning cells also fit) → keep.
		assertSame(SplitResult.KEEP,
				TableCutter.rowPreDecide(false, false, 40, 30, false, extents, match, noAvoid, noCollapse, -1));
		// A spanning cell overflows → proceed to the main processing.
		assertNull(TableCutter.rowPreDecide(false, false, 40, 30, false, new double[] { 50 }, match, noAvoid,
				noCollapse, -1));
		// Row avoid-inside (not the first row) → move.
		assertSame(SplitResult.MOVE,
				TableCutter.rowPreDecide(false, false, 10, 30, true, extents, match, noAvoid, noCollapse, -1));
		// For the first row, ignore avoid and proceed to the main processing.
		assertNull(TableCutter.rowPreDecide(false, true, 10, 30, true, extents, match, noAvoid, noCollapse, -1));
		// Cell with a different writing mode → move.
		assertSame(SplitResult.MOVE, TableCutter.rowPreDecide(false, false, 10, 30, false, extents,
				new boolean[] { false }, noAvoid, noCollapse, -1));
		// Prefer row boundaries (2026-08-27): if the row intersected by the cut line fits entirely
		// in a new fragmentainer, carry the whole row over.
		assertSame(SplitResult.MOVE,
				TableCutter.rowPreDecide(false, false, 10, 30, false, extents, match, noAvoid, noCollapse, 100));
		// A row that cannot fit whole is cut in place (proceed to the main processing).
		assertNull(TableCutter.rowPreDecide(false, false, 10, 300, false, new double[] { 300 }, match, noAvoid,
				noCollapse, 100));
		// The first row proceeds to the main processing as before (do not propagate to moving the entire table).
		assertNull(TableCutter.rowPreDecide(false, true, 10, 30, false, extents, match, noAvoid, noCollapse, 100));
	}

	public void testRowPreDecidePageFirst() {
		final double[] extents = { 30 };
		final boolean[] match = { true };
		final boolean[] noAvoid = { false };
		// Keep it if it fits.
		assertSame(SplitResult.KEEP, TableCutter.rowPreDecide(true, true, 40, 30, false, extents, match, noAvoid,
				new boolean[] { false }, -1));
		// Keep it if the writing mode differs (do not move).
		assertSame(SplitResult.KEEP, TableCutter.rowPreDecide(true, true, 10, 30, false, extents,
				new boolean[] { false }, noAvoid, new boolean[] { false }, -1));
		// Give up splitting if there is a zero-height cell without an upper boundary.
		assertSame(SplitResult.KEEP, TableCutter.rowPreDecide(true, true, 10, 30, false, extents, match, noAvoid,
				new boolean[] { true }, -1));
		// Otherwise, proceed to the main processing (cell splitting).
		assertNull(TableCutter.rowPreDecide(true, true, 10, 30, false, extents, match, noAvoid,
				new boolean[] { false }, -1));
	}

	public void testFirstForceBreak() {
		final PageBreakMode a = PageBreakMode.AUTO, p = PageBreakMode.PAGE;
		// 2 groups × 2 rows, row size 10. The cut line is far enough below.
		final double[][] sizes = { { 10, 10 }, { 10, 10 } };
		final PageBreakMode[] ga = { a, a };
		final PageBreakMode[][] ra = { { a, a }, { a, a } };
		// No forced page break → null.
		assertNull(TableCutter.firstForceBreak(1000, 0, sizes, ga, ga, ra, ra));
		// Immediately before group 2 → cut at the end of the preceding group (0).
		TableCutter.ForceBreakAt at = TableCutter.firstForceBreak(1000, 0, sizes,
				new PageBreakMode[] { a, p }, ga, ra, ra);
		assertEquals(0, at.rowGroup());
		assertEquals(-1, at.row());
		assertSame(p, at.breakMode());
		// Immediately after group 1 → cut at that group's end.
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, new PageBreakMode[] { p, a }, ra, ra);
		assertEquals(0, at.rowGroup());
		assertEquals(-1, at.row());
		// Immediately before row 2 → cut immediately after row 1.
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, ga,
				new PageBreakMode[][] { { a, p }, { a, a } }, ra);
		assertEquals(0, at.rowGroup());
		assertEquals(0, at.row());
		// Immediately before the first row of group 2 → cut at the group boundary (end of the preceding group).
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, ga,
				new PageBreakMode[][] { { a, a }, { p, a } }, ra);
		assertEquals(0, at.rowGroup());
		assertEquals(-1, at.row());
		// Immediately after row 1 (more rows follow in the group) → cut at that row.
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, ga, ra,
				new PageBreakMode[][] { { p, a }, { a, a } });
		assertEquals(0, at.rowGroup());
		assertEquals(0, at.row());
		// Immediately after the last row of a group → cut at the end of the group.
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, ga, ra,
				new PageBreakMode[][] { { a, p }, { a, a } });
		assertEquals(0, at.rowGroup());
		assertEquals(-1, at.row());
		// Ignore directives on rows beyond the cut line (handled by automatic page breaks).
		assertNull(TableCutter.firstForceBreak(15, 0, sizes, ga, ga,
				new PageBreakMode[][] { { a, a }, { p, a } }, ra));
		// An after directive at the end of the table does not produce a page break.
		assertNull(TableCutter.firstForceBreak(1000, 0, sizes, ga, ga, ra,
				new PageBreakMode[][] { { a, a }, { a, p } }));
	}

	public void testFirstRowFlags() {
		final byte first = IPageBreakableBox.FLAGS_FIRST;
		// No change unless at the top of the page.
		assertEquals(0, TableCutter.firstRowFlags((byte) 0, 2, true));
		// First row: set FLAGS_FIRST_ROW.
		assertEquals((byte) (first | IPageBreakableBox.FLAGS_FIRST_ROW),
				TableCutter.firstRowFlags(first, 0, false));
		// Second and later rows: clear FLAGS_FIRST. Set FIRST_ROW if spanned.
		assertEquals(IPageBreakableBox.FLAGS_FIRST_ROW, TableCutter.firstRowFlags(first, 1, true));
		assertEquals(0, TableCutter.firstRowFlags(first, 1, false));
	}
}
