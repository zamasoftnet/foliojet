package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.sizing.FlexLineBreaker.Line;

/**
 * Pure calculation tests for line breaking (Flex F2a; validation conditions from the recommendation:
 * exact fit, oversized first item, zero-size trailing item, gaps, empty item sequence, FP error boundary).
 */
public class FlexLineBreakerTest extends TestCase {

	private static List<FlexItemMetrics> items(final double... outers) {
		final List<FlexItemMetrics> list = new ArrayList<>();
		for (int i = 0; i < outers.length; ++i) {
			list.add(new FlexItemMetrics(i, outers[i], outers[i], 0, Double.POSITIVE_INFINITY, 0, 0, 1));
		}
		return list;
	}

	private static void assertLines(final List<Line> actual, final int[]... expected) {
		assertEquals(expected.length, actual.size());
		for (int i = 0; i < expected.length; ++i) {
			assertEquals("line " + i + " from", expected[i][0], actual.get(i).from());
			assertEquals("line " + i + " to", expected[i][1], actual.get(i).to());
		}
	}

	public void testEmpty() {
		assertTrue(FlexLineBreaker.breakLines(List.of(), 100, 0).isEmpty());
	}

	public void testSingleLine() {
		assertLines(FlexLineBreaker.breakLines(items(50, 40), 100, 0), new int[] { 0, 2 });
	}

	/** An exact fit (==) stays on the same line (FP error also falls on the exact-fit side). */
	public void testExactFit() {
		assertLines(FlexLineBreaker.breakLines(items(50, 50), 100, 0), new int[] { 0, 2 });
		// 0.1×3=0.30000000000000004 > 0.3, but keep them on the same line.
		assertLines(FlexLineBreaker.breakLines(items(0.1, 0.1, 0.1), 0.3, 0), new int[] { 0, 3 });
	}

	public void testWrap() {
		assertLines(FlexLineBreaker.breakLines(items(60, 60, 60), 100, 0), new int[] { 0, 1 },
				new int[] { 1, 2 }, new int[] { 2, 3 });
		assertLines(FlexLineBreaker.breakLines(items(40, 40, 40), 100, 0), new int[] { 0, 2 },
				new int[] { 2, 3 });
	}

	/** An item that overflows by itself still gets its own line (oversized first item). */
	public void testOversizedItem() {
		assertLines(FlexLineBreaker.breakLines(items(200, 50, 40), 100, 0), new int[] { 0, 1 },
				new int[] { 1, 3 });
	}

	/** A zero-size trailing item stays on the preceding line (it does not cause overflow). */
	public void testZeroSizeTail() {
		assertLines(FlexLineBreaker.breakLines(items(100, 0, 0), 100, 0), new int[] { 0, 3 });
	}

	/** Count gaps only between items on the same line. */
	public void testGap() {
		// 40+10+40=90≦100; adding +10+40=140>100 breaks the line.
		assertLines(FlexLineBreaker.breakLines(items(40, 40, 40), 100, 10), new int[] { 0, 2 },
				new int[] { 2, 3 });
		// Without gaps, all three fit.
		assertLines(FlexLineBreaker.breakLines(items(40, 40, 40), 120, 0), new int[] { 0, 3 });
		// An exact fit including gaps (40+10+40+10+40=140) stays on the same line.
		assertLines(FlexLineBreaker.breakLines(items(40, 40, 40), 140, 10), new int[] { 0, 3 });
	}
}
