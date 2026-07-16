package jp.cssj.test.unit.sizing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.sizing.ColumnDistribution;
import net.zamasoft.foliojet.layout.sizing.ColumnDistribution.ColumnType;

/**
 * css-tables-3 の列幅分配のテストです。
 */
public class ColumnDistributionTest extends TestCase {
	private static final double DELTA = 1e-9;

	private static ColumnType[] types(ColumnType... types) {
		return types;
	}

	public void testAllAutoFit() {
		// 余裕がある場合、AUTO列は最大内容幅まで拡張される
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 20 }, new double[] { 30, 50 },
				types(ColumnType.AUTO, ColumnType.AUTO), 100);
		// 目標到達後の余剰20は現在幅(30:50)に比例して分配される
		assertEquals(30 + 20 * 30.0 / 80, sizes[0], DELTA);
		assertEquals(50 + 20 * 50.0 / 80, sizes[1], DELTA);
	}

	public void testAllAutoProportionalDeficit() {
		// 余剰が不足する場合、不足量に比例して分配される
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 20 }, new double[] { 30, 50 },
				types(ColumnType.AUTO, ColumnType.AUTO), 55);
		// 余剰25を不足量(20:30)で比例配分
		assertEquals(10 + 25 * 20.0 / 50, sizes[0], DELTA);
		assertEquals(20 + 25 * 30.0 / 50, sizes[1], DELTA);
	}

	public void testMinimumGuaranteed() {
		// 利用可能幅が最小合計以下でも最小幅は保証される
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 20 }, new double[] { 30, 50 },
				types(ColumnType.AUTO, ColumnType.AUTO), 15);
		assertEquals(10, sizes[0], DELTA);
		assertEquals(20, sizes[1], DELTA);
	}

	public void testPercentPriority() {
		// PERCENT列はAUTO列より先に目標まで拡張される
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 10 }, new double[] { 60, 60 },
				types(ColumnType.PERCENT, ColumnType.AUTO), 80);
		// PERCENT列が先に60へ、残り10がAUTO列へ
		assertEquals(60, sizes[0], DELTA);
		assertEquals(20, sizes[1], DELTA);
	}

	public void testConstrainedBeforeAuto() {
		// CONSTRAINED列はAUTO列より先に目標まで拡張される
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 10 }, new double[] { 40, 60 },
				types(ColumnType.CONSTRAINED, ColumnType.AUTO), 60);
		assertEquals(40, sizes[0], DELTA);
		assertEquals(20, sizes[1], DELTA);
	}

	public void testExcessGoesToAutoFirst() {
		// 全列が目標に達した後の余剰は AUTO 列に分配される
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 10 }, new double[] { 20, 20 },
				types(ColumnType.CONSTRAINED, ColumnType.AUTO), 60);
		assertEquals(20, sizes[0], DELTA);
		assertEquals(40, sizes[1], DELTA);
	}

	public void testExcessToConstrainedWhenNoAuto() {
		// AUTO列が無ければ余剰は CONSTRAINED 列へ
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 10 }, new double[] { 20, 20 },
				types(ColumnType.CONSTRAINED, ColumnType.PERCENT), 60);
		assertEquals(40, sizes[0], DELTA);
		assertEquals(20, sizes[1], DELTA);
	}

	public void testExcessEqualSplitWhenZeroWidth() {
		// 余剰分配先の列が全て幅0なら均等分配
		double[] sizes = ColumnDistribution.distribute(new double[] { 0, 0 }, new double[] { 0, 0 },
				types(ColumnType.AUTO, ColumnType.AUTO), 50);
		assertEquals(25, sizes[0], DELTA);
		assertEquals(25, sizes[1], DELTA);
	}

	public void testTargetBelowMinIgnored() {
		// 目標幅が最小幅を下回る列は縮小されない
		double[] sizes = ColumnDistribution.distribute(new double[] { 30, 10 }, new double[] { 20, 40 },
				types(ColumnType.PERCENT, ColumnType.AUTO), 70);
		assertEquals(30, sizes[0], DELTA);
		assertEquals(40, sizes[1], DELTA);
	}

	public void testEmpty() {
		double[] sizes = ColumnDistribution.distribute(new double[0], new double[0], types(), 100);
		assertEquals(0, sizes.length);
	}
}
