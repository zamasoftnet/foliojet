package jp.cssj.test.unit.sizing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.sizing.AutoColumnWidths;

/**
 * 自動レイアウトの列幅解決(P2-4)のテストです。TwoPass prepareLayout
 * から純化された列型ラダーと colspan 分配を、ボックス木なしで固定します。
 */
public class AutoColumnWidthsTest extends TestCase {
	public void testSingleAutoColumn() {
		final AutoColumnWidths w = new AutoColumnWidths(1);
		w.cell(0, 1, 10, 50, AutoColumnWidths.COLUMN_TYPE_DES, 50);
		final AutoColumnWidths.Result r = w.finish(4);
		assertEquals(10, r.mins()[0], 0.01);
		assertEquals(50, r.desired()[0], 0.01);
		assertEquals(AutoColumnWidths.COLUMN_TYPE_DES, r.types()[0]);
		assertEquals(14, r.minLineSize(), 0.01);
		assertEquals(54, r.maxLineSize(), 0.01);
	}

	public void testLadderFixBeatsDes() {
		final AutoColumnWidths w = new AutoColumnWidths(1);
		w.cell(0, 1, 10, 50, AutoColumnWidths.COLUMN_TYPE_DES, 50);
		w.cell(0, 1, 10, 30, AutoColumnWidths.COLUMN_TYPE_FIX, 80);
		final AutoColumnWidths.Result r = w.finish(0);
		assertEquals(AutoColumnWidths.COLUMN_TYPE_FIX, r.types()[0]);
		assertEquals(80, r.specs()[0], 0.01);
		// 固定列の desired は max(min, spec)
		assertEquals(80, r.desired()[0], 0.01);
	}

	public void testLadderPercentBeatsFix() {
		final AutoColumnWidths w = new AutoColumnWidths(1);
		w.cell(0, 1, 10, 30, AutoColumnWidths.COLUMN_TYPE_FIX, 80);
		w.cell(0, 1, 10, 30, AutoColumnWidths.COLUMN_TYPE_PCT, 0.4);
		final AutoColumnWidths.Result r = w.finish(0);
		assertEquals(AutoColumnWidths.COLUMN_TYPE_PCT, r.types()[0]);
		assertEquals(0.4, r.specs()[0], 0.001);
	}

	public void testColgroupFixedSpansColumns() {
		final AutoColumnWidths w = new AutoColumnWidths(2);
		w.specFixed(0, 2, 40);
		final AutoColumnWidths.Result r = w.finish(0);
		assertEquals(AutoColumnWidths.COLUMN_TYPE_FIX, r.types()[0]);
		assertEquals(AutoColumnWidths.COLUMN_TYPE_FIX, r.types()[1]);
		assertEquals(40, r.specs()[0], 0.01);
		assertEquals(40, r.specs()[1], 0.01);
	}

	public void testColspanFixedDistributedByDesiredRatio() {
		final AutoColumnWidths w = new AutoColumnWidths(2);
		w.cell(0, 1, 0, 10, AutoColumnWidths.COLUMN_TYPE_DES, 10);
		w.cell(1, 1, 0, 30, AutoColumnWidths.COLUMN_TYPE_DES, 30);
		w.cell(0, 2, 0, 40, AutoColumnWidths.COLUMN_TYPE_FIX, 100);
		final AutoColumnWidths.Result r = w.finish(0);
		// 不足 60 を desired 比 1:3 で分配
		assertEquals(25, r.desired()[0], 0.01);
		assertEquals(75, r.desired()[1], 0.01);
	}

	public void testColspanMinDistributed() {
		final AutoColumnWidths w = new AutoColumnWidths(2);
		w.cell(0, 1, 10, 10, AutoColumnWidths.COLUMN_TYPE_DES, 10);
		w.cell(1, 1, 10, 10, AutoColumnWidths.COLUMN_TYPE_DES, 10);
		w.cell(0, 2, 60, 60, AutoColumnWidths.COLUMN_TYPE_DES, 60);
		final AutoColumnWidths.Result r = w.finish(0);
		// 連結の最小 60 > 合計 20: des-min 余地ゼロのため des 比で均等分配
		assertEquals(30, r.mins()[0], 0.01);
		assertEquals(30, r.mins()[1], 0.01);
		assertEquals(60, r.minLineSize(), 0.01);
	}

	public void testPercentClampToRemainder() {
		final AutoColumnWidths w = new AutoColumnWidths(2);
		w.cell(0, 1, 0, 10, AutoColumnWidths.COLUMN_TYPE_PCT, 0.7);
		w.cell(1, 1, 0, 10, AutoColumnWidths.COLUMN_TYPE_PCT, 0.6);
		final AutoColumnWidths.Result r = w.finish(0);
		// パーセントは残余に制限される(合計 100% を超えない)
		assertEquals(0.7, r.specs()[0], 0.001);
		assertEquals(0.3, r.specs()[1], 0.001);
	}
}
