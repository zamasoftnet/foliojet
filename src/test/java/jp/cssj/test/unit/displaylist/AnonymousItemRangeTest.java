package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.box.impl.FlexBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.ItemCoordinator;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;
import net.zamasoft.foliojet.layout.builder.impl.GridBuilder;
import net.zamasoft.foliojet.layout.builder.impl.StyledTextUnitizer;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusEvent;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusKey;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassItemKind;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.RangeHandle.ReplayMode;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.LayoutSourceEventConverter;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;
import net.zamasoft.pdfg2d.pdf.font.ConfigurablePDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;

/**
 * TwoPass T3b: 匿名項目の合成イベント(二段階プロトコル)と ANONYMOUS_CHILDREN の範囲再生。
 */
public final class AnonymousItemRangeTest extends TestCase {
	private static final List<String> FIXTURES = List.of("whitespace", "text-absolute", "float-text",
			"generated", "before", "nested-in-range");
	private static File fixture(final String name) {
		return new File("files/unittest/0500-twopass-range/anon-" + name + ".html");
	}

	/** 匿名項目の範囲再生・分類と収支を固定する。 */
	public void testFixtureRangesAndCensus() throws Exception {
		for (final String name : FIXTURES) {
			try (final var census = ContinuationStats.beginTwoPassCensus()) {
				ContinuationStats.reset();
				TwoPassFlowSealTest.render(fixture(name));
				final long anonymous = itemBinds(census.snapshot(TwoPassCensusEvent.BIND), true);
				if (name.equals("whitespace")) assertEquals(0, anonymous);
				else assertTrue(name + ": 匿名range bind未発火", anonymous > 0);
				RangeOnlyInvariantTest.assertCensus(name, census);
				TwoPassFlowSealTest.assertLeaseBalance(name);
			}
		}
	}

	private static long itemBinds(final Map<TwoPassCensusKey, Long> binds, final boolean anonymousOnly) {
		return binds.entrySet().stream().filter(entry ->
				(entry.getKey().rootKind() == TwoPassRootKind.GRID_ITEM || entry.getKey().rootKind() == TwoPassRootKind.FLEX_ITEM)
				&& (!anonymousOnly || entry.getKey().itemKind() == TwoPassItemKind.ANONYMOUS))
				.mapToLong(Map.Entry::getValue).sum();
	}

	/** Grid/Flex自身を根とするMEASUREとMAINの両方で匿名本文が再生される。 */
	public void testIntrinsicHostRangesAndCensus() throws Exception {
		for (final String display : List.of("grid", "flex")) {
			for (final String width : List.of("max-content", "fit-content")) {
				for (final String body : List.of("text", "mixed")) {
					final String name = display + "/" + width + "/" + body;
					final File file = TwoPassGridFlexRangeTest.fixtureFile("anon-" + display + "-" + width + "-" + body);
					try (final var census = ContinuationStats.beginTwoPassCensus()) {
						ContinuationStats.reset();
						final var pages = TwoPassFlowSealTest.render(file);
						TwoPassGridFlexRangeTest.assertGoldenPages("0500-twopass-range_" + file.getName().replace(".html", ""), pages);
						assertEquals(name + ": 頁数", 1, pages.size());
						final String page = new String(pages.get(0), java.nio.charset.StandardCharsets.UTF_8);
						for (final String text : body.equals("text") ? List.of("TEXT") : List.of("HEAD", "ITEM", "TAIL")) {
							assertTrue(name + ": 本文欠落 " + text, page.contains("Text[\"" + text + "\""));
						}
						final var binds = census.snapshot(TwoPassCensusEvent.BIND);
						final var measures = census.snapshot(TwoPassCensusEvent.MEASURE_RANGE);
						assertTrue(name + ": 匿名MAIN未発火", binds.entrySet().stream().anyMatch(entry ->
								entry.getKey().itemKind() == TwoPassItemKind.ANONYMOUS
								&& !entry.getKey().measurement() && entry.getValue() > 0));
						assertTrue(name + ": 匿名MEASURE未発火", measures.entrySet().stream().anyMatch(entry ->
								entry.getKey().itemKind() == TwoPassItemKind.ANONYMOUS
								&& entry.getKey().measurement() && entry.getValue() > 0));
						assertTrue(name + ": 宿主range未発火", binds.entrySet().stream().anyMatch(entry ->
								entry.getKey().rootKind() == TwoPassRootKind.TOPLEVEL && entry.getValue() > 0));
						RangeOnlyInvariantTest.assertCensus(name, census);
						TwoPassFlowSealTest.assertLeaseBalance(name);
					}
				}
			}
		}
	}

	/** liveログそのものをseal時に読む。Startは最初のtextより前、Endは次の実Startより前。 */
	public void testSyntheticBoundaryPositions() throws Exception {
		final AtomicInteger textStarts = new AtomicInteger(), elementEnds = new AtomicInteger();
		final Consumer<RangeHandle> onSeal = handle -> {
			if (handle.replayMode() != ReplayMode.ANONYMOUS_CHILDREN) return;
			final LayoutSource source = handle.source();
			final long startId = handle.fromId() - 1, endId = handle.toId() + 1;
			assertTrue(source.get(startId) instanceof LayoutSource.AnonymousItemStart);
			assertEquals(startId, ((LayoutSource.AnonymousItemStart) source.get(startId)).anchor());
			assertEquals(endId, source.endOf(startId));
			assertTrue(source.get(endId) instanceof LayoutSource.AnonymousItemEnd);
			assertTrue("textの後へStartを挿入した", source.get(handle.fromId()) instanceof LayoutSource.Chars);
			textStarts.incrementAndGet();
			if (source.get(endId + 1) instanceof LayoutSource.Start) elementEnds.incrementAndGet();
			else assertTrue("匿名Endの後に実終端がない", source.get(endId + 1) instanceof LayoutSource.EndBlock);
		};
		try (final AutoCloseable observer = observe(RangeHandle.class, "sealObserver", onSeal)) {
			TwoPassFlowSealTest.render(fixture("text-absolute"));
		}
		System.err.println("[T3b boundaries] text starts=" + textStarts + " before element=" + elementEnds);
		assertTrue("匿名Start未観測", textStarts.get() > 0);
		assertTrue("次の実Startより前の匿名End未観測", elementEnds.get() > 0);
	}

	/** HTMLのblock化を介さず、sinkのpreDispatchとdocの実際の開閉を照合する。 */
	public void testDirectInlineReplacedAndNestedCoordinators() throws Exception {
		final PDFUserAgent ua = new PDFUserAgent() { };
		final FontManagerImpl fonts = new FontManagerImpl(ConfigurablePDFFontSourceManager.getDefaultFontSourceManager());
		try {
			for (final boolean grid : new boolean[] { true, false }) {
				try (final DirectInput input = new DirectInput(ua, textParams(new BlockParams(), fonts))) {
					input.start(itemHost(grid, fonts), null);
					input.start(new InlineBox(textParams(new InlineParams(), fonts), new InlinePos()),
							LayoutSource.AnonymousItemStart.class);
					input.replaced(new InlineReplacedBox(imageParams(fonts), new InlinePos()), null);
					input.end(null); // INLINE終端では匿名項目を閉じない。
					input.start(itemHost(!grid, fonts), LayoutSource.AnonymousItemEnd.class);
					input.replaced(new InlineReplacedBox(imageParams(fonts), new InlinePos()),
							LayoutSource.AnonymousItemStart.class);
					input.replaced(new FlowReplacedBox(imageParams(fonts), new FlowPos()),
							LayoutSource.AnonymousItemEnd.class);
					input.end(null); // 入れ子coordinatorを閉じる。
					input.replaced(new InlineReplacedBox(imageParams(fonts), new InlinePos()),
							LayoutSource.AnonymousItemStart.class); // 親へ戻った後にも開始できる。
					input.end(LayoutSource.AnonymousItemEnd.class);
					input.doc.end();
					assertTrue(input.source.isContextCompleteRange(0, input.source.nextId() - 1));
					final AtomicInteger starts = new AtomicInteger(), ends = new AtomicInteger();
					input.source.replay(0, input.source.nextId() - 1, event -> {
						if (event instanceof LayoutSource.AnonymousItemStart) starts.incrementAndGet();
						if (event instanceof LayoutSource.AnonymousItemEnd) ends.incrementAndGet();
					});
					assertEquals(3, starts.get());
					assertEquals(starts.get(), ends.get());
				}
			}
		} finally {
			fonts.close();
			ua.dispose();
		}
	}

	private static FlowBlockBox itemHost(final boolean grid, final FontManager fonts) {
		return grid ? new GridBox(textParams(new GridParams(), fonts), new FlowPos())
				: new FlexBox(textParams(new FlexParams(), fonts), new FlowPos());
	}

	public void testUnknownBoxFailureIncludesSourceContext() throws Exception {
		final PDFUserAgent ua = new PDFUserAgent() { };
		ua.getDocumentContext().setBaseURI(java.net.URI.create("file:/t4b-unknown.html"));
		final FontManagerImpl fonts = new FontManagerImpl(ConfigurablePDFFontSourceManager.getDefaultFontSourceManager());
		try (final DirectInput input = new DirectInput(ua, textParams(new BlockParams(), fonts))) {
			try {
				input.start(new FlowBlockBox(textParams(new BlockParams(), fonts), new FlowPos()) { }, null);
				fail("未知subclassを受理した");
			} catch (final java.lang.reflect.InvocationTargetException expected) {
				final var invariant = net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException.findIn(expected);
				assertNotNull(invariant);
				for (final String detail : List.of("uri=file:/t4b-unknown.html", "EventId=[0,0]", "box kind=", "owner state=")) {
					assertTrue(invariant.getMessage(), invariant.getMessage().contains(detail));
				}
				assertNotNull(invariant.getCause());
			}
		} finally {
			fonts.close();
			ua.dispose();
		}
	}

	private static final AtomicInteger ELEMENT_KEYS = new AtomicInteger();

	private static <T extends AbstractTextParams> T textParams(final T params, final FontManager fonts) {
		withFont(params);
		params.fontManager = fonts;
		// AbstractTextBox.addInline は親子の element が同一でないことを assert する(手組みでは null 同士になる)
		params.element = new net.zamasoft.foliojet.layout.segment.StructureToken(ELEMENT_KEYS.incrementAndGet(), "div", null,
				new org.xml.sax.helpers.AttributesImpl());
		params.lineBreakRules = new TextBreakingRules() {
			public boolean atomic(final char c1, final char c2) { return false; }
			public boolean canSeparate(final char c1, final char c2) { return false; }
		};
		params.color = net.zamasoft.pdfg2d.gc.paint.GrayColor.BLACK;
		return params;
	}

	private static ReplacedParams imageParams(final FontManager fonts) {
		final ReplacedParams params = textParams(new ReplacedParams(), fonts);
		params.image = new net.zamasoft.pdfg2d.g2d.image.RasterImageImpl(
				new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB));
		return params;
	}

	/** package-privateな実sinkを駆動し、期待した境界が実イベントの前に記録されたかを見る。 */
	private static final class DirectInput implements AutoCloseable {
		private final Class<?> type = Class.forName("net.zamasoft.foliojet.css.style.RecordingLayoutSink");
		private final DocumentBuilder doc;
		private final Object sink;
		private final LayoutSource source;

		DirectInput(final PDFUserAgent ua, final BlockParams params) throws Exception {
			final MeasurePageGenerator pages = new MeasurePageGenerator(ua, params, 300, 400);
			this.doc = new DocumentBuilder(new PageGenerator() {
				public net.zamasoft.foliojet.ua.UserAgent getUserAgent() { return ua; }
				public net.zamasoft.foliojet.layout.box.params.PageBreakMode getPageSide() { return pages.getPageSide(); }
				public net.zamasoft.foliojet.layout.box.impl.PageBox nextPage() { return pages.nextPage(); }
				public boolean drawPage(final net.zamasoft.foliojet.layout.box.impl.PageBox page,
						final boolean last, final boolean forced) { return pages.drawPage(page, last, forced); }
				public LayoutSource getLayoutSource() { return DirectInput.this.source; }
			});
			this.doc.setPageMode(DocumentBuilder.PAGE_MODE_NO_BREAK);
			final Constructor<?> constructor = this.type.getDeclaredConstructor(DocumentBuilder.class, long.class);
			constructor.setAccessible(true);
			this.sink = constructor.newInstance(this.doc, Long.MAX_VALUE);
			this.source = (LayoutSource) this.call("source", new Class<?>[0]);
		}

		private Object call(final String name, final Class<?>[] types, final Object... args) throws Exception {
			final var method = this.type.getDeclaredMethod(name, types);
			method.setAccessible(true);
			return method.invoke(this.sink, args);
		}

		private void boundary(final long id, final Class<?> expected) {
			final LayoutSource.Event first = this.source.get(id);
			if (expected == null) {
				assertFalse(first instanceof LayoutSource.AnonymousItemStart || first instanceof LayoutSource.AnonymousItemEnd);
			} else {
				assertTrue("境界が実イベントより前にない: " + first, expected.isInstance(first));
				assertNotNull(this.source.get(id + 1));
			}
		}

		void start(final INonReplacedBox box, final Class<?> boundary) throws Exception {
			final long id = this.source.nextId();
			this.call("start", new Class<?>[] { INonReplacedBox.class }, box);
			this.boundary(id, boundary);
		}

		void replaced(final AbstractReplacedBox box, final Class<?> boundary) throws Exception {
			final long id = this.source.nextId();
			this.call("replaced", new Class<?>[] { AbstractReplacedBox.class }, box);
			this.boundary(id, boundary);
		}

		void end(final Class<?> boundary) throws Exception {
			final long id = this.source.nextId();
			this.call("end", new Class<?>[0]);
			this.boundary(id, boundary);
		}

		public void close() {
			this.source.close();
		}
	}

	private record ReplayProbe(long nextId, int size, LayoutSource.RetentionLease lease) { }

	/** 親float/セルの再生中にも項目が作り直されるが、主ログは伸びない。 */
	public void testReplayDoesNotAppend() throws Exception {
		final Map<RangeHandle, ReplayProbe> active = new IdentityHashMap<>();
		final AtomicInteger replays = new AtomicInteger(), rebuiltAnonymous = new AtomicInteger();
		final BiConsumer<RangeHandle, ReplayIntent> before = (handle, intent) -> {
			// compactによる保持件数の減少も止め、nextIdとsizeを独立に照合する。
			final LayoutSource source = handle.source();
			assertNull(active.put(handle, new ReplayProbe(source.nextId(), source.size(), source.retainFrom(0))));
		};
		final BiConsumer<RangeHandle, ReplayIntent> after = (handle, intent) -> {
			final ReplayProbe probe = active.remove(handle);
			assertNotNull(probe);
			try {
				assertEquals("範囲再生中にEventIdが増えた", probe.nextId(), handle.source().nextId());
				assertEquals("範囲再生中にログ長が変わった", probe.size(), handle.source().size());
				replays.incrementAndGet();
			} finally {
				probe.lease().close();
			}
		};
		final Consumer<RangeHandle> onSeal = handle -> {
			if (handle.replayMode() == ReplayMode.ANONYMOUS_CHILDREN && !active.isEmpty()) {
				rebuiltAnonymous.incrementAndGet();
			}
		};
		try (final AutoCloseable start = observe(RangeHandle.class, "replayStartObserver", before);
				final AutoCloseable end = observe(RangeHandle.class, "replayObserver", after);
				final AutoCloseable seal = observe(RangeHandle.class, "sealObserver", onSeal)) {
			TwoPassFlowSealTest.render(fixture("nested-in-range"));
			assertTrue("未終了の再生", active.isEmpty());
		} finally {
			for (final ReplayProbe probe : active.values()) probe.lease().close();
		}
		System.err.println("[T3b replay] checked=" + replays + " rebuilt anonymous=" + rebuiltAnonymous);
		assertTrue("範囲再生未発火", replays.get() > 0);
		assertTrue("親範囲からの匿名項目再構築未発火", rebuiltAnonymous.get() > 0);
	}

	/** 通常HTMLの空白はStyleEventMachineで先に捨てられる場合もある。件数は固定しない。 */
	public void testWhitespaceItemsAreDropped() throws Exception {
		final long grid = GridBuilder.GRID_ITEM_EMPTY_ANON_DROPS.get();
		final long flex = FlexBuilder.FLEX_ITEM_EMPTY_ANON_DROPS.get();
		final AtomicInteger starts = new AtomicInteger(), anonymousSeals = new AtomicInteger();
		final Consumer<LayoutSource.Event> onAppend = event -> {
			if (event instanceof LayoutSource.AnonymousItemStart) starts.incrementAndGet();
		};
		final Consumer<RangeHandle> onSeal = handle -> {
			if (handle.replayMode() == ReplayMode.ANONYMOUS_CHILDREN) anonymousSeals.incrementAndGet();
		};
		try (final AutoCloseable append = observe(LayoutSource.class, "appendObserver", onAppend);
				final AutoCloseable seal = observe(RangeHandle.class, "sealObserver", onSeal)) {
			TwoPassFlowSealTest.render(fixture("whitespace"));
		}
		final long gridDrops = GridBuilder.GRID_ITEM_EMPTY_ANON_DROPS.get() - grid;
		final long flexDrops = FlexBuilder.FLEX_ITEM_EMPTY_ANON_DROPS.get() - flex;
		System.err.println("[T3b empty] starts=" + starts
				+ " grid drops=" + gridDrops + " flex drops=" + flexDrops);
		assertEquals("空白だけの本文にリースを取得した", 0, anonymousSeals.get());
	}

	/** StyleEventMachineの手前の空白除去に依存せず、B1の意味的な空判定を直接通す。 */
	public void testWhitespaceBodyDoesNotCreateAnItem() throws Exception {
		final FontManager fonts = (FontManager) Proxy.newProxyInstance(FontManager.class.getClassLoader(),
				new Class<?>[] { FontManager.class }, (proxy, method, args) -> {
					if (method.getName().equals("getFontListMetrics")) return new FontListMetrics(new FontMetrics[0]);
					throw new AssertionError("折り畳まれる空白からfont処理が発火: " + method.getName());
				});
		for (final boolean grid : new boolean[] { true, false }) {
			final TwoPassBlockBuilder host = new TwoPassBlockBuilder(null,
					new FlowBlockBox(withFont(new BlockParams()), new FlowPos()));
			final Constructor<?> constructor = (grid ? GridBuilder.class : FlexBuilder.class)
					.getDeclaredConstructor(Builder.class, grid ? GridBox.class : FlexBox.class);
			constructor.setAccessible(true);
			final ItemCoordinator coordinator = (ItemCoordinator) constructor.newInstance(host,
					grid ? new GridBox(withFont(new GridParams()), new FlowPos())
							: new FlexBox(withFont(new FlexParams()), new FlowPos()));
			final var drops = grid ? GridBuilder.GRID_ITEM_EMPTY_ANON_DROPS : FlexBuilder.FLEX_ITEM_EMPTY_ANON_DROPS;
			final long before = drops.get();
			try (final LayoutSource source = new LayoutSource()) {
				final long anchor = source.append(new LayoutSource.AnonymousItemStart(source.nextId()));
				final Builder body = coordinator.requireAnonymousItem(anchor);
				body.getRootBox().getBlockParams().fontManager = fonts;
				final StyledTextUnitizer text = new StyledTextUnitizer(body);
				text.startContainer();
				final char[] spaces = " \n\t ".toCharArray();
				source.appendChars(0, spaces, 0, spaces.length, false);
				text.characters(0, spaces, 0, spaces.length, false);
				text.endContainer();
				final long end = source.append(new LayoutSource.AnonymousItemEnd());
				assertTrue("空白Charsの本文範囲が空", end > anchor + 1);
				coordinator.itemClosed();
				assertFalse(coordinator.hasOpenItem());
				assertEquals("空白本文がslotを消費した", before + 1, drops.get());
				final Field items = coordinator.getClass().getDeclaredField("items");
				items.setAccessible(true);
				assertTrue("破棄した匿名項目がplanに残っている", ((List<?>) items.get(coordinator)).isEmpty());
			}
		}
	}

	/** 生成文字もソースで解決済みのイベントとして匿名本文に含める。 */
	public void testGeneratedTextIsRecorded() throws Exception {
		for (final String name : List.of("generated", "before")) {
			final AtomicInteger generated = new AtomicInteger();
			final Consumer<LayoutSource.Event> onAppend = event -> {
				if (event instanceof LayoutSource.Chars chars && chars.charOffset() < 0) generated.incrementAndGet();
			};
			try (final AutoCloseable append = observe(LayoutSource.class, "appendObserver", onAppend)) {
				TwoPassFlowSealTest.render(fixture(name));
			}
			System.err.println("[T3b generated] " + name + " events=" + generated);
			assertTrue(name + ": 生成文字未観測", generated.get() > 0);
		}
	}

	/** display:contentsの疑似要素はblock化されず、生成Charsが匿名本文の先頭になる。 */
	public void testGeneratedTextInsideAnonymousRange() throws Exception {
		final AtomicInteger generatedRanges = new AtomicInteger();
		final Consumer<RangeHandle> onSeal = handle -> {
			if (handle.replayMode() != ReplayMode.ANONYMOUS_CHILDREN) return;
			final LayoutSource source = handle.source();
			if (!(source.get(handle.fromId()) instanceof LayoutSource.Chars chars)
					|| chars.charOffset() >= 0
					|| !new String(chars.payload().freshChars()).equals("ANON GENERATED ")) return;
			assertTrue(source.get(handle.fromId() - 1) instanceof LayoutSource.AnonymousItemStart);
			assertTrue(source.get(handle.toId() + 1) instanceof LayoutSource.AnonymousItemEnd);
			assertEquals(handle.toId() + 1, source.endOf(handle.fromId() - 1));
			final StringBuilder tail = new StringBuilder();
			source.replay(handle.fromId() + 1, handle.toId(), event -> {
				if (event instanceof LayoutSource.Chars text && text.charOffset() >= 0) {
					tail.append(text.payload().freshChars());
				}
			});
			assertTrue("生成文字直後の本文が別項目になった: " + tail,
					tail.toString().contains("GRID TAIL") || tail.toString().contains("FLEX TAIL"));
			generatedRanges.incrementAndGet();
		};
		try (final AutoCloseable seal = observe(RangeHandle.class, "sealObserver", onSeal)) {
			final List<byte[]> range = TwoPassFlowSealTest.render(fixture("before"));
		}
		assertTrue("生成Charsを含むANONYMOUS_CHILDREN未観測", generatedRanges.get() > 0);
	}

	private static <T extends AbstractTextParams> T withFont(final T params) {
		params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		return params;
	}

	/** 構造走査・1:1変換・水位破棄を小さな実ログで確認する。 */
	public void testSyntheticStructureScans() {
		try (final LayoutSource source = new LayoutSource()) {
			final LayoutSource.Start flow = new LayoutSource.Start(BoxRecipe.freeze(
					LayoutSource.BoxKind.FLOW, new BlockParams(), new FlowPos()));
			final long root = source.append(flow);
			final long anon = source.append(new LayoutSource.AnonymousItemStart(source.nextId()));
			final long text = source.appendChars(0, "text".toCharArray(), 0, 4, false);
			final long child = source.append(flow);
			final long childEnd = source.append(new LayoutSource.EndBlock());
			final long anonEnd = source.append(new LayoutSource.AnonymousItemEnd());
			final long rootEnd = source.append(new LayoutSource.EndBlock());
			assertEquals(rootEnd, source.endOf(root));
			assertEquals(anonEnd, source.endOf(anon));
			assertEquals(childEnd, source.endOf(child));
			assertTrue(source.isContextCompleteRange(root, rootEnd));
			assertTrue(source.isContextCompleteRange(anon, anonEnd));
			assertFalse(source.isContextCompleteRange(anon, childEnd));
			assertFalse(source.isContextCompleteRange(text, anonEnd));
			assertEquals(child, source.tailBound(text, source.nextId()));
			assertEquals(anon, source.tailBound(anon, source.nextId()));
			assertFalse(source.containsOpaque(root, rootEnd));
			final List<SegmentEvent> events = new ArrayList<>();
			source.replay(anon, anonEnd, event -> events.add(LayoutSourceEventConverter.convert(event)));
			assertEquals(anonEnd - anon + 1, (long) events.size());
			assertEquals(new SegmentEvent.AnonymousItemStart(anon), events.get(0));
			assertTrue(events.get(events.size() - 1) instanceof SegmentEvent.AnonymousItemEnd);
			source.compact(child);
			assertNotNull(source.get(root));
			assertNotNull(source.get(anon));
			assertNull(source.get(text));
			assertEquals(anonEnd, source.endOf(anon));
			assertFalse(source.isIntact(anon, anonEnd));
			source.compact(source.nextId());
			assertEquals(0, source.size());
		}
	}

	private static AutoCloseable observe(final Class<?> type, final String name, final Object observer) throws Exception {
		final Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		final Object saved = field.get(null);
		field.set(null, observer);
		return () -> field.set(null, saved);
	}
}
