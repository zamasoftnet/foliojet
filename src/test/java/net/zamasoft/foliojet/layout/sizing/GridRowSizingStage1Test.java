package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver.GridArea;

/** row subgrid Stage 1で追加した行サイジングAPIの純粋計算テストです。 */
public class GridRowSizingStage1Test extends TestCase {

	private static void assertBitsEqual(final double[] expected, final double[] actual) {
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; ++i) {
			assertEquals("row " + i, Double.doubleToLongBits(expected[i]), Double.doubleToLongBits(actual[i]));
		}
	}

	/** 旧overloadと空の追加寄与はdoubleのbit列まで同一。 */
	public void testEmptyExtraIsBitIdentical() {
		final List<List<GridArea>> areaSets = List.of(List.of(),
				List.of(new GridArea(0, 0, 1, 1), new GridArea(1, 2, 1, 1)),
				List.of(new GridArea(0, 0, 1, 3), new GridArea(1, 0, 1, 1),
						new GridArea(1, 1, 1, 2)));
		final List<double[]> extents = List.of(new double[0], new double[] { 13.25, 7.5 },
				new double[] { 100, 11, 42 });
		final int[] rows = { 0, 4, 3 };
		final double[] gaps = { 0, 2.5, 3 };
		for (int i = 0; i < areaSets.size(); ++i) {
			assertBitsEqual(GridRowSizing.resolve(areaSets.get(i), extents.get(i), rows[i], gaps[i]),
					GridRowSizing.resolve(areaSets.get(i), extents.get(i), rows[i], gaps[i], List.of()));
		}
	}

	/** 単一行の追加寄与はitemと同じmax集約へ参加する。 */
	public void testSingleSpanExtraContribution() {
		final double[] h = GridRowSizing.resolve(List.of(new GridArea(0, 0, 1, 1)), new double[] { 12 }, 2,
				4, List.of(new GridRowSizing.Contribution(0, 1, 20),
						new GridRowSizing.Contribution(1, 1, 7)));
		assertEquals(20.0, h[0], 0);
		assertEquals(7.0, h[1], 0);
	}

	/** 跨る追加寄与はitemの後に同じ不足分配へ参加する。 */
	public void testSpanningExtraContribution() {
		final double[] h = GridRowSizing.resolve(
				List.of(new GridArea(0, 0, 1, 1), new GridArea(0, 1, 1, 1)),
				new double[] { 10, 20 }, 2, 5, List.of(new GridRowSizing.Contribution(0, 2, 55)));
		assertEquals(20.0, h[0], 0);
		assertEquals(30.0, h[1], 0);
	}
}
