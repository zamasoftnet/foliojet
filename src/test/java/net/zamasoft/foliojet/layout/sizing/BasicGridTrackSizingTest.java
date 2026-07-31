package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.GridTrackListValue;

/**
 * {@link BasicGridTrackSizing}(Grid G3b)の純粋計算テストです
 * (consult-codex-2026-07-31-grid-g3.txt Q2の必須ケース)。
 */
public class BasicGridTrackSizingTest extends TestCase {

	private static final GridTrackListValue.TrackSize AUTO = GridTrackListValue.Auto.INSTANCE;

	private static GridTrackListValue.TrackSize fixed(final double length) {
		return new GridTrackListValue.Fixed(length);
	}

	/** fixed+auto: 残余はstretchでauto列へ(80+gap10+auto→300なら210)。 */
	public void testFixedAutoStretch() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(80), AUTO), new double[] { 60, 70 },
				new double[] { 60, 70 }, 300, 10);
		assertEquals(80.0, w[0], 0.001);
		assertEquals(210.0, w[1], 0.001);
	}

	/** auto二列: 成長上限まで均等成長→なお残る分は均等stretch。 */
	public void testAutoGrowthThenStretch() {
		// base 40+30=70, free 80。col0はlimit80まで+40成長、col1は飽和。
		// 残り40を均等stretchで+20ずつ → 100, 50
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO, AUTO), new double[] { 40, 30 },
				new double[] { 80, 30 }, 150, 0);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(50.0, w[1], 0.001);
	}

	/** 成長上限に届かない残余は上限内で止まる。 */
	public void testAutoPartialGrowth() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO), new double[] { 40 }, new double[] { 80 },
				60, 0);
		assertEquals(60.0, w[0], 0.001);
	}

	/** min-content床: 利用可能幅を超えても縮めずoverflow。 */
	public void testOverflowKeepsMinContent() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO), new double[] { 60 }, new double[] { 60 },
				30, 0);
		assertEquals(60.0, w[0], 0.001);

		// fixed合計超過も縮めない
		final double[] w2 = BasicGridTrackSizing.resolve(List.of(fixed(100), fixed(100)), new double[2],
				new double[2], 150, 10);
		assertEquals(100.0, w2[0], 0.001);
		assertEquals(100.0, w2[1], 0.001);
	}

	/** fixedのみ: 残余は分配せず末尾に残す(G1と同じ)。 */
	public void testFixedOnlyLeavesRemainder() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(100), fixed(100)), new double[2],
				new double[2], 300, 20);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(100.0, w[1], 0.001);
	}

	/** 空auto列(itemなし)は0起点でstretchのみ受ける。幅0コンテナも安全。 */
	public void testEmptyAutoColumnAndZeroContainer() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO, AUTO), new double[] { 50, 0 },
				new double[] { 50, 0 }, 100, 0);
		assertEquals(75.0, w[0], 0.001);
		assertEquals(25.0, w[1], 0.001);

		final double[] z = BasicGridTrackSizing.resolve(List.of(AUTO), new double[] { 0 }, new double[] { 0 }, 0,
				0);
		assertEquals(0.0, z[0], 0.001);
		assertFalse(Double.isNaN(z[0]));
	}
}
