package jp.cssj.test.unit.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.LineCutter;
import net.zamasoft.foliojet.layout.fragment.LineCutter.Decision;

/**
 * Tests for cut decisions at line boundaries (orphans/widows).
 * Assume a text block consisting of lines with height 10.
 */
public class LineCutterTest extends TestCase {
	/** Create lineStarts/lineEnds for n lines (height 10). */
	private static double[][] lines(int n) {
		final double[] starts = new double[n];
		final double[] ends = new double[n];
		for (int i = 0; i < n; ++i) {
			starts[i] = i * 10;
			ends[i] = (i + 1) * 10;
		}
		return new double[][] { starts, ends };
	}

	private static Decision decide(int lineCount, double pageLimit, int orphans, int widows, boolean first) {
		final double[][] l = lines(lineCount);
		return LineCutter.decide(pageLimit, lineCount * 10, 10, orphans, widows, first, l[0], l[1]);
	}

	public void testKeepWhenFits() {
		// If the cut line is at or below the bottom, the whole block fits on the previous page.
		assertTrue(decide(4, 40, 2, 2, false) instanceof Decision.Keep);
		assertTrue(decide(4, 100, 2, 2, false) instanceof Decision.Keep);
	}

	public void testSingleLineMovesUnlessFirst() {
		// For a single line, move the whole block unless it is at the top of the page.
		assertTrue(decide(1, 5, 2, 2, false) instanceof Decision.Move);
		// At the top of the page, leave it on the previous page (prevent an infinite loop).
		assertTrue(decide(1, 5, 2, 2, true) instanceof Decision.Keep);
	}

	public void testMoveWhenCutAboveFirstLine() {
		// If the cut line is above the bottom of the first line, move the whole block.
		assertTrue(decide(4, 5, 1, 1, false) instanceof Decision.Move);
	}

	public void testSimpleCut() {
		// 6 lines, cut line 30 → cut after line 3 (satisfies orphans=2, widows=2).
		final Decision d = decide(6, 30, 2, 2, false);
		assertTrue(d instanceof Decision.CutAfter);
		assertEquals(2, ((Decision.CutAfter) d).lastLine());
	}

	public void testWidowsPushCutUp() {
		// 4 lines, cut line 35 → 3 lines fit, but reduce to 2 to satisfy widows=2.
		final Decision d = decide(4, 35, 1, 2, false);
		assertTrue(d instanceof Decision.CutAfter);
		assertEquals(1, ((Decision.CutAfter) d).lastLine());
	}

	public void testOrphansForcesMove() {
		// 4 lines, cut line 15 → only 1 line fits, violating orphans=2, so move the whole block.
		assertTrue(decide(4, 15, 2, 2, false) instanceof Decision.Move);
	}

	public void testWidowsUnsatisfiableMoves() {
		// 3 lines, cut line 25, widows=3 → no cut satisfies widows, so move the whole block.
		assertTrue(decide(3, 25, 1, 3, false) instanceof Decision.Move);
	}

	public void testFirstKeepsAtLeastOneLine() {
		// At the top of the page, leave at least 1 line on the previous page even if widows cannot be satisfied.
		final Decision d = decide(3, 25, 1, 3, true);
		assertTrue(d instanceof Decision.CutAfter);
		assertEquals(0, ((Decision.CutAfter) d).lastLine());
	}

	public void testFirstIgnoresOrphans() {
		// At the top of the page, a cut may ignore orphans.
		final Decision d = decide(4, 15, 2, 1, true);
		assertTrue(d instanceof Decision.CutAfter);
		assertEquals(0, ((Decision.CutAfter) d).lastLine());
	}
}
