package jp.cssj.test.unit.builder;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.builder.impl.RowLayoutEngine;

/**
 * 行高分配の共有核(P2-2/P2-4)のテストです。両ビルダーから純化された
 * 4分配(rowspan/グループ高/%高/表高)をボックス木なしで固定します。
 */
public class RowLayoutEngineTest extends TestCase {
	public void testGroupSizeProportional() {
		final double[] sizes = { 10, 30 };
		final double added = RowLayoutEngine.distributeGroupSize(sizes, 60);
		assertEquals(15, sizes[0], 0.01);
		assertEquals(45, sizes[1], 0.01);
		assertEquals(20, added, 0.01);
	}

	public void testGroupSizeZeroSumSplitsEvenly() {
		final double[] sizes = { 0, 0 };
		final double added = RowLayoutEngine.distributeGroupSize(sizes, 60);
		// 分母はグループ自身の行数(合計=指定高)
		assertEquals(30, sizes[0], 0.01);
		assertEquals(30, sizes[1], 0.01);
		assertEquals(60, added, 0.01);
	}

	public void testGroupSizeNoShrink() {
		final double[] sizes = { 40, 40 };
		assertEquals(0, RowLayoutEngine.distributeGroupSize(sizes, 60), 0.01);
		assertEquals(40, sizes[0], 0.01);
	}

	public void testPercentRowsConsumeRemainderInOrder() {
		final double[] sizes = { 10, 10, 10 };
		final double[] ratios = { 0.5, 0, 0.5 };
		// 表高 100、残余 30: 先頭 %行が 50-10=40 を要求するが残余 30 で打ち切り
		final double added = RowLayoutEngine.distributePercentRowSizes(sizes, ratios, 100, 30);
		assertEquals(40, sizes[0], 0.01);
		assertEquals(10, sizes[2], 0.01);
		assertEquals(30, added, 0.01);
	}

	public void testTableSizeMixedGoesToAutoRows() {
		final double[] sizes = { 20, 10 };
		final boolean[] auto = { false, true };
		RowLayoutEngine.distributeTableSize(sizes, auto, 60);
		assertEquals(20, sizes[0], 0.01);
		assertEquals(40, sizes[1], 0.01);
	}

	public void testTableSizeAllAutoScalesProportionally() {
		final double[] sizes = { 10, 30 };
		final boolean[] auto = { true, true };
		RowLayoutEngine.distributeTableSize(sizes, auto, 80);
		assertEquals(20, sizes[0], 0.01);
		assertEquals(60, sizes[1], 0.01);
	}

	public void testSpannedRowsPercentFirstThenAuto() {
		// percent-rowspan-groups fixture と同型: %行 0.5 + 自動行、
		// 連結の要求 93 に対し不足 58.2 → %行へ 29.1、自動行へ残り
		final double[] sizes = { 17.4, 17.4 };
		final boolean[] noAdj = { true, true };
		final boolean[] auto = { false, true };
		final double[] ratios = { 0.5, 0 };
		final net.zamasoft.foliojet.layout.builder.impl.Rowspan rowspan = new net.zamasoft.foliojet.layout.builder.impl.Rowspan(
				0, 2);
		rowspan.min = 93;
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(rowspan), noAdj, auto, ratios);
		assertEquals(46.5, sizes[0], 0.01);
		assertEquals(46.5, sizes[1], 0.01);
	}
}
