package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.builder.impl.TableBuildStats;
import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.TableRetentionReason;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionSnapshot;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.ua.SelectorFacts;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 保持系(RetainedTableBuilder / TwoPassBlockBuilder / LayoutSource /
 * SelectorFacts)の保持量high-waterのレポートテストです(E-6増分1、
 * 2026-07-24。spillableテープ基盤のspill閾値・対象選定の実測基盤——
 * docs/consultations/consult-e6-spillable-tape-codex.md §3-1)。
 *
 * <p>
 * TableBuildCharacterizationTestと同じ「空虚な緑の防止」: 観測カウンタが
 * 実際に配線されている(既知の文書で発火する)ことを固定し、あわせて
 * 現在値をstderrへレポートする。範囲再生の保持量を比較するときに
 * カウンタが死んだ場合、このテストが検出する。
 * </p>
 */
public class RetentionHighWaterReportTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 通常CI: 短セル40行のslice所有、compact、終了清算前の解放を固定する。 */
	public void testShortCellOwnershipAndRelease() throws Exception {
		final int rows = 40;
		final File file = EnduranceTest.generateManyRowsTable("t5a-short-cell-ownership", rows);
		final var afterPassB = new AtomicReference<RetentionSnapshot>();
		final var compacted = new AtomicReference<RetentionSnapshot>();
		final var beforeClose = new ArrayList<RetentionSnapshot>();
		try (final AutoCloseable stages = RangeOnlyInvariantTest.observe(RetainedTableBuilder.class,
				"retentionObserver", (BiConsumer<String, LayoutSource>) (stage, source) -> {
			if (stage.equals("after-pass-b")) afterPassB.set(source.retentionSnapshot());
		}); final AutoCloseable compact = RangeOnlyInvariantTest.observe(LayoutSource.class,
				"compactObserver", (Consumer<LayoutSource>) source -> {
			final var snapshot = source.retentionSnapshot();
			if (snapshot.slicedRanges() >= rows * 3) compacted.set(snapshot);
		}); final AutoCloseable close = RangeOnlyInvariantTest.observe(LayoutSource.class,
				"beforeCloseObserver", (Consumer<LayoutSource>)
						source -> beforeClose.add(source.retentionSnapshot()))) {
			this.transcode(file, "t5a-short-cell-ownership", 1);
		} finally {
			Files.deleteIfExists(file.toPath());
		}
		final var measured = afterPassB.get();
		assertNotNull("Pass B後の観測なし", measured);
		assertTrue("計測後も全本文セルのslice所有を保持", measured.slicedRanges() >= rows * 3);
		assertTrue("計測で本文の所有を消費していない", measured.openRanges() >= rows * 3);
		assertTrue("本文イベントのslice化が未発火", measured.slicedEvents() >= rows * 3);
		final var reclaimed = compacted.get();
		assertNotNull("sliceを保持したcompactの観測なし", reclaimed);
		assertTrue("compact後も元ログに本文イベントが残っている: " + reclaimed,
				reclaimed.retainedEvents() < reclaimed.slicedEvents());
		assertFalse("終了清算前の観測なし", beforeClose.isEmpty());
		for (final var snapshot : beforeClose) {
			assertEquals("清算待ちの本文範囲: " + snapshot, 0, snapshot.openRanges());
			assertEquals("清算待ちのリース: " + snapshot, 0L, snapshot.leases());
			assertEquals("清算待ちのslice: " + snapshot, 0, snapshot.slicedRanges());
			assertEquals("清算待ちのslice本文: " + snapshot, 0, snapshot.slicedEvents());
		}
	}

	/**
	 * 短セル3列の表: 列幅確定前の保持と、MAIN消費後・終了清算前を分けて測る。
	 * 6,000行の変換・明示GC・histogramは{@code -Dfoliojet.perf}または
	 * {@code -Dfoliojet.retentionDiag}指定時だけ実行する。行数の上書きは
	 * {@code -Dfoliojet.retentionRows}で行う。
	 *
	 * <p>追加課題: 5MB単一セルがOOM(18秒)からTIMEOUT(420秒)になった原因は未確定で、
	 * 完走の改善実績には数えない。次はPass B/C別時間・GC時間・compact走査件数を観測する。
	 * 10,000行に向けては全体寸法確定後の行単位配置と、全行木・配置済み木・
	 * 計画参照({@code rowToCells}等)の寿命短縮が追加課題となる。</p>
	 */
	public void testShortCellManyRowsRetention() throws Exception {
		if (System.getProperty("foliojet.perf") == null
				&& System.getProperty("foliojet.retentionDiag") == null) return;
		final int rows = Integer.getInteger("foliojet.retentionRows", 6000);
		final File file = EnduranceTest.generateManyRowsTable("t4b-retention-rows", rows);
		final var firstMain = new AtomicReference<RetentionSnapshot>();
		final var lastMain = new AtomicReference<RetentionSnapshot>();
		final var beforeClose = new ArrayList<RetentionSnapshot>();
		final AtomicLong mains = new AtomicLong();
		final AtomicLong compactSamples = new AtomicLong();
		final AtomicLong lastCompactId = new AtomicLong();
		final AtomicReference<RetentionSnapshot> lastCompact = new AtomicReference<>();
		ContinuationStats.reset();
		TwoPassGridFlexRangeTest.resetHighWater();
		try (final AutoCloseable stages = RangeOnlyInvariantTest.observe(RetainedTableBuilder.class,
				"retentionObserver", (BiConsumer<String, LayoutSource>)
						(stage, source) -> reportStage(stage, source));
				final AutoCloseable compact = RangeOnlyInvariantTest.observe(LayoutSource.class,
						"compactObserver", (Consumer<LayoutSource>) source -> {
					lastCompact.set(source.retentionSnapshot());
					if (compactSamples.getAndIncrement() == 0 || source.nextId() - lastCompactId.get() >= 11000) {
						lastCompactId.set(source.nextId());
						reportStage("after-compact", source);
					}
				}); final AutoCloseable start = RangeOnlyInvariantTest.observe(RangeHandle.class,
				"replayStartObserver", (BiConsumer<RangeHandle,
						ReplayIntent>) (handle, intent) -> {
			if (intent == ReplayIntent.MAIN) {
				firstMain.compareAndSet(null, handle.source().retentionSnapshot());
			}
		}); final AutoCloseable end = RangeOnlyInvariantTest.observe(RangeHandle.class,
				"replayObserver", (BiConsumer<RangeHandle,
						ReplayIntent>) (handle, intent) -> {
			if (intent == ReplayIntent.MAIN) {
				mains.incrementAndGet();
				lastMain.set(handle.source().retentionSnapshot());
			}
		}); final AutoCloseable close = RangeOnlyInvariantTest.observe(LayoutSource.class,
				"beforeCloseObserver", (Consumer<LayoutSource>)
						source -> {
							beforeClose.add(source.retentionSnapshot());
							reportStage("before-source-close", source);
						})) {
			this.transcode(file, "t4b-retention-rows", 1);
		} finally {
			System.err.println("[T5a short cells] rows=" + rows + " firstMain=" + firstMain.get()
					+ " lastMain=" + lastMain.get() + " beforeClose=" + beforeClose
					+ " mains=" + mains.get() + " compactSamples=" + compactSamples.get()
					+ " lastCompact=" + lastCompact.get());
			report();
			Files.deleteIfExists(file.toPath());
		}
		// 観測値の閾値は固定しない。変換の正常終了だけを受け入れる。
	}

	private static void reportStage(final String stage, final LayoutSource source) {
		final Runtime runtime = Runtime.getRuntime();
		final long used = runtime.totalMemory() - runtime.freeMemory();
		System.gc();
		final long usedAfterGc = runtime.totalMemory() - runtime.freeMemory();
		System.err.println("[T5a short cells] stage=" + stage + " heapUsed=" + used
				+ " heapUsedAfterGc=" + usedAfterGc + " " + source.retentionSnapshot());
		if (stage.startsWith("after-input-rows-") || stage.equals("before-pass-b")
				|| stage.equals("after-table-end")) reportClassHistogram(stage);
	}

	/** 変換スレッドを止めた同じ時点のlive histogram。JDKがない環境では観測だけ省略する。 */
	private static void reportClassHistogram(final String stage) {
		final String prefix = "[T5a histogram stage=" + stage + "] ";
		final String executable = File.separatorChar == '\\' ? "jcmd.exe" : "jcmd";
		final Path bundled = Path.of(System.getProperty("java.home"), "bin", executable);
		final Process process;
		try {
			process = new ProcessBuilder(Files.isExecutable(bundled) ? bundled.toString() : executable,
					Long.toString(ProcessHandle.current().pid()), "GC.class_histogram")
					.redirectErrorStream(true).start();
		} catch (IOException e) {
			System.err.println(prefix + "skipped: jcmd unavailable: " + e.getMessage());
			return;
		}
		// 上位25クラスと、順位が下がったCSSStyle/Value[]も必ず出す。
		// 全行を読んでpipeを詰まらせず、行数増加中の生存数を同じ書式で比較する。
		final Thread reader = Thread.startVirtualThread(() -> {
			try (var input = process.inputReader(StandardCharsets.UTF_8)) {
				int rows = 0;
				long styles = 0, valueArrays = 0, valueArrayBytes = 0, treeEntries = 0, integers = 0;
				for (String line; (line = input.readLine()) != null;) {
					if (line.stripLeading().matches("[0-9]+:\\s+[0-9]+\\s+[0-9]+\\s+.*")) {
						final String[] columns = line.strip().split("\\s+");
						final String type = columns[3];
						final long count = Long.parseLong(columns[1]);
						final boolean style = type.equals("net.zamasoft.foliojet.css.CSSStyle")
								|| type.startsWith("net.zamasoft.foliojet.css.CSSStyle$");
						final boolean values = type.equals("[Lnet.zamasoft.foliojet.css.value.Value;");
						if (style) styles += count;
						if (values) {
							valueArrays += count;
							valueArrayBytes += Long.parseLong(columns[2]);
						}
						if (type.equals("java.util.TreeMap$Entry")) treeEntries += count;
						if (type.equals("java.lang.Integer")) integers += count;
						if (++rows <= 25 || style || values) System.err.println(prefix + line);
					} else if (rows == 0) {
						System.err.println(prefix + line);
					}
				}
				if (rows != 0) {
					System.err.println("[T5a style retention] stage=" + stage + " cssStyles=" + styles
							+ " valueArrays=" + valueArrays + " valueArrayBytes=" + valueArrayBytes
							+ " treeEntries=" + treeEntries + " integers=" + integers);
				}
			} catch (IOException e) {
				System.err.println(prefix + "output unavailable: " + e.getMessage());
			}
		});
		try {
			if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
				System.err.println(prefix + "skipped: jcmd timed out");
				process.destroyForcibly();
			} else if (process.exitValue() != 0) {
				System.err.println(prefix + "jcmd exit=" + process.exitValue());
			}
			reader.join(5000);
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
		}
	}

	/** T3a: huge-gridをTwoPass宿主へ入れ、吸収済み計画の追加保持がゼロであることを確認する。 */
	public void testHugeGridParentPlanRetention() throws Exception {
		final String html = Files.readString(Path.of("files/unittest/0500-twopass-range/huge-grid.html"))
				.replace("<body>", "<body><div style='float:left;width:auto'>")
				.replace("</body>", "</div></body>");
		final Set<TwoPassBlockBuilder> owners = Collections.newSetFromMap(new IdentityHashMap<>());
		final long[] observedItems = { 0 };
		// 測定のresetを他の試験へ漏らさない。出力はこのfixtureだけのhigh-water。
		final AtomicLong[] counters = { TableBuildStats.SOURCE_LEASE_HIGH_WATER,
				TableBuildStats.SOURCE_RETAINED_EVENT_HIGH_WATER, TableBuildStats.SOURCE_OLDEST_WATERMARK_LAG_HIGH_WATER,
				TableBuildStats.SOURCE_OLDEST_WATERMARK_AT_HIGH_WATER };
		final long[] saved = new long[counters.length];
		for (int i = 0; i < counters.length; ++i) saved[i] = counters[i].get();
		try {
			TwoPassGridFlexRangeTest.resetHighWater();
			final List<byte[]> range;
			try (final AutoCloseable observer = RangeOnlyInvariantTest.observeOwnership(owner -> {
				for (final Object node : RangeOnlyInvariantTest.nodes(owner)) {
					if (isGridOrFlexPlan(node) && owners.add(owner)) {
						observedItems[0] += retainedPlanItems(owner);
					}
				}
			})) {
				range = TwoPassFlowSealTest.render(html);
			}
			TwoPassGridFlexRangeTest.reportHighWater("parent range", range.size());
			assertTrue("Grid計画の項目が未観測", observedItems[0] > 0);
			for (final TwoPassBlockBuilder owner : owners) {
				assertTrue("吸収済みノードが子builder/計画を保持している", RangeOnlyInvariantTest.nodes(owner).isEmpty());
			}
			System.err.println("[T4b huge-grid parent] observedPlanItems=" + observedItems[0]
					+ " retainedOwnerNodes=0");

		} finally {
			for (int i = 0; i < counters.length; ++i) counters[i].set(saved[i]);
		}
	}

	private static boolean isGridOrFlexPlan(final Object node) {
		final String kind = RangeOnlyInvariantTest.call(node, "kind").toString();
		return kind.equals("GRID") || kind.equals("FLEX");
	}

	private static long retainedPlanItems(final TwoPassBlockBuilder owner) {
		long items = 0;
		for (final Object node : RangeOnlyInvariantTest.nodes(owner)) {
			if (!isGridOrFlexPlan(node)) continue;
			final Object plan = RangeOnlyInvariantTest.call(node, "retainedPlan");
			if (plan != null) items += ((List<?>) RangeOnlyInvariantTest.field(plan, "items")).size();
		}
		return items;
	}

	/**
	 * auto表(thead/tfoot・colspanつき)で、Retained保持形状・TwoPass・
	 * LayoutSourceの各high-waterが観測されることを固定する。
	 */
	public void testRetainedAutoTableHighWaterObserved() throws Exception {
		final int bodyRows = 200;
		final int columns = 6;
		final long autoBefore = TableBuildStats.retentionReasonCount(TableRetentionReason.AUTO_COLUMNS);
		final long cellSealsBefore = ContinuationStats.CELL_RANGE_SEALS.get();
		final long cellRangeBindsBefore = ContinuationStats.CELL_RANGE_BINDS.get();
		final long passCTablesBefore = ContinuationStats.TABLE_PASS_C_TABLES.get();
		final long passBMeasuresBefore = ContinuationStats.TABLE_PASS_B_CELL_MEASURES.get();
		final File doc = generateAutoTable("e6-hw-auto-table", bodyRows, columns);
		this.transcode(doc, "e6-hw-auto-table", 1);

		assertTrue("auto表がRetained(AUTO_COLUMNS)として記録されていません",
				TableBuildStats.retentionReasonCount(TableRetentionReason.AUTO_COLUMNS) > autoBefore);
		assertTrue("Retained行数high-waterが観測されていません: " + TableBuildStats.RETAINED_ROW_HIGH_WATER.get(),
				TableBuildStats.RETAINED_ROW_HIGH_WATER.get() >= bodyRows);
		assertTrue("Retained論理セルslot数high-waterが観測されていません: "
				+ TableBuildStats.RETAINED_CELL_SLOT_HIGH_WATER.get(),
				TableBuildStats.RETAINED_CELL_SLOT_HIGH_WATER.get() >= (long) bodyRows * columns);
		assertTrue("Retained実セル数high-waterが観測されていません: " + TableBuildStats.RETAINED_CELL_HIGH_WATER.get(),
				TableBuildStats.RETAINED_CELL_HIGH_WATER.get() >= bodyRows);
		assertTrue("反復ヘッダ行数high-waterが観測されていません",
				TableBuildStats.RETAINED_REPEATED_HEADER_ROW_HIGH_WATER.get() >= 1);
		assertTrue("反復フッタ行数high-waterが観測されていません",
				TableBuildStats.RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER.get() >= 1);
		assertTrue("colspan制約数(Uspan)high-waterが観測されていません: "
				+ TableBuildStats.RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER.get(),
				TableBuildStats.RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER.get() >= 2);
		assertTrue("範囲leaseのhigh-waterが観測されていません",
				TableBuildStats.SOURCE_LEASE_HIGH_WATER.get() > 0);
		assertTrue("保持Eventのhigh-waterが観測されていません",
				TableBuildStats.SOURCE_RETAINED_EVENT_HIGH_WATER.get() > 0);
		assertTrue("TwoPassネスト深さのhigh-waterが観測されていません",
				TableBuildStats.TWO_PASS_NEST_DEPTH_HIGH_WATER.get() >= 1);
		assertTrue("LayoutSourceイベント数のhigh-waterが観測されていません",
				ContinuationStats.SOURCE_EVENT_HIGH_WATER.get() > 0);

		// E-6増分5a: セルclose時のrange sealがこの表の全実セル規模で発火し
		// (プレーンテキストセルは全て適格のはず)、seal数とbind数が一致する
		// (リース1:1)。
		final long cellSeals = ContinuationStats.CELL_RANGE_SEALS.get() - cellSealsBefore;
		final long cellRangeBinds = ContinuationStats.CELL_RANGE_BINDS.get() - cellRangeBindsBefore;
		assertTrue("セルrange seal(E-6増分5a)がauto表の実セル規模で発火していません: " + cellSeals,
				cellSeals >= bodyRows);
		assertEquals("セルseal数とセルrange bind数が一致しません(リース取り残しの疑い)", cellSeals,
				cellRangeBinds);

		// E-6増分5b-2: 全実セルがseal適格のこの表はPass C(行単位逐次bind)で
		// 処理され、Pass B(行計測)が実セル規模で発火する——「行高計算中は
		// bind済みセル本文木ゼロ(計測木は都度破棄)」の観測指標
		assertTrue("auto表が表Pass C(行単位逐次bind)で処理されていません",
				ContinuationStats.TABLE_PASS_C_TABLES.get() > passCTablesBefore);
		final long passBMeasures = ContinuationStats.TABLE_PASS_B_CELL_MEASURES.get() - passBMeasuresBefore;
		assertTrue("表Pass B(行計測)がauto表の実セル規模で発火していません: " + passBMeasures,
				passBMeasures >= bodyRows);

		report();
	}

	/**
	 * STRUCTURE_SCAN(:last-child系)と:has()で、SelectorFactsの各Map/Setの
	 * エントリ数high-waterが観測されることを固定する。
	 */
	public void testSelectorFactsHighWaterObserved() throws Exception {
		this.transcode(new File("files/unittest/3000-SELECTOR/last-child-family.html"), "e6-hw-last-child-family", 2);
		this.transcode(new File("files/unittest/3000-SELECTOR/has.html"), "e6-hw-has", 2);

		assertTrue("lastChildのhigh-waterが観測されていません", SelectorFacts.LAST_CHILD_HIGH_WATER.get() > 0);
		assertTrue("lastOfTypeのhigh-waterが観測されていません", SelectorFacts.LAST_OF_TYPE_HIGH_WATER.get() > 0);
		assertTrue("positionFromEndのhigh-waterが観測されていません",
				SelectorFacts.POSITION_FROM_END_HIGH_WATER.get() > 0);
		assertTrue("typePositionFromEndのhigh-waterが観測されていません",
				SelectorFacts.TYPE_POSITION_FROM_END_HIGH_WATER.get() > 0);
		assertTrue(":has()要素数のhigh-waterが観測されていません",
				SelectorFacts.HAS_MATCH_ELEMENT_HIGH_WATER.get() > 0);
		assertTrue(":has()ペア数のhigh-waterが観測されていません", SelectorFacts.HAS_MATCH_PAIR_HIGH_WATER.get() > 0);

		report();
	}

	/** 現在のhigh-water値をstderrへレポートする(このJVMで走った全テストの累積)。 */
	private static void report() {
		final StringBuilder s = new StringBuilder();
		s.append("[E-6 retention high-water]\n");
		s.append("  RETAINED_ROW_HIGH_WATER=").append(TableBuildStats.RETAINED_ROW_HIGH_WATER.get()).append('\n');
		s.append("  RETAINED_CELL_HIGH_WATER=").append(TableBuildStats.RETAINED_CELL_HIGH_WATER.get()).append('\n');
		s.append("  RETAINED_CELL_SLOT_HIGH_WATER=").append(TableBuildStats.RETAINED_CELL_SLOT_HIGH_WATER.get())
				.append('\n');
		s.append("  RETAINED_REPEATED_HEADER_ROW_HIGH_WATER=")
				.append(TableBuildStats.RETAINED_REPEATED_HEADER_ROW_HIGH_WATER.get()).append('\n');
		s.append("  RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER=")
				.append(TableBuildStats.RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER.get()).append('\n');
		s.append("  RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER=")
				.append(TableBuildStats.RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER.get()).append('\n');

		s.append("  CELL_RANGE_SEALS=").append(ContinuationStats.CELL_RANGE_SEALS.get()).append('\n');
		s.append("  CELL_RANGE_BINDS=").append(ContinuationStats.CELL_RANGE_BINDS.get()).append('\n');

		s.append("  TABLE_PASS_C_TABLES=").append(ContinuationStats.TABLE_PASS_C_TABLES.get()).append('\n');
		s.append("  TABLE_LEGACY_BINDROWS=").append(ContinuationStats.TABLE_LEGACY_BINDROWS.get()).append('\n');
		s.append("  TABLE_PASS_B_CELL_MEASURES=").append(ContinuationStats.TABLE_PASS_B_CELL_MEASURES.get())
				.append('\n');
		for (final TableRetentionReason reason : TableRetentionReason.values()) {
			s.append("  RETENTION_REASON_").append(reason).append('=')
					.append(TableBuildStats.retentionReasonCount(reason)).append('\n');
		}


		s.append("  TWO_PASS_NEST_DEPTH_HIGH_WATER=").append(TableBuildStats.TWO_PASS_NEST_DEPTH_HIGH_WATER.get())
				.append('\n');
		s.append("  SOURCE_EVENT_HIGH_WATER=").append(ContinuationStats.SOURCE_EVENT_HIGH_WATER.get()).append('\n');
		s.append("  SOURCE_LEASE_HIGH_WATER=").append(TableBuildStats.SOURCE_LEASE_HIGH_WATER.get()).append('\n');
		s.append("  SOURCE_RETAINED_EVENT_HIGH_WATER=")
				.append(TableBuildStats.SOURCE_RETAINED_EVENT_HIGH_WATER.get()).append('\n');
		s.append("  SOURCE_OLDEST_WATERMARK_LAG_HIGH_WATER=")
				.append(TableBuildStats.SOURCE_OLDEST_WATERMARK_LAG_HIGH_WATER.get()).append('\n');
		s.append("  SOURCE_OLDEST_WATERMARK_AT_HIGH_WATER=")
				.append(TableBuildStats.SOURCE_OLDEST_WATERMARK_AT_HIGH_WATER.get()).append('\n');


		s.append("  SELECTOR_LAST_CHILD_HIGH_WATER=").append(SelectorFacts.LAST_CHILD_HIGH_WATER.get()).append('\n');
		s.append("  SELECTOR_LAST_OF_TYPE_HIGH_WATER=").append(SelectorFacts.LAST_OF_TYPE_HIGH_WATER.get())
				.append('\n');
		s.append("  SELECTOR_EMPTY_HIGH_WATER=").append(SelectorFacts.EMPTY_HIGH_WATER.get()).append('\n');
		s.append("  SELECTOR_POSITION_FROM_END_HIGH_WATER=").append(SelectorFacts.POSITION_FROM_END_HIGH_WATER.get())
				.append('\n');
		s.append("  SELECTOR_TYPE_POSITION_FROM_END_HIGH_WATER=")
				.append(SelectorFacts.TYPE_POSITION_FROM_END_HIGH_WATER.get()).append('\n');
		s.append("  SELECTOR_HAS_MATCH_ELEMENT_HIGH_WATER=").append(SelectorFacts.HAS_MATCH_ELEMENT_HIGH_WATER.get())
				.append('\n');
		s.append("  SELECTOR_HAS_MATCH_PAIR_HIGH_WATER=").append(SelectorFacts.HAS_MATCH_PAIR_HIGH_WATER.get())
				.append('\n');
		System.err.print(s);
	}

	/**
	 * table-layout:auto(既定)の表を、thead/tfoot・colspan(2種類の
	 * (開始列,colspan)制約)つきで生成する。golden比較対象ではないため
	 * files/unittestへは置かずlocal/unittestへ都度生成する。
	 */
	private static File generateAutoTable(String name, int bodyRows, int columns) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"400pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}td,th{border:1pt solid black}</style>\n");
			w.write("</head><body><table>\n");
			w.write("<thead><tr>");
			for (int c = 0; c < columns; ++c) {
				w.write("<th>h" + c + "</th>");
			}
			w.write("</tr></thead>\n");
			w.write("<tfoot><tr>");
			for (int c = 0; c < columns; ++c) {
				w.write("<td>f" + c + "</td>");
			}
			w.write("</tr></tfoot>\n");
			w.write("<tbody>\n");
			for (int i = 0; i < bodyRows; ++i) {
				w.write("<tr>");
				int c = 0;
				if (i == 0) {
					// (開始列0, colspan2)の制約
					w.write("<td colspan=\"2\">span2</td>");
					c = 2;
				} else if (i == 1) {
					// (開始列0, colspan3)の制約
					w.write("<td colspan=\"3\">span3</td>");
					c = 3;
				}
				for (; c < columns; ++c) {
					w.write("<td>r" + i + "c" + c + "</td>");
				}
				w.write("</tr>\n");
			}
			w.write("</tbody></table></body></html>\n");
		}
		return file;
	}

	private void transcode(File source, String name, int passCount) throws Exception {
		File pdf = new File("local/unittest/display-list/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (passCount > 1) {
					session.property("processing.pass-count", String.valueOf(passCount));
				}
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
