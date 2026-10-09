package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.builder.impl.TableBuildStats;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusEvent;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusKey;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassItemKind;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.RangeHandle.ReplayMode;
import net.zamasoft.foliojet.layout.fragment.RangeHandle.State;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory;

/** T2: observe range replay, ownership state, and retention for each item type. */
public final class TwoPassGridFlexRangeTest extends TestCase {
	public void testGridTakeoverRanges() throws Exception {
		for (final Fixture fixture : gridTakeoverRangesFixtures()) {
			renderItems(fixture.label(), fixture.file(), fixture.expectedKind(), fixture.expectedRoot());
		}
	}

	private static List<Fixture> gridTakeoverRangesFixtures() {
		return List.of(
				new Fixture("grid-tracks-fixed", TwoPassItemKind.TAKEOVER),
				new Fixture("grid-tracks-fr", TwoPassItemKind.TAKEOVER),
				new Fixture("grid-tracks-column", TwoPassItemKind.TAKEOVER),
				new Fixture("grid-span-areas", TwoPassItemKind.TAKEOVER),
				new Fixture("grid-takeover-content", TwoPassItemKind.TAKEOVER));
	}

	public void testGridNeutralWrapperRanges() throws Exception {
		for (final Fixture fixture : gridNeutralWrapperRangesFixtures()) {
			renderItems(fixture.label(), fixture.file(), fixture.expectedKind(), fixture.expectedRoot());
		}
	}

	private static List<Fixture> gridNeutralWrapperRangesFixtures() {
		return List.of(
				new Fixture("grid-neutral-roots", TwoPassItemKind.ELEMENT));
	}

	public void testFlexTakeoverRanges() throws Exception {
		for (final Fixture fixture : flexTakeoverRangesFixtures()) {
			renderItems(fixture.label(), fixture.file(), fixture.expectedKind(), fixture.expectedRoot());
		}
	}

	private static List<Fixture> flexTakeoverRangesFixtures() {
		return List.of(
				new Fixture("flex-row-nowrap", TwoPassItemKind.TAKEOVER),
				new Fixture("flex-row-wrap", TwoPassItemKind.TAKEOVER),
				new Fixture("flex-column-nowrap", TwoPassItemKind.TAKEOVER),
				new Fixture("flex-column-wrap", TwoPassItemKind.TAKEOVER),
				new Fixture("flex-takeover-content", TwoPassItemKind.TAKEOVER));
	}

	public void testFlexNeutralWrapperRanges() throws Exception {
		for (final Fixture fixture : flexNeutralWrapperRangesFixtures()) {
			renderItems(fixture.label(), fixture.file(), fixture.expectedKind(), fixture.expectedRoot());
		}
	}

	private static List<Fixture> flexNeutralWrapperRangesFixtures() {
		return List.of(
				new Fixture("flex-neutral-row", TwoPassItemKind.ELEMENT, TwoPassRootKind.FLEX_ITEM),
				new Fixture("flex-neutral-column", TwoPassItemKind.ELEMENT, TwoPassRootKind.FLEX_ITEM));
	}

	/** Pass absolute-ownership proofs from previously sealed floats/inline-blocks to items. */
	public void testSealedChildAbsoluteOwnershipRanges() throws Exception {
		for (final Fixture fixture : sealedChildAbsoluteOwnershipRangesFixtures()) {
			renderItems(fixture.label(), fixture.file(), fixture.expectedKind(), fixture.expectedRoot());
		}
	}

	private static List<Fixture> sealedChildAbsoluteOwnershipRangesFixtures() {
		return List.of(
				new Fixture("grid-sealed-float", TwoPassItemKind.TAKEOVER, TwoPassRootKind.GRID_ITEM),
				new Fixture("grid-neutral-sealed-float", TwoPassItemKind.ELEMENT, TwoPassRootKind.GRID_ITEM),
				new Fixture("grid-sealed-inline-block", TwoPassItemKind.TAKEOVER, TwoPassRootKind.GRID_ITEM),
				new Fixture("grid-neutral-sealed-inline-block", TwoPassItemKind.ELEMENT, TwoPassRootKind.GRID_ITEM),
				new Fixture("flex-sealed-float", TwoPassItemKind.TAKEOVER, TwoPassRootKind.FLEX_ITEM),
				new Fixture("flex-neutral-sealed-float", TwoPassItemKind.ELEMENT, TwoPassRootKind.FLEX_ITEM),
				new Fixture("flex-sealed-inline-block", TwoPassItemKind.TAKEOVER, TwoPassRootKind.FLEX_ITEM),
				new Fixture("flex-neutral-sealed-inline-block", TwoPassItemKind.ELEMENT, TwoPassRootKind.FLEX_ITEM));
	}

	/** Freeze strictLineBox to the mode at recording time and preserve it for every replay. */
	public void testStrictLineBoxRoundTrip() {
		for (final boolean strict : new boolean[] { false, true }) {
			final BlockParams source = new BlockParams();
			source.strictLineBox = strict;
			final BlockParamsTemplate template = BlockParamsTemplate.freeze(source);
			source.strictLineBox = !strict;
			final BlockParams first = template.materialize();
			assertEquals(strict, first.strictLineBox);
			first.strictLineBox = !strict;
			assertEquals(strict, template.materialize().strictLineBox);
		}
	}

	/** Also compare height, SVG, middle alignment, and separators on page push-out against existing goldens. */
	public void testPushedFlexRowRanges() throws Exception {
		final String fixture = "0510-flex/pushed-row-absolute-child.html";
		try (final var census = ContinuationStats.beginTwoPassCensus()) {
			ContinuationStats.reset();
			final List<byte[]> range = TwoPassFlowSealTest.render(new File("files/unittest", fixture));
			assertItemCensus(fixture, census.snapshot(TwoPassCensusEvent.BIND));
			assertBindTotals(fixture, census.snapshot(TwoPassCensusEvent.BIND));
			assertGoldenPages("0510-flex_pushed-row-absolute-child", range);
		}
	}

	/** Pin down an SVG-only inline-block's strut, middle alignment, and subsequent line positions with a minimal setup. */
	public void testPushedFlexInlineBlockMiddleRanges() throws Exception {
		// Both reconstruction after push-out and normal initial bind use the recorded line height.
		for (final boolean pushed : new boolean[] { true, false }) {
			final String label = "flex inline-block SVG middle pushed=" + pushed;
			final File file = fixtureFile("flex-middle-" + (pushed ? "pushed" : "normal"));
			try (final var census = ContinuationStats.beginTwoPassCensus()) {
				ContinuationStats.reset();
				final List<byte[]> range = TwoPassFlowSealTest.render(file);
				assertGoldenPages("0500-twopass-range_" + file.getName().replace(".html", ""), range);
				assertEquals(label + ": ページ数", pushed ? 2 : 1, range.size());
				final String page = new String(range.get(range.size() - 1), StandardCharsets.UTF_8);
				final double svgY = drawY(page, "AbsoluteRectFrame[w=28.50 h=15.00]");
				// Recorded-side measurement (sans-serif 12px/normal): even an SVG-only line includes a 2.91 pt descent.
				assertEquals(label + ": middle", 3.81,
						drawY(page, "Text[\"34℃\"") - svgY, 0.02);
				assertEquals(label + ": 後続行", 23.91,
						drawY(page, "Text[\"熱中症指数\"") - svgY, 0.02);
				final var binds = census.snapshot(TwoPassCensusEvent.BIND);
				assertItemCensus(label, binds);
				assertBindTotals(label, binds);
				assertTrue(label + ": flex項目のrange bind未発火", binds.keySet().stream()
						.anyMatch(key -> key.rootKind() == TwoPassRootKind.FLEX_ITEM
								&& key.itemKind() == TwoPassItemKind.TAKEOVER
								&& key.sealOutcome().equals("accepted")));
				TwoPassFlowSealTest.assertLeaseBalance(label);
			}
		}
	}

	/**
	 * T5b fix (codex review P2): the MEASURE parent wrapper also copies the recorded strictLineBox.
	 * Grid row height comes from item intrinsic dimensions (minPage via MEASURE), so verify that row heights
	 * for items containing SVG-only lines match between live (fixed width) and max-content (MEASURE).
	 */
	public void testMeasureWrapperKeepsStrutForAtomicLine() throws Exception {
		final List<double[]> geometry = new ArrayList<>();
		for (final String width : new String[] { "200px", "max-content" }) {
			final String label = "measure wrapper strut width=" + width;
			final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
					+ "@page{size:400pt 300pt;margin:10pt}body{margin:0;font:12px/normal sans-serif}"
					+ ".host{display:grid;width:" + width + ";grid-template-columns:auto;grid-template-rows:auto auto}"
					+ ".host>div{border:1px solid #246}.icon{display:inline-block;vertical-align:middle}"
					+ "</style></head><body><div class='host'><div>"
					+ "<svg class='icon' width='38' height='20' xmlns='http://www.w3.org/2000/svg'>"
					+ "<rect width='38' height='20' fill='#fc0'/></svg><br><span>34℃</span></div>"
					+ "<div>熱中症指数</div></div></body></html>";
			final List<byte[]> pages = TwoPassFlowSealTest.render(html);
			assertEquals(label + ": ページ数", 1, pages.size());
			final String page = new String(pages.get(0), StandardCharsets.UTF_8);
			final double svgY = drawY(page, "AbsoluteRectFrame[w=28.50 h=15.00]");
			final double text = drawY(page, "Text[\"34℃\"") - svgY;
			final double nextRow = drawY(page, "Text[\"熱中症指数\"") - svgY;
			System.err.println("[T5b measure] " + label + " svgY=" + svgY + " text=" + text + " nextRow=" + nextRow);
			geometry.add(new double[] { text, nextRow });
		}
		assertEquals("1 行目の文字: live と MEASURE 経由", geometry.get(0)[0], geometry.get(1)[0], 0.02);
		assertEquals("次の行(行高=minPage): live と MEASURE 経由", geometry.get(0)[1], geometry.get(1)[1], 0.02);
	}

	/** T5b: preserve the actual height of textless lines and relative middle position even when pushing a paragraph to the next page. */
	public void testPushedParagraphPreservesTextlessLineHeight() throws Exception {
		final List<double[]> geometry = new ArrayList<>();
		for (final boolean pushed : new boolean[] { false, true }) {
			final String label = "textless paragraph pushed=" + pushed;
			final String html = """
					<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
					@page{size:400pt 300pt;margin:10pt}body{margin:0;font:12px/normal sans-serif}
					.host{display:flex;width:300px}.host>div{flex:0 1 290px}
					p{margin:0;page-break-inside:avoid}
					.icon,.middle{display:inline-block;vertical-align:middle}
					.icon{background:#def;outline:1pt solid #246}
					</style></head><body><div style='height:%dpt;background:#eee'></div>
					<div class='host'><div><p>今日の天気<br>
					<span class='icon'><svg width='38' height='20' xmlns='http://www.w3.org/2000/svg'><rect width='38' height='20' fill='#fc0'/></svg></span>
					<span class='middle'>34℃</span><br>熱中症指数</p></div></div>
					</body></html>
					""".formatted(pushed ? 260 : 0);
			// A normal fixed-width block does not become TwoPass, so use a flex item as the host. It does not fill
			// the line: a single item that fills it is laid out in the flow (2026-10-09), not as a TwoPass host.
			// DirectSession converts on another thread. Observe reconstruction with the existing static AtomicLong.
			final long replays = BoxRecipeBoxFactory.FLEX_REPLAYS.get();
			ContinuationStats.reset();
			final List<byte[]> pages = TwoPassFlowSealTest.render(html);
			assertEquals(label + ": ページ数", pushed ? 2 : 1, pages.size());
			assertTrue(label + ": TwoPassのrange bind未発火", ContinuationStats.RANGE_FIRST_BINDS.get() > 0);
			if (pushed) {
				assertTrue(label + ": flexのrecipe再構築未発火", BoxRecipeBoxFactory.FLEX_REPLAYS.get() > replays);
			}
			final String page = new String(pages.get(pages.size() - 1), StandardCharsets.UTF_8);
			assertEquals(label + ": iconの描画数", 1L, page.lines().filter(line -> line.contains(" outline[")).count());
			final double svgY = drawY(page, "AbsoluteRectFrame[w=28.50 h=15.00]");
			// icon has no padding/border, auto height, and one SVG-only line. Its actual background height equals that line height.
			final String icon = drawLine(page, " outline[");
			final int from = icon.indexOf(" h=") + 3;
			final double height = Double.parseDouble(icon.substring(from, icon.indexOf(']', from)));
			assertTrue(label + ": SVGの下のstrutがない", height > 15);
			geometry.add(new double[] { height, drawY(page, "Text[\"34℃\"") - svgY,
					drawY(page, "Text[\"熱中症指数\"") - svgY });
			TwoPassFlowSealTest.assertLeaseBalance(label);
		}
		assertEquals("改頁前後の文字なし行高", geometry.get(0)[0], geometry.get(1)[0], 0.02);
		assertEquals("改頁前後のmiddle位置", geometry.get(0)[1], geometry.get(1)[1], 0.02);
		assertEquals("改頁前後の後続行位置", geometry.get(0)[2], geometry.get(1)[2], 0.02);
	}

	private static double drawY(final String page, final String entry) {
		final String line = drawLine(page, entry);
		final int from = line.indexOf(" y=") + 3;
		return Double.parseDouble(line.substring(from, line.indexOf(' ', from)));
	}

	private static String drawLine(final String page, final String entry) {
		final String line = page.lines().filter(value -> value.contains(entry)).findFirst().orElse(null);
		assertNotNull(entry + ": 描画がありません", line);
		return line;
	}

	public void testParentRangesRebuildItems() throws Exception {
		for (final Fixture fixture : parentRangesRebuildItemsFixtures()) {
			renderItems(fixture.label(), fixture.file(), fixture.expectedKind(), fixture.expectedRoot());
		}
	}

	private static List<Fixture> parentRangesRebuildItemsFixtures() {
		return List.of(
				new Fixture("float-parent", TwoPassItemKind.TAKEOVER),
				new Fixture("cell-parent", TwoPassItemKind.ELEMENT));
	}

	public void testAnonymousItemsUseRanges() throws Exception {
		for (final Fixture fixture : anonymousItemsUseRangesFixtures()) {
			renderItems(fixture.label(), fixture.file(), fixture.expectedKind(), fixture.expectedRoot());
		}
	}

	private static List<Fixture> anonymousItemsUseRangesFixtures() {
		return List.of(
				new Fixture("grid-anonymous", TwoPassItemKind.ANONYMOUS),
				new Fixture("flex-anonymous", TwoPassItemKind.ANONYMOUS));
	}

	/** Also compare existing examples of span, areas, replaced elements, order, page breaks, etc. across both paths. */
	public void testExistingGridFlexCorpusRanges() throws Exception {
		for (final File file : corpusFiles()) {
			try (final var census = ContinuationStats.beginTwoPassCensus()) {
				ContinuationStats.reset();
				final List<byte[]> range = TwoPassFlowSealTest.render(file);
				assertItemCensus(file.toString(), census.snapshot(TwoPassCensusEvent.BIND));
				assertBindTotals(file.toString(), census.snapshot(TwoPassCensusEvent.BIND));
			}
		}
	}

	public void testItemRangeStates() throws Exception {
		final Field seal = RangeHandle.class.getDeclaredField("sealObserver");
		final Field replay = RangeHandle.class.getDeclaredField("replayObserver");
		seal.setAccessible(true);
		replay.setAccessible(true);
		final Object savedSeal = seal.get(null), savedReplay = replay.get(null);
		final List<RangeHandle> handles = new ArrayList<>();
		final AtomicInteger measures = new AtomicInteger();
		final Consumer<RangeHandle> onSeal = handle -> {
			assertEquals(State.OPEN, handle.state());
			if (handle.replayMode() == ReplayMode.ROOTED_SUBTREE) {
				assertTrue(handle.source().get(handle.fromId()) instanceof LayoutSource.Start
						|| handle.source().get(handle.fromId()) instanceof LayoutSource.Replaced);
			} else if (handle.replayMode() == ReplayMode.ANONYMOUS_CHILDREN) {
				assertTrue(handle.source().get(handle.fromId() - 1) instanceof LayoutSource.AnonymousItemStart);
				assertEquals(handle.toId() + 1, handle.source().endOf(handle.fromId() - 1));
			} else {
				assertEquals(ReplayMode.CHILDREN_ONLY, handle.replayMode());
				assertTrue(handle.source().get(handle.fromId() - 1) instanceof LayoutSource.Start);
				assertEquals(handle.toId() + 1, handle.source().endOf(handle.fromId() - 1));
			}
			handles.add(handle);
		};
		final BiConsumer<RangeHandle, ReplayIntent> onReplay = (handle, intent) -> {
			assertEquals(intent == ReplayIntent.MEASURE ? State.OPEN : State.CONSUMED, handle.state());
			if (intent == ReplayIntent.MEASURE) {
				measures.incrementAndGet();
			}
		};
		try {
			seal.set(null, onSeal);
			replay.set(null, onReplay);
			// Items bound directly, items absorbed into an outer float, and items measured in cell Pass B.
			final var measured = renderItems("item lifecycle", fixtureFile("item-lifecycle"), TwoPassItemKind.TAKEOVER);
			assertTrue("項目自身のMEASURE範囲再生が未発火", measured.keySet().stream()
					.anyMatch(key -> key.itemKind() == TwoPassItemKind.TAKEOVER || key.itemKind() == TwoPassItemKind.ELEMENT));
		} finally {
			seal.set(null, savedSeal);
			replay.set(null, savedReplay);
		}
		assertFalse(handles.isEmpty());
		assertTrue("MEASURE未発火", measures.get() > 0);
		assertTrue("MAIN未発火", handles.stream().anyMatch(handle -> handle.state() == State.CONSUMED));
		assertTrue("親への吸収未発火", handles.stream().anyMatch(handle -> handle.state() == State.SUBSUMED));
		assertTrue(handles.stream().anyMatch(handle -> handle.replayMode() == ReplayMode.ROOTED_SUBTREE));
		for (final RangeHandle handle : handles) {
			assertFalse("未終端のハンドル", handle.state() == State.OPEN);
			assertIllegalState(handle::subsume);
			assertIllegalState(handle::abandon);
			assertIllegalState(() -> handle.measure(null, null));
			assertIllegalState(() -> handle.bind(null, null));
		}
	}

	public void testHugeGridHighWaterAndRanges() throws Exception {
		final File file = new File("files/unittest/0500-twopass-range/huge-grid.html");
		resetHighWater();
		final List<byte[]> range = TwoPassFlowSealTest.render(file);
		reportHighWater("range", range.size());
		assertGoldenPages("0500-twopass-range_huge-grid", range);
	}

	static File fixtureFile(final String name) {
		return new File("files/unittest/0500-twopass-range/t4b-" + name + ".html");
	}

	private record Fixture(String label, TwoPassItemKind expectedKind, TwoPassRootKind expectedRoot) {
		Fixture(final String label, final TwoPassItemKind expectedKind) {
			this(label, expectedKind, null);
		}
		File file() { return fixtureFile(this.label); }
	}

	static List<File> corpusFiles() {
		final List<File> documents = new ArrayList<>();
		for (final String directory : List.of("0500-grid", "0510-flex")) {
			final File[] files = new File("files/unittest", directory).listFiles((dir, name) -> name.endsWith(".html"));
			assertNotNull(files);
			Arrays.sort(files);
			documents.addAll(List.of(files));
		}
		return List.copyOf(documents);
	}

	private static Map<TwoPassCensusKey, Long> renderItems(final String label, final File file, final TwoPassItemKind expectedKind)
			throws Exception {
		return renderItems(label, file, expectedKind, null);
	}

	private static Map<TwoPassCensusKey, Long> renderItems(final String label, final File file,
			final TwoPassItemKind expectedKind, final TwoPassRootKind expectedRoot) throws Exception {
		try (final var census = ContinuationStats.beginTwoPassCensus()) {
			ContinuationStats.reset();
			final List<byte[]> range = TwoPassFlowSealTest.render(file);
			assertGoldenPages("0500-twopass-range_" + file.getName().replace(".html", ""), range);
			final var binds = census.snapshot(TwoPassCensusEvent.BIND);
			assertItemCensus(label, binds);
			assertBindTotals(label, binds);
			assertTrue(label + ": 項目種別未発火 " + expectedRoot + "/" + expectedKind,
					binds.keySet().stream().anyMatch(key -> key.itemKind() == expectedKind
							&& (expectedRoot == null || key.rootKind() == expectedRoot)));
			return census.snapshot(TwoPassCensusEvent.MEASURE_RANGE);
		}
	}

	static void assertGoldenPages(final String fixture, final List<byte[]> pages) throws Exception {
		final File[] files = new File("files/unittest/display-list-golden", fixture)
				.listFiles((dir, name) -> name.startsWith("page-") && name.endsWith(".txt"));
		assertNotNull(fixture + ": goldenがありません", files);
		Arrays.sort(files);
		final List<byte[]> golden = new ArrayList<>();
		for (final File file : files) {
			golden.add(Files.readAllBytes(file.toPath()));
		}
		TwoPassFlowSealTest.assertPagesEqual(fixture + " golden/range", golden, pages);
	}

	/** Use the same denominator as the overall census to detect omissions and double counting at item level too. */
	private static void assertBindTotals(final String label, final Map<TwoPassCensusKey, Long> binds) {
		final long ranges = binds.entrySet().stream().filter(entry -> entry.getKey().sealOutcome().equals("accepted"))
				.mapToLong(Map.Entry::getValue).sum();
		assertEquals(label + ": range census", ContinuationStats.RANGE_FIRST_BINDS.get(), ranges);

	}

	static void assertItemCensus(final String label, final Map<TwoPassCensusKey, Long> binds) {
		for (final var key : binds.keySet()) {
			if (key.rootKind() != TwoPassRootKind.GRID_ITEM && key.rootKind() != TwoPassRootKind.FLEX_ITEM) continue;
			assertTrue(label + ": 項目分類なし " + key, key.itemKind() != TwoPassItemKind.NONE);
			assertTrue(label + ": 未seal項目 " + key, key.sealAttempted());
			assertEquals(label + ": 不適格項目 " + key, "accepted", key.sealOutcome());
		}
		TwoPassFlowSealTest.assertLeaseBalance(label);
	}

	static void resetHighWater() {
		TableBuildStats.SOURCE_LEASE_HIGH_WATER.set(0);
		TableBuildStats.SOURCE_RETAINED_EVENT_HIGH_WATER.set(0);
		TableBuildStats.SOURCE_OLDEST_WATERMARK_LAG_HIGH_WATER.set(0);
		TableBuildStats.SOURCE_OLDEST_WATERMARK_AT_HIGH_WATER.set(-1);
	}

	static void reportHighWater(final String path, final int pages) {
		System.err.println("[T2 huge-grid] " + path + " pages=" + pages
				+ " leases=" + TableBuildStats.SOURCE_LEASE_HIGH_WATER.get()
				+ " retainedEvents=" + TableBuildStats.SOURCE_RETAINED_EVENT_HIGH_WATER.get()
				+ " oldestWatermarkLag=" + TableBuildStats.SOURCE_OLDEST_WATERMARK_LAG_HIGH_WATER.get()
				+ " oldestWatermarkAtPeak=" + TableBuildStats.SOURCE_OLDEST_WATERMARK_AT_HIGH_WATER.get());
	}

	private static void assertIllegalState(final Runnable action) {
		try {
			action.run();
			fail("終端済みハンドルを再使用した");
		} catch (final IllegalStateException expected) {
			// Terminate only once.
		}
	}
}
