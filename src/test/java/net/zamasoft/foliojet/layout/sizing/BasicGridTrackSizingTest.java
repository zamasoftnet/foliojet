package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.sizing.BasicGridTrackSizing.ItemContribution;

/**
 * {@link BasicGridTrackSizing}(Grid G3b/c、G4dでspan対応)の純粋計算
 * テストです(consult-codex-2026-07-31-grid-g3.txt Q2・-grid-g4.txt Q2の
 * 必須ケース)。
 */
public class BasicGridTrackSizingTest extends TestCase {

	private static final GridTrackListValue.TrackSize AUTO = GridTrackListValue.Auto.INSTANCE;

	private static GridTrackListValue.TrackSize fixed(final double length) {
		return new GridTrackListValue.Fixed(length);
	}

	/** 列ごとのspan1 contribution(min=max等値も可)を組み立てる。 */
	private static List<ItemContribution> perColumn(final double[] colMin, final double[] colMax) {
		final List<ItemContribution> items = new ArrayList<>();
		for (int i = 0; i < colMin.length; ++i) {
			if (colMin[i] > 0 || colMax[i] > 0) {
				items.add(new ItemContribution(i, 1, colMin[i], colMax[i]));
			}
		}
		return items;
	}

	/** fixed+auto: 残余はstretchでauto列へ(80+gap10+auto→300なら210)。 */
	public void testFixedAutoStretch() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(80), AUTO),
				perColumn(new double[] { 60, 70 }, new double[] { 60, 70 }), 300, 10);
		assertEquals(80.0, w[0], 0.001);
		assertEquals(210.0, w[1], 0.001);
	}

	/** auto二列: 成長上限まで均等成長→なお残る分は均等stretch。 */
	public void testAutoGrowthThenStretch() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO, AUTO),
				perColumn(new double[] { 40, 30 }, new double[] { 80, 30 }), 150, 0);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(50.0, w[1], 0.001);
	}

	/** 成長上限に届かない残余は上限内で止まる。 */
	public void testAutoPartialGrowth() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO),
				perColumn(new double[] { 40 }, new double[] { 80 }), 60, 0);
		assertEquals(60.0, w[0], 0.001);
	}

	/** min-content床: 利用可能幅を超えても縮めずoverflow。 */
	public void testOverflowKeepsMinContent() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO),
				perColumn(new double[] { 60 }, new double[] { 60 }), 30, 0);
		assertEquals(60.0, w[0], 0.001);

		final double[] w2 = BasicGridTrackSizing.resolve(List.of(fixed(100), fixed(100)), List.of(), 150, 10);
		assertEquals(100.0, w2[0], 0.001);
		assertEquals(100.0, w2[1], 0.001);
	}

	/** fixedのみ: 残余は分配せず末尾に残す(G1と同じ)。 */
	public void testFixedOnlyLeavesRemainder() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(100), fixed(100)), List.of(), 300, 20);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(100.0, w[1], 0.001);
	}

	/** fr比例分配と、fixed/autoとの共存(G3c)。 */
	public void testFrProportionalAndMixed() {
		final double[] w = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(1), new GridTrackListValue.Fr(2)), List.of(), 300, 0);
		assertEquals(100.0, w[0], 0.001);
		assertEquals(200.0, w[1], 0.001);

		final double[] m = BasicGridTrackSizing.resolve(List.of(fixed(60), AUTO, new GridTrackListValue.Fr(1)),
				perColumn(new double[] { 0, 30, 0 }, new double[] { 0, 50, 0 }), 300, 0);
		assertEquals(60.0, m[0], 0.001);
		assertEquals(50.0, m[1], 0.001);
		assertEquals(190.0, m[2], 0.001);
	}

	/** 単独0.5frは残余の50%だけ充填(weight合計1未満の切り上げ)。 */
	public void testFrPartialFill() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(new GridTrackListValue.Fr(0.5)), List.of(), 200, 0);
		assertEquals(100.0, w[0], 0.001);
	}

	/** frのmin-content床: 割る列は床で凍結して残余を再計算。 */
	public void testFrBaseFloorFreeze() {
		final double[] w = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(1), new GridTrackListValue.Fr(1)),
				perColumn(new double[] { 80, 0 }, new double[] { 80, 0 }), 100, 0);
		assertEquals(80.0, w[0], 0.001);
		assertEquals(20.0, w[1], 0.001);
	}

	/** fr異常系: base合計超過・幅0・weight0でNaN/負値を返さない。 */
	public void testFrDegenerateCases() {
		final double[] over = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(1), new GridTrackListValue.Fr(1)),
				perColumn(new double[] { 80, 70 }, new double[] { 80, 70 }), 100, 0);
		assertEquals(80.0, over[0], 0.001);
		assertEquals(70.0, over[1], 0.001);

		final double[] zero = BasicGridTrackSizing.resolve(List.of(new GridTrackListValue.Fr(1)), List.of(), 0, 0);
		assertEquals(0.0, zero[0], 0.001);

		final double[] w0 = BasicGridTrackSizing.resolve(
				List.of(new GridTrackListValue.Fr(0), new GridTrackListValue.Fr(1)), List.of(), 100, 0);
		assertEquals(0.0, w0[0], 0.001);
		assertEquals(100.0, w0[1], 0.001);
		for (final double v : new double[] { over[0], over[1], zero[0], w0[0], w0[1] }) {
			assertFalse(Double.isNaN(v));
			assertTrue(v >= 0);
		}
	}

	/** 空auto列(itemなし)は0起点でstretchのみ受ける。 */
	public void testEmptyAutoColumnAndZeroContainer() {
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO, AUTO),
				perColumn(new double[] { 50, 0 }, new double[] { 50, 0 }), 100, 0);
		assertEquals(75.0, w[0], 0.001);
		assertEquals(25.0, w[1], 0.001);
	}

	/** span不足分配(G4d): fixed+autoを跨ぐspanはauto側だけが伸びる。 */
	public void testSpanDeficitToAuto() {
		// [40pt auto]、span1のauto contribution 20、span2 item min=max=100
		// → 不足 100-(40+20)=40 が auto へ → base 60。intrinsics min=max=100
		final List<ItemContribution> items = List.of(new ItemContribution(1, 1, 20, 20),
				new ItemContribution(0, 2, 100, 100));
		final BasicGridTrackSizing.Intrinsics in = BasicGridTrackSizing.intrinsics(List.of(fixed(40), AUTO), items,
				0);
		assertEquals(100.0, in.min(), 0.001);
		assertEquals(100.0, in.max(), 0.001);
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(40), AUTO), items, 100, 0);
		assertEquals(40.0, w[0], 0.001);
		assertEquals(60.0, w[1], 0.001);
	}

	/** span不足分配: 内側gapは控除、同一span長は最大必要増分で反映。 */
	public void testSpanDeficitGapAndBatch() {
		// [auto auto] gap10。span2 item二つ(min 90と70)——大きい方だけが効く
		// (planned increase)。不足=90-10-0=80 → 各autoへ40
		final List<ItemContribution> items = List.of(new ItemContribution(0, 2, 90, 90),
				new ItemContribution(0, 2, 70, 70));
		final BasicGridTrackSizing.Intrinsics in = BasicGridTrackSizing.intrinsics(List.of(AUTO, AUTO), items, 10);
		assertEquals(90.0, in.min(), 0.001);
		final double[] w = BasicGridTrackSizing.resolve(List.of(AUTO, AUTO), items, 90, 10);
		assertEquals(40.0, w[0], 0.001);
		assertEquals(40.0, w[1], 0.001);
	}

	/** span不足分配: frを跨ぐ場合はfr側(weight比)へ、床として効く。 */
	public void testSpanDeficitToFr() {
		// [40pt 1fr]、span2 item min=max=100 → fr床=60。available 80 <
		// 40+60 → overflowでfr=60(床維持)
		final List<ItemContribution> items = List.of(new ItemContribution(0, 2, 100, 100));
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(40), new GridTrackListValue.Fr(1)), items, 80,
				0);
		assertEquals(40.0, w[0], 0.001);
		assertEquals(60.0, w[1], 0.001);

		// 余裕があればfrが残余を取る(床より大きい)
		final double[] wide = BasicGridTrackSizing.resolve(List.of(fixed(40), new GridTrackListValue.Fr(1)), items,
				200, 0);
		assertEquals(160.0, wide[1], 0.001);
	}

	/** fixedのみを跨ぐspanはトラックを増やさない(overflow許容)。 */
	public void testSpanOverFixedOnly() {
		final List<ItemContribution> items = List.of(new ItemContribution(0, 2, 300, 300));
		final double[] w = BasicGridTrackSizing.resolve(List.of(fixed(40), fixed(40)), items, 200, 0);
		assertEquals(40.0, w[0], 0.001);
		assertEquals(40.0, w[1], 0.001);
	}
}
