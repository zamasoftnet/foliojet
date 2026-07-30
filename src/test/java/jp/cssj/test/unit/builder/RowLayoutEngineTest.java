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

	// ---- A-0(2026-07-30): A-4(行窓要求の共有)に先立ち、分配カスケード
	// (%→連結でのみ拡張された自動行→自動行→全行)の各段と演算の
	// ビット厳密性を固定する特性テスト ----

	private static net.zamasoft.foliojet.layout.builder.impl.Rowspan span(final int row, final int span,
			final double min) {
		final net.zamasoft.foliojet.layout.builder.impl.Rowspan s = new net.zamasoft.foliojet.layout.builder.impl.Rowspan(
				row, span);
		s.min = min;
		return s;
	}

	public void testSpannedPrefersRowsExtendedOnlyBySpan() {
		// 中央の行だけが「連結によってのみ拡張された自動行」(noAdj=false)
		// → 不足30は全て中央へ
		final double[] sizes = { 10, 10, 10 };
		final boolean[] noAdj = { true, false, true };
		final boolean[] auto = { true, true, true };
		final double[] ratios = { 0, 0, 0 };
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(span(0, 3, 60)), noAdj, auto, ratios);
		assertEquals(10, sizes[0], 0.0);
		assertEquals(40, sizes[1], 0.0);
		assertEquals(10, sizes[2], 0.0);
	}

	public void testSpannedFallsBackToAutoRows() {
		// 連結専用行なし(全行に非連結セルあり)→ 自動行(row1)だけへ
		final double[] sizes = { 10, 10 };
		final boolean[] noAdj = { true, true };
		final boolean[] auto = { false, true };
		final double[] ratios = { 0, 0 };
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(span(0, 2, 50)), noAdj, auto, ratios);
		assertEquals(10, sizes[0], 0.0);
		assertEquals(40, sizes[1], 0.0);
	}

	public void testSpannedSpreadsOverAllRowsAsLastResort() {
		// 全行が自動(autoCount==span)は「全行へ均等」の最終段に落ちる。
		// 1/3の循環小数もビット単位で旧実装と一致すること(doubleToLongBits)
		final double[] sizes = { 10, 10, 10 };
		final boolean[] noAdj = { false, false, false };
		final boolean[] auto = { true, true, true };
		final double[] ratios = { 0, 0, 0 };
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(span(0, 3, 31)), noAdj, auto, ratios);
		final double expected = 10 + 1.0 / 3;
		for (int i = 0; i < 3; ++i) {
			assertEquals("row " + i, Double.doubleToLongBits(expected), Double.doubleToLongBits(sizes[i]));
		}
	}

	public void testSpannedWindowClippedAtTableEnd() {
		// 表末尾を越える連結(空行の打ち切り)でも配列外を触らず、
		// 実在する行だけで分配する
		final double[] sizes = { 10 };
		final boolean[] noAdj = { false };
		final boolean[] auto = { true };
		final double[] ratios = { 0 };
		RowLayoutEngine.distributeSpannedRowSizes(sizes, List.of(span(0, 2, 40)), noAdj, auto, ratios);
		assertEquals(40, sizes[0], 0.0);
	}

	public void testSpannedProcessesSortedSpansCumulatively() {
		// SPAN_COMPARATORの短い順に処理され、後続の連結は前の分配結果を
		// 前提に不足だけを埋める(累積の固定)
		final double[] sizes = { 10, 10, 10 };
		final boolean[] noAdj = { false, false, false };
		final boolean[] auto = { true, true, true };
		final double[] ratios = { 0, 0, 0 };
		final java.util.List<net.zamasoft.foliojet.layout.builder.impl.Rowspan> spans = new java.util.ArrayList<>(
				List.of(span(0, 3, 60), span(0, 2, 40)));
		spans.sort(net.zamasoft.foliojet.layout.builder.impl.Rowspan.SPAN_COMPARATOR);
		RowLayoutEngine.distributeSpannedRowSizes(sizes, spans, noAdj, auto, ratios);
		// span(0,2,40): 不足20→row0/row1へ10ずつ → {20,20,10}
		// span(0,3,60): 合計50、不足10→3行へ10/3ずつ
		final double third = 10.0 / 3;
		assertEquals(Double.doubleToLongBits(20 + third), Double.doubleToLongBits(sizes[0]));
		assertEquals(Double.doubleToLongBits(20 + third), Double.doubleToLongBits(sizes[1]));
		assertEquals(Double.doubleToLongBits(10 + third), Double.doubleToLongBits(sizes[2]));
	}
}
