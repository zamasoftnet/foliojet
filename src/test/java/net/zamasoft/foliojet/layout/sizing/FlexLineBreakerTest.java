package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.sizing.FlexLineBreaker.Line;

/**
 * 行分割の純粋計算テストです(Flex F2a——答申の検証条件: exact fit、
 * 1個目oversize、zero-size末尾、gap、空item列、FP誤差境界)。
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

	/** ちょうど収まる(==)は同じ行(FP誤差もexact側へ)。 */
	public void testExactFit() {
		assertLines(FlexLineBreaker.breakLines(items(50, 50), 100, 0), new int[] { 0, 2 });
		// 0.1×3=0.30000000000000004 > 0.3 だが同一行に残す
		assertLines(FlexLineBreaker.breakLines(items(0.1, 0.1, 0.1), 0.3, 0), new int[] { 0, 3 });
	}

	public void testWrap() {
		assertLines(FlexLineBreaker.breakLines(items(60, 60, 60), 100, 0), new int[] { 0, 1 },
				new int[] { 1, 2 }, new int[] { 2, 3 });
		assertLines(FlexLineBreaker.breakLines(items(40, 40, 40), 100, 0), new int[] { 0, 2 },
				new int[] { 2, 3 });
	}

	/** 単体で超過するitemも自分の行を持つ(1個目oversize)。 */
	public void testOversizedItem() {
		assertLines(FlexLineBreaker.breakLines(items(200, 50, 40), 100, 0), new int[] { 0, 1 },
				new int[] { 1, 3 });
	}

	/** zero-sizeの末尾itemは前の行に残る(超過しないため)。 */
	public void testZeroSizeTail() {
		assertLines(FlexLineBreaker.breakLines(items(100, 0, 0), 100, 0), new int[] { 0, 3 });
	}

	/** gapは行内のitem間にだけ数える。 */
	public void testGap() {
		// 40+10+40=90≦100、+10+40=140>100で切る
		assertLines(FlexLineBreaker.breakLines(items(40, 40, 40), 100, 10), new int[] { 0, 2 },
				new int[] { 2, 3 });
		// gapがなければ3個収まる
		assertLines(FlexLineBreaker.breakLines(items(40, 40, 40), 120, 0), new int[] { 0, 3 });
		// gap込みでちょうど(40+10+40+10+40=140)は同じ行
		assertLines(FlexLineBreaker.breakLines(items(40, 40, 40), 140, 10), new int[] { 0, 3 });
	}
}
