package jp.cssj.test.unit.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.LineCutter;
import net.zamasoft.foliojet.layout.fragment.LineCutter.Decision;

/**
 * 行境界での切断判定(orphans/widows)のテストです。
 * 行高さ10の行が並ぶテキストブロックを想定します。
 */
public class LineCutterTest extends TestCase {
	/** n行(高さ10)の lineStarts/lineEnds を作ります。 */
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
		// 切断線が底辺以下なら全体が前ページに収まる
		assertTrue(decide(4, 40, 2, 2, false) instanceof Decision.Keep);
		assertTrue(decide(4, 100, 2, 2, false) instanceof Decision.Keep);
	}

	public void testSingleLineMovesUnlessFirst() {
		// 1行だけの場合、ページ先頭でなければ全体移動
		assertTrue(decide(1, 5, 2, 2, false) instanceof Decision.Move);
		// ページ先頭なら前ページに残す(無限ループ防止)
		assertTrue(decide(1, 5, 2, 2, true) instanceof Decision.Keep);
	}

	public void testMoveWhenCutAboveFirstLine() {
		// 切断線が最初の行の底辺より上なら全体移動
		assertTrue(decide(4, 5, 1, 1, false) instanceof Decision.Move);
	}

	public void testSimpleCut() {
		// 6行、切断線30 → 3行目の後で切断(orphans=2, widows=2 を満たす)
		final Decision d = decide(6, 30, 2, 2, false);
		assertTrue(d instanceof Decision.CutAfter);
		assertEquals(2, ((Decision.CutAfter) d).lastLine());
	}

	public void testWidowsPushCutUp() {
		// 4行、切断線35 → 3行残せるが widows=2 を満たすため2行に減らす
		final Decision d = decide(4, 35, 1, 2, false);
		assertTrue(d instanceof Decision.CutAfter);
		assertEquals(1, ((Decision.CutAfter) d).lastLine());
	}

	public void testOrphansForcesMove() {
		// 4行、切断線15 → 1行しか残せず orphans=2 を満たせないため全体移動
		assertTrue(decide(4, 15, 2, 2, false) instanceof Decision.Move);
	}

	public void testWidowsUnsatisfiableMoves() {
		// 3行、切断線25、widows=3 → どこで切っても widows を満たせず全体移動
		assertTrue(decide(3, 25, 1, 3, false) instanceof Decision.Move);
	}

	public void testFirstKeepsAtLeastOneLine() {
		// ページ先頭では widows を満たせなくても最低1行を前ページに残す
		final Decision d = decide(3, 25, 1, 3, true);
		assertTrue(d instanceof Decision.CutAfter);
		assertEquals(0, ((Decision.CutAfter) d).lastLine());
	}

	public void testFirstIgnoresOrphans() {
		// ページ先頭では orphans を無視して切断できる
		final Decision d = decide(4, 15, 2, 1, true);
		assertTrue(d instanceof Decision.CutAfter);
		assertEquals(0, ((Decision.CutAfter) d).lastLine());
	}
}
