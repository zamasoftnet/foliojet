package net.zamasoft.foliojet.layout.sizing;

import junit.framework.TestCase;

/**
 * {@link FixedGridLayout}(Grid G1a)の純粋計算テストです
 * (consult-codex-2026-07-31-grid-g1.txt §4の期待値をそのまま固定)。
 */
public class FixedGridLayoutTest extends TestCase {

	/** 答申§4のfixed-2x2: 列[100,100]・gap 20/10・高さ[30,50,20,40]。 */
	public void testFixed2x2() {
		final FixedGridLayout layout = new FixedGridLayout(new double[] { 100, 100 }, 20, 10);
		assertEquals(2, layout.columnCount());
		assertEquals(0.0, layout.columnStart(0), 0);
		assertEquals(120.0, layout.columnStart(1), 0);
		assertEquals(0, layout.columnOf(0));
		assertEquals(1, layout.columnOf(1));
		assertEquals(0, layout.rowOf(1));
		assertEquals(1, layout.rowOf(2));

		final FixedGridLayout.Placement p = layout.place(new double[] { 30, 50, 20, 40 });
		assertEquals(2, p.rowStarts().length);
		assertEquals(0.0, p.rowStarts()[0], 0);
		assertEquals(50.0, p.rowHeights()[0], 0); // max(30,50)
		assertEquals(60.0, p.rowStarts()[1], 0); // 50 + rowGap10
		assertEquals(40.0, p.rowHeights()[1], 0); // max(20,40)
		assertEquals(100.0, p.totalExtent(), 0); // 50+10+40
	}

	/** 端数行(3item×2列=2行目1個)と単一item。 */
	public void testPartialRows() {
		final FixedGridLayout layout = new FixedGridLayout(new double[] { 80, 40 }, 0, 5);
		final FixedGridLayout.Placement p = layout.place(new double[] { 10, 20, 30 });
		assertEquals(2, p.rowHeights().length);
		assertEquals(20.0, p.rowHeights()[0], 0);
		assertEquals(30.0, p.rowHeights()[1], 0);
		assertEquals(55.0, p.totalExtent(), 0); // 20+5+30

		final FixedGridLayout.Placement single = layout.place(new double[] { 7 });
		assertEquals(7.0, single.totalExtent(), 0);
		assertEquals(0.0, layout.place(new double[0]).totalExtent(), 0);
	}
}
