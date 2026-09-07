package jp.cssj.test.unit.displaylist;

import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.impl.TableBuildPlanner.RowEmissionExclusion;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;

/** B-2c の保持観測。各欄の最大値は同時点とは限らず、ヒープ量の代用にはしない。 */
final class RowRetentionReport {
	/** 指定の存在と、実際に高さ配分が発火したことを分けて数える。 */
	enum Feature {
		TOP_CAPTION, TOP_CAPTION_PAGE_BREAK, BOTTOM_CAPTION, ABSOLUTE_GROUP_SIZE, GROUP_SIZE_GROWTH,
		ZERO_GROUP_SIZE_GROWTH, TABLE_SIZE_AFTER_GROUP
	}

	private record GroupSizes(boolean absolute, double[] rows) { }
	private static final class Features {
		final EnumSet<Feature> values = EnumSet.noneOf(Feature.class);
		final EnumSet<RowEmissionExclusion> exclusions = EnumSet.noneOf(RowEmissionExclusion.class);
		List<GroupSizes> before, after;
		WeakReference<Object> pageBeforeTop;
		int emissions, completions;
	}
	// 観測のために表・行・セル木の寿命を延ばさない。寸法スナップショットもPass B後に捨てる。
	private final Map<RetainedTableBuilder, Features> features = new WeakHashMap<>();
	private final EnumMap<RowEmissionExclusion, Integer> exclusions = new EnumMap<>(RowEmissionExclusion.class);
	private final EnumMap<Feature, Integer> observedFeatures = new EnumMap<>(Feature.class);
	private final EnumMap<Feature, Integer> emittedFeatures = new EnumMap<>(Feature.class);
	private final EnumMap<Feature, Integer> emittingTables = new EnumMap<>(Feature.class);
	private final EnumMap<Feature, Integer> completedFeatures = new EnumMap<>(Feature.class);
	private final int[] highWater = new int[8];
	private int emissions;
	private int changedNotifications, completions, beforeNotifications, eligibleTables;
	private int boundTreeRows, boundTreeCells;
	private int streamingTreeRows, streamingTreeCells;
	private int finishedTables, pendingRowsAtEnd, pendingCellsAtEnd;
	private int liveSamples;
	private long liveRowsAtEnd, liveCellsAtEnd;

	RowRetentionReport() {
		for (final RowEmissionExclusion reason : RowEmissionExclusion.values()) this.exclusions.put(reason, 0);
	}

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

	synchronized void record(final String stage, final RetainedTableBuilder table) {
		if (ReplayIntent.current() != ReplayIntent.MAIN) return;
		if (stage.equals("after-pass-b") || stage.equals("after-table-end")) {
			// 初回判定後に受理宿主が不適格になった理由も、表ごとに一度だけ数える。
			final Features state = this.features.get(table);
			for (final RowEmissionExclusion reason : table.rowEmissionExclusions()) {
				if (state == null || state.exclusions.add(reason)) this.exclusions.merge(reason, 1, Integer::sum);
			}
		}
		this.recordFeatures(stage, table);
		final var s = table.rowRetention();
		final int[] counts = { s.pendingRows(), s.pendingCells(), s.boundRows(), s.boundCells(),
				s.currentPageRows(), s.currentPageCells(), s.repeatedRows(), s.repeatedCells() };
		for (int i = 0; i < counts.length; ++i) this.highWater[i] = Math.max(this.highWater[i], counts[i]);
		this.boundTreeRows = Math.max(this.boundTreeRows, s.boundRows() + s.currentPageRows() + s.repeatedRows());
		this.boundTreeCells = Math.max(this.boundTreeCells, s.boundCells() + s.currentPageCells() + s.repeatedCells());
		if (stage.equals("before-row-emission") || stage.equals("after-row-emission")
				|| stage.equals("after-row-completion")) {
			// 完成経路の表は全行を保持する。送出の上限は受理・追記・完了の通知で測る。
			this.streamingTreeRows = Math.max(this.streamingTreeRows,
					s.boundRows() + s.currentPageRows() + s.repeatedRows());
			this.streamingTreeCells = Math.max(this.streamingTreeCells,
					s.boundCells() + s.currentPageCells() + s.repeatedCells());
		}
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

	private void recordFeatures(final String stage, final RetainedTableBuilder table) {
		if (stage.equals("before-top-captions")) {
			final Features state = new Features();
			state.pageBeforeTop = new WeakReference<>(page(table));
			this.features.put(table, state);
			return;
		}
		if (stage.equals("before-group-page-size")) {
			final Features state = this.features.computeIfAbsent(table, key -> new Features());
			state.before = groupSizes(table);
			return;
		}
		final Features state = this.features.get(table);
		if (stage.equals("after-top-captions") && state != null) {
			if (!((List<?>) RangeOnlyInvariantTest.field(table, "topCaptions")).isEmpty()
					&& state.pageBeforeTop.get() != page(table)) state.values.add(Feature.TOP_CAPTION_PAGE_BREAK);
			state.pageBeforeTop = null;
		} else if (stage.equals("after-group-page-size") && state != null) {
			state.after = groupSizes(table);
			for (int i = 0; i < state.before.size(); ++i) {
				final GroupSizes before = state.before.get(i);
				if (!before.absolute()) continue;
				state.values.add(Feature.ABSOLUTE_GROUP_SIZE);
				if (grew(before.rows(), state.after.get(i).rows())) {
					state.values.add(Feature.GROUP_SIZE_GROWTH);
					boolean zero = true;
					for (final double size : before.rows()) zero &= size == 0;
					if (zero) state.values.add(Feature.ZERO_GROUP_SIZE_GROWTH);
				}
			}
			state.before = null;
		} else if (stage.equals("after-pass-b")) {
			if (state == null) return;
			if (!((List<?>) RangeOnlyInvariantTest.field(table, "topCaptions")).isEmpty()) {
				state.values.add(Feature.TOP_CAPTION);
			}
			if (!((List<?>) RangeOnlyInvariantTest.field(table, "bottomCaptions")).isEmpty()) {
				state.values.add(Feature.BOTTOM_CAPTION);
			}
			if (state.values.contains(Feature.GROUP_SIZE_GROWTH)) {
				final List<GroupSizes> last = groupSizes(table);
				for (int i = 0; i < last.size(); ++i) {
					if (grew(state.after.get(i).rows(), last.get(i).rows())) {
						state.values.add(Feature.TABLE_SIZE_AFTER_GROUP);
					}
				}
			}
			state.after = null;
			for (final Feature feature : state.values) this.observedFeatures.merge(feature, 1, Integer::sum);
		} else if (state != null && (stage.equals("after-row-emission") || stage.equals("after-row-completion"))) {
			state.emissions += table.rowEmittedFragments();
			if (stage.equals("after-row-completion")) {
				junit.framework.Assert.assertEquals("表の完了は1回", 1, ++state.completions);
				if (state.emissions > 0) {
					for (final Feature feature : state.values) this.emittingTables.merge(feature, 1, Integer::sum);
				}
			}
			for (final Feature feature : state.values) {
				this.emittedFeatures.merge(feature, table.rowEmittedFragments(), Integer::sum);
				if (stage.equals("after-row-completion")) this.completedFeatures.merge(feature, 1, Integer::sum);
			}
		} else if (stage.equals("after-table-end")) {
			this.features.remove(table);
		}
	}

	private static Object page(final RetainedTableBuilder table) {
		return ((LayoutStack) RangeOnlyInvariantTest.field(table, "layoutStack")).getPageContext().getRootBox();
	}

	private static boolean grew(final double[] before, final double[] after) {
		for (int i = 0; i < before.length; ++i) if (after[i] > before[i]) return true;
		return false;
	}

	/** 計算済みの実行高を読むだけ。配分アルゴリズムを観測側に複製しない。 */
	static List<double[]> rowSizes(final RetainedTableBuilder table) {
		final List<double[]> sizes = new ArrayList<>();
		for (final GroupSizes group : groupSizes(table)) sizes.add(group.rows());
		return sizes;
	}

	@SuppressWarnings("unchecked")
	private static List<GroupSizes> groupSizes(final RetainedTableBuilder table) {
		final var groups = (List<TableRowGroupBox>) RangeOnlyInvariantTest.field(table, "rowGroups");
		final var rows = (Map<TableRowGroupBox, List<TableRowBox>>)
				RangeOnlyInvariantTest.field(table, "rowGroupToRows");
		final List<GroupSizes> sizes = new ArrayList<>();
		for (final TableRowGroupBox group : groups) {
			final List<TableRowBox> groupRows = rows.get(group);
			final double[] values = new double[groupRows.size()];
			for (int i = 0; i < values.length; ++i) values[i] = groupRows.get(i).getPageSize();
			sizes.add(new GroupSizes(group.getInnerTableParams().size.getType() == LengthType.ABSOLUTE, values));
		}
		return sizes;
	}

	/** 分岐の観測数と送出表数は別。別の除外理由を持つ表も配分・caption配置は通る。 */
	synchronized void assertFeature(final Feature feature, final int tables, final int streamingTables) {
		junit.framework.Assert.assertEquals("対象分岐の発火数: " + this, tables,
				this.observedFeatures.getOrDefault(feature, 0).intValue());
		junit.framework.Assert.assertEquals("対象の実送出有無: " + this, streamingTables > 0,
				this.emittedFeatures.getOrDefault(feature, 0) > 0);
		junit.framework.Assert.assertEquals("各対象表の実送出: " + this, streamingTables,
				this.emittingTables.getOrDefault(feature, 0).intValue());
		junit.framework.Assert.assertEquals("対象の完了回数: " + this, streamingTables,
				this.completedFeatures.getOrDefault(feature, 0).intValue());
	}

	synchronized void assertTableCounts(final int tables, final int streamingTables) {
		junit.framework.Assert.assertEquals("全表の終端: " + this, tables, this.finishedTables);
		junit.framework.Assert.assertEquals("送出表の完了: " + this, streamingTables, this.completions);
		junit.framework.Assert.assertEquals("実送出有無: " + this, streamingTables > 0, this.emissions > 0);
		junit.framework.Assert.assertEquals("終端の未処理行: " + this, 0, this.pendingRowsAtEnd);
		junit.framework.Assert.assertEquals("終端の未処理セル: " + this, 0, this.pendingCellsAtEnd);
	}

	synchronized int exclusionCount(final RowEmissionExclusion reason) {
		return this.exclusions.getOrDefault(reason, 0);
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
				this.streamingTreeRows <= rows && this.streamingTreeCells <= rows * columns);
	}

	@Override
	public synchronized String toString() {
		return "rowRetentionHW=" + new RetainedTableBuilder.RowRetention(this.highWater[0], this.highWater[1],
				this.highWater[2], this.highWater[3], this.highWater[4], this.highWater[5], this.highWater[6],
				this.highWater[7]) + " emittedFragments=" + this.emissions
				+ " changedNotifications=" + this.changedNotifications + " completions=" + this.completions
				+ " beforeNotifications=" + this.beforeNotifications + " eligibleTables=" + this.eligibleTables
				+ " boundTreeRowsHW=" + this.boundTreeRows + " boundTreeCellsHW=" + this.boundTreeCells
				+ " streamingTreeRowsHW=" + this.streamingTreeRows + " streamingTreeCellsHW=" + this.streamingTreeCells
				+ " finishedTables=" + this.finishedTables + " pendingRowsAtEnd=" + this.pendingRowsAtEnd
				+ " pendingCellsAtEnd=" + this.pendingCellsAtEnd + " liveSamples=" + this.liveSamples
				+ " liveRowsAtEnd=" + this.liveRowsAtEnd + " liveCellsAtEnd=" + this.liveCellsAtEnd
				+ " exclusions=" + this.exclusions + " features=" + this.observedFeatures
				+ " featureEmissions=" + this.emittedFeatures + " featureEmittingTables=" + this.emittingTables
				+ " featureCompletions=" + this.completedFeatures;
	}
}
