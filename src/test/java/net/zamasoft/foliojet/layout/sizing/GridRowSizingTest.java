package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver.GridArea;

/**
 * {@link GridRowSizing}(Grid G4d)の純粋計算テストです。
 */
public class GridRowSizingTest extends TestCase {

	/** rowSpan1のみ: 行内max。空行は0。 */
	public void testSingleSpanRows() {
		final double[] h = GridRowSizing.resolve(List.of(new GridArea(0, 0, 1, 1), new GridArea(1, 0, 1, 1),
				new GridArea(0, 2, 1, 1)), new double[] { 20, 30, 10 }, 3, 5);
		assertEquals(30.0, h[0], 0.001);
		assertEquals(0.0, h[1], 0.001);
		assertEquals(10.0, h[2], 0.001);
	}

	/** rowSpanの不足は各行へ均等加算(不足/rowSpan)。 */
	public void testRowSpanDeficit() {
		// 行0=20(q)、行1=15(r)。span2のp=50: 不足=50-0-35=15 → 各行+7.5
		final double[] h = GridRowSizing.resolve(List.of(new GridArea(0, 0, 1, 2), new GridArea(1, 0, 1, 1),
				new GridArea(1, 1, 1, 1)), new double[] { 50, 20, 15 }, 2, 0);
		assertEquals(27.5, h[0], 0.001);
		assertEquals(22.5, h[1], 0.001);
	}

	/** 内側rowGapは不足から控除。足りていれば行高は変わらない。 */
	public void testRowSpanGapAndNoDeficit() {
		// gap10、行0=20、行1=30。span2=55 → 不足=55-10-50=-5 → 変化なし
		final double[] h = GridRowSizing.resolve(List.of(new GridArea(0, 0, 1, 2), new GridArea(1, 0, 1, 1),
				new GridArea(1, 1, 1, 1)), new double[] { 55, 20, 30 }, 2, 10);
		assertEquals(20.0, h[0], 0.001);
		assertEquals(30.0, h[1], 0.001);
	}

	/** 同一span長のitemはplanned increase(最大必要増分)でまとめて反映。 */
	public void testSameSpanBatch() {
		// 二つのspan2(60と40)が同じ行対を跨ぐ——大きい方だけが効く(+30ずつ)
		final double[] h = GridRowSizing.resolve(
				List.of(new GridArea(0, 0, 1, 2), new GridArea(1, 0, 1, 2)), new double[] { 60, 40 }, 2, 0);
		assertEquals(30.0, h[0], 0.001);
		assertEquals(30.0, h[1], 0.001);
	}
}
