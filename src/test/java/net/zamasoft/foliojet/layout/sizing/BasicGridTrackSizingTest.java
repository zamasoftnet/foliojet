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

	/** fr比例分配と、fixed/autoとの共存(G3c)。 */
	public void testFrProportionalAndMixed() {
		// 1fr 2fr → 100/200
		final double[] w = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(1), new GridTrackListValue.Fr(2)), new double[2], new double[2],
				300, 0);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(200.0, w[1], 0.001);

		// fixed 60 + auto(max50) + 1fr、gap0、W=300 → 60/50/190
		final double[] m = BasicGridTrackSizing.resolve(
				List.of(fixed(60), AUTO, new GridTrackListValue.Fr(1)), new double[] { 0, 30, 0 },
				new double[] { 0, 50, 0 }, 300, 0);
		assertEquals(60.0, m[0], 0.001);
		assertEquals(50.0, m[1], 0.001);
		assertEquals(190.0, m[2], 0.001);
	}

	/** 単独0.5frは残余の50%だけ充填(weight合計1未満の切り上げ)。 */
	public void testFrPartialFill() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(new GridTrackListValue.Fr(0.5)), new double[] { 0 },
				new double[] { 0 }, 200, 0);
		assertEquals(100.0, w[0], 0.001);
	}

	/** frのmin-content床: 割る列は床で凍結して残余を再計算。 */
	public void testFrBaseFloorFreeze() {
		// 1fr 1fr、W=100、col0のmin=80 → col0=80、col1=20
		final double[] w = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(1), new GridTrackListValue.Fr(1)), new double[] { 80, 0 },
				new double[] { 80, 0 }, 100, 0);
		assertEquals(80.0, w[0], 0.001);
		assertEquals(20.0, w[1], 0.001);
	}

	/** fr異常系: base合計超過・幅0・weight0でNaN/負値を返さない。 */
	public void testFrDegenerateCases() {
		// base合計(80+70)がW=100を超過 → 床のままoverflow
		final double[] over = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(1), new GridTrackListValue.Fr(1)), new double[] { 80, 70 },
				new double[] { 80, 70 }, 100, 0);
		assertEquals(80.0, over[0], 0.001);
		assertEquals(70.0, over[1], 0.001);

		// W=0
		final double[] zero = BasicGridTrackSizing.resolve(List.of(new GridTrackListValue.Fr(1)),
				new double[] { 0 }, new double[] { 0 }, 0, 0);
		assertEquals(0.0, zero[0], 0.001);

		// weight 0(床なし→0、床あり→床)
		final double[] w0 = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(0), new GridTrackListValue.Fr(1)), new double[] { 0, 0 },
				new double[] { 0, 0 }, 100, 0);
		assertEquals(0.0, w0[0], 0.001);
		assertEquals(100.0, w0[1], 0.001);
		for (final double v : new double[] { over[0], over[1], zero[0], w0[0], w0[1] }) {
			assertFalse(Double.isNaN(v));
			assertTrue(v >= 0);
		}
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
