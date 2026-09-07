package jp.cssj.test.unit.displaylist;

import java.util.function.BiConsumer;

import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;

/** B-2c の保持観測。各欄の最大値は同時点とは限らず、ヒープ量の代用にはしない。 */
final class RowRetentionReport {
	private final int[] highWater = new int[8];
	private int emissions;
	private int changedNotifications, completions, beforeNotifications, eligibleTables;
	private int boundTreeRows, boundTreeCells;
	private int finishedTables, pendingRowsAtEnd, pendingCellsAtEnd;
	private int liveSamples;
	private long liveRowsAtEnd, liveCellsAtEnd;

	/** enduranceの表終端で採取した実ヒープ。行・セル・表・描画器への参照は保存しない。 */
	synchronized void recordTableEndHistogram(final RetentionHighWaterReportTest.LiveTableBoxes live) {
		if (live == null) return;
		++this.liveSamples;
		this.liveRowsAtEnd = Math.max(this.liveRowsAtEnd, live.rows());
		this.liveCellsAtEnd = Math.max(this.liveCellsAtEnd, live.cells());
		System.err.println("[B-2c live table boxes] rows=" + live.rows() + " cells=" + live.cells());
	}

	/** 別JVM・単一の送出適格表の終端だけで検査する。採取不能を回収成功とは扱わない。 */
	synchronized void assertLiveTableBound(final int rows, final int columns) {
		junit.framework.Assert.assertTrue("表終端のlive histogramが採取できていない: " + this,
				this.finishedTables > 0 && this.liveSamples == this.finishedTables);
		junit.framework.Assert.assertTrue("表終端の実行箱が現在頁+1単位+反復分を超過: " + this,
				this.liveRowsAtEnd <= rows && this.liveCellsAtEnd <= rows * columns);
	}

	AutoCloseable observe() throws Exception {
		return RangeOnlyInvariantTest.observe(RetainedTableBuilder.class, "retentionPlanObserver",
				(BiConsumer<String, RetainedTableBuilder>) this::record);
	}

	private synchronized void record(final String stage, final RetainedTableBuilder table) {
		if (ReplayIntent.current() != ReplayIntent.MAIN) return;
		final var s = table.rowRetention();
		final int[] counts = { s.pendingRows(), s.pendingCells(), s.boundRows(), s.boundCells(),
				s.currentPageRows(), s.currentPageCells(), s.repeatedRows(), s.repeatedCells() };
		for (int i = 0; i < counts.length; ++i) this.highWater[i] = Math.max(this.highWater[i], counts[i]);
		this.boundTreeRows = Math.max(this.boundTreeRows, s.boundRows() + s.currentPageRows() + s.repeatedRows());
		this.boundTreeCells = Math.max(this.boundTreeCells, s.boundCells() + s.currentPageCells() + s.repeatedCells());
		if (stage.equals("before-row-emission")) ++this.beforeNotifications;
		if (stage.equals("after-pass-b") && table.rowEmissionExclusions().isEmpty()) ++this.eligibleTables;
		if (stage.equals("after-table-end")) {
			++this.finishedTables;
			this.pendingRowsAtEnd = Math.max(this.pendingRowsAtEnd, s.pendingRows());
			this.pendingCellsAtEnd = Math.max(this.pendingCellsAtEnd, s.pendingCells());
		}
		if (stage.equals("after-row-emission") || stage.equals("after-row-completion")) {
			this.emissions += table.rowEmittedFragments();
			// 分割・移動などで状態が変わった通知だけ。全追記通知数ではない。
			if (stage.equals("after-row-emission")) ++this.changedNotifications;
			else ++this.completions;
		}
		if (stage.equals("after-pass-b") || stage.startsWith("after-row-")) {
			System.err.println("[B-2 row retention] " + stage + " " + s
					+ " exclusions=" + table.rowEmissionExclusions());
		}
	}

	/** 指定fixtureだけの幾何上限。全表・全入力に対するヒープ上限ではない。 */
	synchronized void assertStreamingBound(final int pageRows, final int unitRows, final int repeatedRows,
			final int columns) {
		junit.framework.Assert.assertTrue("送出適格表が未観測: " + this, this.eligibleTables > 0);
		junit.framework.Assert.assertTrue("実断片の送出が未発火: " + this, this.emissions > 0);
		junit.framework.Assert.assertTrue("完了が未観測: " + this, this.completions > 0);
		junit.framework.Assert.assertTrue("終端または未処理計画の解放が未観測: " + this,
				this.finishedTables > 0 && this.pendingRowsAtEnd == 0 && this.pendingCellsAtEnd == 0);
		junit.framework.Assert.assertTrue("通知直前の採取が不足: " + this,
				this.beforeNotifications >= this.changedNotifications + this.completions);
		final int rows = pageRows + unitRows + repeatedRows;
		junit.framework.Assert.assertTrue("bind済み木が現在頁+1単位+反復分を超過: " + this,
				this.boundTreeRows <= rows && this.boundTreeCells <= rows * columns);
	}

	@Override
	public synchronized String toString() {
		return "rowRetentionHW=" + new RetainedTableBuilder.RowRetention(this.highWater[0], this.highWater[1],
				this.highWater[2], this.highWater[3], this.highWater[4], this.highWater[5], this.highWater[6],
				this.highWater[7]) + " emittedFragments=" + this.emissions
				+ " changedNotifications=" + this.changedNotifications + " completions=" + this.completions
				+ " beforeNotifications=" + this.beforeNotifications + " eligibleTables=" + this.eligibleTables
				+ " boundTreeRowsHW=" + this.boundTreeRows + " boundTreeCellsHW=" + this.boundTreeCells
				+ " finishedTables=" + this.finishedTables + " pendingRowsAtEnd=" + this.pendingRowsAtEnd
				+ " pendingCellsAtEnd=" + this.pendingCellsAtEnd + " liveSamples=" + this.liveSamples
				+ " liveRowsAtEnd=" + this.liveRowsAtEnd + " liveCellsAtEnd=" + this.liveCellsAtEnd;
	}
}
