package jp.cssj.test.unit.builder;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.builder.impl.RowLayoutEngine;

/**
 * Tests the shared row-height distribution core (P2-2/P2-4).
 * Verifies the four pure distributions extracted from both builders
 * (rowspan/group height/percentage height/table height), without a box tree.
 */
public class RowLayoutEngineTest extends TestCase {
	public void testGroupSizeProportional() {
		final double[] sizes = { 10, 30 };
		final double added = RowLayoutEngine.distributeGroupSize(sizes, 60);
		assertEquals(15, sizes[0], 0.01);
		assertEquals(45, sizes[1], 0.01);
		assertEquals(20, added, 0.01);
	}

	public void testGroupSizeZeroSumSplitsEvenly() {
		final double[] sizes = { 0, 0 };
		final double added = RowLayoutEngine.distributeGroupSize(sizes, 60);
		// The denominator is the group's own row count (total = specified height).
		assertEquals(30, sizes[0], 0.01);
		assertEquals(30, sizes[1], 0.01);
		assertEquals(60, added, 0.01);
	}

	public void testGroupSizeNoShrink() {
		final double[] sizes = { 40, 40 };
		assertEquals(0, RowLayoutEngine.distributeGroupSize(sizes, 60), 0.01);
		assertEquals(40, sizes[0], 0.01);
	}

	public void testPercentRowsConsumeRemainderInOrder() {
		final double[] sizes = { 10, 10, 10 };
		final double[] ratios = { 0.5, 0, 0.5 };
		// Table height 100, remaining 30: the first percentage row requests 50-10=40, capped at the remaining 30.
		final double added = RowLayoutEngine.distributePercentRowSizes(sizes, ratios, 100, 30);
		assertEquals(40, sizes[0], 0.01);
		assertEquals(10, sizes[2], 0.01);
		assertEquals(30, added, 0.01);
	}

	public void testTableSizeMixedGoesToAutoRows() {
		final double[] sizes = { 20, 10 };
		final boolean[] auto = { false, true };
		RowLayoutEngine.distributeTableSize(sizes, auto, 60);
		assertEquals(20, sizes[0], 0.01);
		assertEquals(40, sizes[1], 0.01);
	}

	public void testTableSizeAllAutoScalesProportionally() {
		final double[] sizes = { 10, 30 };
		final boolean[] auto = { true, true };
		RowLayoutEngine.distributeTableSize(sizes, auto, 80);
		assertEquals(20, sizes[0], 0.01);
		assertEquals(60, sizes[1], 0.01);
	}

	public void testSpannedRowsPercentFirstThenAuto() {
		// Same configuration as the percent-rowspan-groups fixture: percentage row 0.5 + auto row;
		// a span requests 93, leaving a shortfall of 58.2 → 29.1 to the percentage row, the rest to the auto row.
		final double[] sizes = { 17.4, 17.4 };
		final boolean[] noAdj = { true, true };
		final boolean[] auto = { false, true };
		final double[] ratios = { 0.5, 0 };
		final net.zamasoft.foliojet.layout.builder.impl.Rowspan rowspan = new net.zamasoft.foliojet.layout.builder.impl.Rowspan(
				0, 2);
		rowspan.min = 93;
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(rowspan), noAdj, auto, ratios);
		assertEquals(46.5, sizes[0], 0.01);
		assertEquals(46.5, sizes[1], 0.01);
	}

	// ---- A-0 (2026-07-30): Ahead of A-4 (sharing row-window requirements), characterization tests fix
	// each stage of the distribution cascade (percentage → auto rows expanded only by spans → auto rows → all rows)
	// and the bit-exactness of its arithmetic. ----

	private static net.zamasoft.foliojet.layout.builder.impl.Rowspan span(final int row, final int span,
			final double min) {
		final net.zamasoft.foliojet.layout.builder.impl.Rowspan s = new net.zamasoft.foliojet.layout.builder.impl.Rowspan(
				row, span);
		s.min = min;
		return s;
	}

	public void testSpannedPrefersRowsExtendedOnlyBySpan() {
		// Only the middle row is an "auto row expanded solely by spans" (noAdj=false),
		// so all of the 30 shortfall goes to the middle row.
		final double[] sizes = { 10, 10, 10 };
		final boolean[] noAdj = { true, false, true };
		final boolean[] auto = { true, true, true };
		final double[] ratios = { 0, 0, 0 };
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(span(0, 3, 60)), noAdj, auto, ratios);
		assertEquals(10, sizes[0], 0.0);
		assertEquals(40, sizes[1], 0.0);
		assertEquals(10, sizes[2], 0.0);
	}

	public void testSpannedFallsBackToAutoRows() {
		// No span-only row (all rows have non-spanning cells) → only the auto row (row1) receives it.
		final double[] sizes = { 10, 10 };
		final boolean[] noAdj = { true, true };
		final boolean[] auto = { false, true };
		final double[] ratios = { 0, 0 };
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(span(0, 2, 50)), noAdj, auto, ratios);
		assertEquals(10, sizes[0], 0.0);
		assertEquals(40, sizes[1], 0.0);
	}

	public void testSpannedSpreadsOverAllRowsAsLastResort() {
		// When all rows are auto (autoCount==span), fall through to the final "equal distribution to all rows" stage.
		// Even the repeating decimal 1/3 must match the old implementation bit for bit (doubleToLongBits).
		final double[] sizes = { 10, 10, 10 };
		final boolean[] noAdj = { false, false, false };
		final boolean[] auto = { true, true, true };
		final double[] ratios = { 0, 0, 0 };
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(span(0, 3, 31)), noAdj, auto, ratios);
		final double expected = 10 + 1.0 / 3;
		for (int i = 0; i < 3; ++i) {
			assertEquals("row " + i, Double.doubleToLongBits(expected), Double.doubleToLongBits(sizes[i]));
		}
	}

	public void testSpannedWindowClippedAtTableEnd() {
		// Even if a span extends past the table end (empty rows truncated), do not access outside the array;
		// distribute only among existing rows.
		final double[] sizes = { 10 };
		final boolean[] noAdj = { false };
		final boolean[] auto = { true };
		final double[] ratios = { 0 };
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(span(0, 2, 40)), noAdj, auto, ratios);
		assertEquals(40, sizes[0], 0.0);
	}

	public void testSpannedProcessesSortedSpansCumulatively() {
		// Process spans shortest first via SPAN_COMPARATOR. Later spans use earlier distribution results
		// and fill only the shortfall (verifies cumulative behavior).
		final double[] sizes = { 10, 10, 10 };
		final boolean[] noAdj = { false, false, false };
		final boolean[] auto = { true, true, true };
		final double[] ratios = { 0, 0, 0 };
		final java.util.List<net.zamasoft.foliojet.layout.builder.impl.Rowspan> spans = new java.util.ArrayList<>(
				List.of(span(0, 3, 60), span(0, 2, 40)));
		spans.sort(net.zamasoft.foliojet.layout.builder.impl.Rowspan.SPAN_COMPARATOR);
		RowLayoutEngine.distributeSpannedRowSizes(sizes, spans, noAdj, auto, ratios);
		// span(0,2,40): shortfall 20 → 10 each to row0/row1 → {20,20,10}.
		// span(0,3,60): total 50, shortfall 10 → 10/3 to each of 3 rows.
		final double third = 10.0 / 3;
		assertEquals(Double.doubleToLongBits(20 + third), Double.doubleToLongBits(sizes[0]));
		assertEquals(Double.doubleToLongBits(20 + third), Double.doubleToLongBits(sizes[1]));
		assertEquals(Double.doubleToLongBits(10 + third), Double.doubleToLongBits(sizes[2]));
	}
}
