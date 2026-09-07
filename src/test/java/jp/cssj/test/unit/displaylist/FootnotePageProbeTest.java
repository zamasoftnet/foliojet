package jp.cssj.test.unit.displaylist;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import junit.framework.TestCase;
import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.FootnotePageProbe;
import net.zamasoft.foliojet.layout.FootnotePageProbeReport;
import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** Bの連続計測・回収と、Cの同頁予約・配達・既定経路の不変性を検査します。 */
public final class FootnotePageProbeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String AREA = "@footnote { float: bottom; writing-mode: horizontal-tb }";

	public void testDefaultAndHorizontalDoNotCreateProbe() throws Exception {
		final String fixture = fixture();
		for (final String html : List.of(fixture.replace(AREA, ""), fixture.replace("float: bottom", "float: block-end"),
				fixture.replace("writing-mode: vertical-rl", "writing-mode: horizontal-tb"))) {
			final Capture baseline = transcode(html, false, Map.of());
			final Capture actual = transcode(html, true, Map.of());
			assertFalse(baseline.failed());
			assertFalse(actual.failed());
			assertFalse(actual.pages().isEmpty());
			assertParity(baseline, actual);
			assertEquals(0L, actual.created());
			assertTrue(actual.reports().isEmpty());
		}
	}

	public void testFirstPageCallsHeightsAndCarryIn() throws Exception {
		final Capture actual = compare(fixture());
		assertEquals(2, actual.reports().size());
		final FootnotePageProbeReport first = actual.reports().get(0);
		assertEquals(1, first.generation());
		assertEquals(Set.of(0L, 1L), first.callIds());
		assertTrue(first.unmeasuredIds().isEmpty());
		assertEquals(0.0, first.h0(), 0.0);
		assertTrue(first.emitted());
		assertTrue(first.innerWidth() > 0);
		assertTrue(first.innerHeight() > 0);
		assertEquals(2, actual.notes().size());
		for (final Note note : actual.notes()) {
			assertEquals(note.height(), first.measuredHeights().get(note.id()), 0.01);
			assertEquals("呼び出しと同じ一頁目の帯", 1, note.page());
			assertTrue(note.text(), note.text().startsWith((note.id() + 1) + ". "));
		}
		assertEquals(first.measuredHeight() + 6, actual.metrics().get(0).inset(), 0.01);
		assertTrue(actual.metrics().stream().filter(page -> page.page() > 1).allMatch(page -> page.inset() == 0));
		assertEquals(Set.of(0L, 1L), actual.plans().get(0).reservedIds());
		final FootnotePageProbeReport second = actual.reports().get(1);
		assertEquals(2, second.generation());
		assertEquals(first.innerHeight(), second.innerHeight(), 0.0);
		assertEquals(first.measuredHeight() + 6, second.h0(), 0.01);
		try {
			first.measuredHeights().put(999L, 1.0);
			fail("台帳はimmutable");
		} catch (final UnsupportedOperationException expected) { }
	}

	public void testPageBreakInsideSingleChars() throws Exception {
		final Capture actual = compare(document("", "<p>" + "長い本文を読み進めます。".repeat(240) + "</p>"));
		assertTrue(actual.pages().size() >= 3);
		assertTrue("probe有効時は文字イベントを小さく刻む", actual.maxChars() <= 256);
		assertTrue(actual.reports().size() >= 3);
		assertTrue(actual.reports().stream().anyMatch(report -> report.completion() == FootnotePageProbeReport.Completion.DRAW_PAGE));
		assertTrue(actual.plans().stream().filter(plan -> !plan.reported()).allMatch(plan -> plan.reservedIds().isEmpty()));
	}

	public void testUnsplitNormalizedCharsCanCloseSeveralPages() throws Exception {
		// NFCは呼び出し境界に依存するため、この設定では元のCharsを一度に配達する。
		final Capture actual = transcode(document("", "<p>" + "長い本文を読み進めます。".repeat(240) + "</p>"),
				true, Map.of("input.normalize-text", "true"));
		assertFalse(actual.failed());
		assertTrue(actual.maxChars() > 256);
		assertTrue("一回のB配達中でも報告を落とさない", actual.reports().stream().anyMatch(report ->
				actual.reports().stream().filter(other -> other.eventId() == report.eventId()).count() >= 2));
		assertTrue("Cが報告なしのページへ実際に進んだ", actual.plans().stream().anyMatch(plan -> !plan.reported()));
		assertTrue(actual.plans().stream().filter(plan -> !plan.reported()).allMatch(plan -> plan.inset() == 0));
		assertProbeRan(actual);
	}

	public void testCharacterChunksKeepReplayAndAssignmentAnchors() throws Exception {
		// NFCで変わらない文字を使い、文字分割の有無だけを比較する。
		final String html = document("h1 { string-set: heading content(); font-size:12pt }"
				+ "@page { @top-center { content:string(heading); writing-mode:horizontal-tb } }",
				"<h1>見出し</h1><p>" + "本文とインラインの続き。".repeat(180) + "<span>末尾。</span></p>");
		final Capture chunked = transcode(html, true, Map.of());
		final Capture whole = transcode(html, true, Map.of("input.normalize-text", "true"));
		assertFalse(chunked.failed());
		assertFalse(whole.failed());
		assertTrue(chunked.maxChars() <= 256 && whole.maxChars() > 256);
		TwoPassFlowSealTest.assertPagesEqual("Charsの分割で再生文字・代入アンカーを変えない", whole.pages(), chunked.pages());
	}

	public void testRetainedTableAcrossPages() throws Exception {
		final String rows = "<tr><td>表のセルの本文です。</td><td>続きのセルです。</td></tr>".repeat(70);
		// 144ptの版面へ12ptの全角を8列+最後の1字。最後の行は表StartのendContainerで閉じる。
		final Capture actual = compare(document("table { table-layout:auto }",
				"文".repeat(97) + "<table>" + rows + "</table><p>表の後です。</p>"));
		assertTrue(actual.pages().size() >= 2);
		assertTrue(actual.reports().size() >= 2);
		assertTrue("Bを表の完了まで維持した", actual.retention().stream().anyMatch(state -> state.inputFinished()));
		assertTrue("BでもRetained表の入力を受けた", actual.tables().stream()
				.anyMatch(stage -> stage.probe() && stage.stage().equals("before-table-end")));
	}

	public void testLateFootnoteBodyIsUnmeasured() throws Exception {
		// literalだけのcallは通常文字として流れる。callのIDが載ってから注本文が始まるまでを長くする。
		final String css = ".note::footnote-call { content:'参照" + "本文が続きます。".repeat(240) + "' }";
		final Capture actual = compare(document(css, "<p>先頭<span class='note'>後着の注本文。</span>終端</p>"));
		final FootnotePageProbeReport first = actual.reports().get(0);
		assertTrue(first.callIds().contains(0L));
		assertTrue("未着の本文を高さ0として確定しない", first.unmeasuredIds().contains(0L));
		assertFalse(first.measuredHeights().containsKey(0L));
		assertFalse(actual.plans().get(0).reservedIds().contains(0L));
		assertEquals(1, actual.notes().size());
		assertTrue("後着本文はcallページに遡らない", actual.notes().get(0).page() > 1);
		assertTrue("callページの採番を本文到着まで保持", actual.notes().get(0).text().startsWith("1. "));
	}

	public void testAnonymousBoundariesAndAlternatingPages() throws Exception {
		// 通常の縦組みgridは単一列フローへfallbackし、匿名項目を合成しない。
		// hostだけ横組みにしてcoordinatorを使い、ページはbottom+縦組み(Bの生成条件)を保つ。
		final Capture anonymous = compare(document(".host { display:grid; writing-mode:horizontal-tb }",
				"<div class='host'>直接文字<span>インライン</span><p>要素項目</p>末尾文字</div>"));
		assertTrue("主ログへ合成境界を実際に追記する", anonymous.anonymousBoundaries() > 0);
		final Capture alternating = compare(document(".page { break-before:page }",
				("<div class='page'><p>本文<span class='note'>横書きの注。</span>続き</p><p>ページの末尾。</p></div>").repeat(4)));
		assertTrue(alternating.reports().size() >= 3);
		assertTrue("同じBが少なくとも3ページを確定する", alternating.reports().stream()
				.filter(report -> report.deliveredEvents() > 0).count() >= 3);

	}

	public void testEndOfInputReleasesOwner() throws Exception {
		final Capture actual = compare(document("", "<p>短い本文です。</p>"));
		assertEquals(1, actual.reports().size());
		assertEquals(FootnotePageProbeReport.Completion.END_OF_INPUT, actual.reports().get(0).completion());
	}

	public void testFailureWithOpenRetainedTableReleasesOwner() throws Exception {
		final String html = document("table { table-layout:auto }",
				"<p>先頭。</p><table><tr><td>" + "未完のセル。".repeat(600) + "</td></tr></table>");
		final Map<String, String> properties = Map.of("processing.retained-text-limit", "1024");
		final Capture baseline = transcode(html, false, properties);
		final Capture actual = transcode(html, true, properties);
		assertTrue(baseline.failed());
		assertTrue(actual.failed());
		assertTrue(actual.messages().contains(MessageCodes.ERROR_RETAINED_TEXT_LIMIT));
		assertParity(baseline, actual);
		assertProbeRan(actual);
		assertTrue(actual.retention().stream().anyMatch(state -> state.closed() && !state.inputFinished()));
		assertTrue(actual.retention().stream().anyMatch(state -> state.unfinishedTables() > 0));
	}

	public void testNamedPageTransitionsBelongToProbe() throws Exception {
		final String css = "@page alpha { margin:18pt } @page beta { margin:18pt } .a { page:alpha } .b { page:beta }";
		final Capture actual = compare(document(css, "<p>無名。</p><section class='a'><p>一。</p><p>二。</p></section>"
				+ "<section class='b'><p>三。</p><p>四。</p></section><p>無名へ戻る。</p>"));
		final List<String> main = actual.names().stream().filter(change -> !change.probe()).map(NameChange::name).toList();
		final List<String> probe = actual.names().stream().filter(NameChange::probe).map(NameChange::name).toList();
		assertEquals(List.of("alpha", "beta", ""), main);
		assertEquals("名前ごとの各ブロックで遷移を繰り返さない", main, probe);
		assertTrue(actual.reports().stream().anyMatch(report -> "alpha".equals(report.pageName())));
		assertTrue(actual.reports().stream().anyMatch(report -> "beta".equals(report.pageName())));
	}

	public void testLongDocumentReclaimsPinsResourcesAndPageAccounting() throws Exception {
		final String css = ".page { break-before:page } .atom { display:inline-block; writing-mode:horizontal-tb }";
		final String body = "<div class='page'><p>本文です。<span class='atom'>計測する短い文字。</span>続きです。</p></div>";
		final Capture shortRun = compare(document(css, body.repeat(16)));
		final Capture longRun = compare(document(css, body.repeat(64)));
		assertTrue(longRun.reports().size() >= 64);
		final List<FootnotePageProbe.Retention> live = longRun.retention().stream().filter(state -> !state.closed()).toList();
		final long inputEnd = longRun.retention().get(longRun.retention().size() - 1).nextId();
		assertTrue("pinを先頭に残さない", live.stream().anyMatch(state -> state.pin() > inputEnd / 2));
		assertTrue("未完宿主より先へpinを進めない", live.stream().allMatch(state -> state.pin() <= state.unfinishedFrom()));
		assertTrue("同じ短いページの繰り返しでは保持窓を文書長へ広げない",
				live.stream().allMatch(state -> state.nextId() - state.pin() < 128));
		// 短いinline-blockはEnd配達内でseal・MEASURE bindを終える。
		// 直後のreclaimで登録を回収するため、回収後は移動pinの1件だけでも正しい。
		assertTrue("完了した宿主の登録を回収する", live.stream()
				.anyMatch(state -> state.resourcesBeforeReclaim() > state.resources()));
		assertTrue(live.stream().allMatch(state -> state.resources() < 64 && state.structureTokens() < 16));
		assertTrue(live.stream().allMatch(state -> state.compactionRequests() <= 1));
		assertEquals("同一形のページを4倍にしても登録数の上限は増えない",
				shortRun.retention().stream().mapToInt(FootnotePageProbe.Retention::resources).max().orElseThrow(),
				live.stream().mapToInt(FootnotePageProbe.Retention::resources).max().orElseThrow());
		assertEquals("会計をページで区切り、文書累計にしない",
				shortRun.reports().stream().mapToLong(FootnotePageProbeReport::pageBytes).max().orElseThrow(),
				longRun.reports().stream().mapToLong(FootnotePageProbeReport::pageBytes).max().orElseThrow());
		final long pageBytes = longRun.reports().stream().mapToLong(FootnotePageProbeReport::pageBytes).max().orElseThrow();
		assertTrue(pageBytes > 0);
		assertTrue(longRun.reports().stream().mapToLong(FootnotePageProbeReport::pageBytes).sum() > pageBytes * 16);
		assertTrue(live.stream().allMatch(state -> state.currentBytes() <= pageBytes));
	}

	public void testPageAccountingKeepsOpenScopesAndMainAccount() {
		final var limit = new PDFUserAgent() { }.getRetainedTextLimit();
		try (limit; final var main = limit.enter("main");
				final var owner = new net.zamasoft.foliojet.layout.fragment.ScratchOwner(limit, "probe-page")) {
			limit.add(17);
			final net.zamasoft.foliojet.layout.RetainedTextLimit.Scope child;
			try (final var attachment = owner.attach()) {
				child = limit.enter("open-child");
				limit.add(80);
				assertEquals(80L, owner.finishPage());
				assertFalse(child.isClosed());
				limit.add(30);
			}
			owner.reclaimBefore(Long.MAX_VALUE);
			assertFalse("未終了のスコープは回収しない", child.isClosed());
			assertEquals(1, owner.registeredResourceCount());
			assertEquals(17L, limit.getCurrentBytes());
			try (final var attachment = owner.attach()) {
				assertEquals(30L, limit.getCurrentBytes());
				child.close();
				assertEquals(30L, owner.finishPage());
			}
			owner.reclaimBefore(Long.MAX_VALUE);
			assertEquals(0, owner.registeredResourceCount());
			assertEquals(17L, limit.getCurrentBytes());
		}
	}

	public void testPageWindowTransfersOnlyItsOwnText() {
		final var limit = new PDFUserAgent() { }.getRetainedTextLimit();
		try (limit; final var window = limit.pageWindow()) {
			window.add(512);
			try (final var host = limit.enter("C-two-pass")) {
				limit.add(100);
				window.remove(256);
				assertEquals("キューの消費後もC宿主の文字は残る", 356L, limit.getCurrentBytes());
				try (final var measurement = limit.measurement("B")) {
					limit.add(800);
					assertEquals(800L, limit.getCurrentBytes());
				}
				assertEquals(356L, limit.getCurrentBytes());
				window.close();
				assertEquals("window終了でCの会計をsuspend/清算しない", 100L, limit.getCurrentBytes());
			}
			assertEquals(0L, limit.getCurrentBytes());
		}
	}

	public void testRepeatedFootnoteHeadersKeepBoundedProbeLedger() throws Exception {
		final String table = "<table><thead><tr><th>見出し<span class='note'>見出しの注。</span></th></tr></thead><tbody>"
				+ "<tr><td>本文の行。</td></tr>".repeat(32) + "</tbody></table>";
		final String css = "table { table-layout:auto; border-spacing:0 } th,td { padding:0 }";
		final Capture shortRun = compare(document(css, table.repeat(4)));
		final Capture longRun = compare(document(css, table.repeat(16)));
		assertTrue("同じcallを複数の継続ページで走査する", longRun.reports().stream().anyMatch(report ->
				report.callIds().stream().anyMatch(id -> longRun.reports().stream().filter(other -> other.callIds().contains(id)).count() >= 3)));
		final int shortMax = shortRun.retention().stream().mapToInt(FootnotePageProbe.Retention::footnoteLedgerSize).max().orElseThrow();
		final int longMax = longRun.retention().stream().mapToInt(FootnotePageProbe.Retention::footnoteLedgerSize).max().orElseThrow();
		assertTrue("台帳が実際に登録された", shortMax > 0);
		assertEquals("反復表を四倍にしてもBの台帳件数は増えない", shortMax, longMax);
		assertTrue(longRun.retention().stream().filter(FootnotePageProbe.Retention::closed).allMatch(state -> state.footnoteLedgerSize() == 0));
	}

	public void testManyFootnotesUseFifoAndBandCap() throws Exception {
		final Capture actual = compare(document(".note { height:28pt }",
				"<p>本文" + "注<span class='note'>横書きの注。</span>".repeat(12) + "終端</p>"));
		assertEquals(12, actual.notes().size());
		for (int i = 0; i < 12; ++i) assertEquals("FIFOを飛び越さない", i, actual.notes().get(i).id());
		assertTrue(actual.metrics().stream().allMatch(page -> page.inset() <= 144 * 0.6 + 0.01));
		assertTrue(actual.notes().get(11).page() > actual.notes().get(0).page());
		assertTrue(actual.plans().get(0).reservedIds().size() < 12);
	}

	public void testDifferentNamedPageGeometryIsMeasured() throws Exception {
		final Capture actual = compare(document("@page narrow { margin:36pt } .narrow { page:narrow }",
				"<p>先頭。</p><section class='narrow'><p>本文<span class='note'>狭い頁の注。</span>続き。</p></section><p>最後。</p>"));
		assertTrue(actual.reports().stream().anyMatch(report -> "narrow".equals(report.pageName())));
		final FootnotePageProbeReport named = actual.reports().stream()
				.filter(report -> "narrow".equals(report.pageName()) && report.callIds().contains(0L)).findFirst().orElseThrow();
		assertEquals(108.0, named.innerWidth(), 0.01);
		assertEquals(108.0, named.innerHeight(), 0.01);
		assertTrue(actual.plans().stream().anyMatch(plan -> plan.generation() == named.generation() && plan.usable()));
		assertEquals(1, actual.notes().size());
	}

	public void testBlankNamedTransitionReportsDiscardAndContinuesGeneration() throws Exception {
		final Capture actual = compare(document("@page named { size:210pt 240pt } .named { page:named }",
				"<section class='named'><p>最初の本文。</p></section>"));
		assertTrue("未出力の白紙も報告する", actual.reports().stream().anyMatch(report -> !report.emitted()));
		final FootnotePageProbeReport blank = actual.reports().stream().filter(report -> !report.emitted()).findFirst().orElseThrow();
		assertTrue(blank.callIds().isEmpty());
		assertTrue("白紙を捨ててもBの世代は続く", actual.reports().stream()
				.anyMatch(report -> report.generation() > blank.generation() && report.emitted()));
		assertEquals("Cも白紙を出力しない", 1, actual.pages().size());
	}

	public void testProbeAndListenerFailuresUseConversionFailurePolicy() throws Exception {
		for (final String failure : List.of("probe", "listener")) {
			final String html = document("table { writing-mode:horizontal-tb }",
					"<p>本文。</p><table><tr><td>実文字を含むセル。</td></tr></table>");
			final Capture actual = transcode(html, true, Map.of("processing.fail-on-fatal-error", "true"), failure);
			assertTrue(failure + "の例外は変換失敗", actual.failed());
			assertTrue(actual.messages().contains(jp.cssj.cti2.helpers.CTIMessageCodes.FATAL_UNEXPECTED));
			assertEquals("例外を投げる経路を実際に通った", 1L, actual.injectedFailures());
			assertProbeRan(actual);
			assertTrue(actual.retention().stream().anyMatch(state -> state.closed() && !state.inputFinished()));
		}
	}

	public void testAllInitialPageSidesFollowProductionTransitionsWithoutAdvancingUA() throws Exception {
		final Class<?> type = Class.forName("net.zamasoft.foliojet.css.style.PageSequence");
		final var constructor = type.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		final var query = type.getDeclaredMethod("footnotePageElement", int.class);
		query.setAccessible(true);
		final CSSElement[] previousSides = { null, CSSElement.PAGE_FIRST_RIGHT, CSSElement.PAGE_LEFT_EVEN,
				CSSElement.PAGE_RIGHT_ODD, CSSElement.PAGE_FIRST_LEFT, CSSElement.PAGE_LEFT_ODD,
				CSSElement.PAGE_RIGHT_EVEN, CSSElement.PAGE_SINGLE_FIRST, CSSElement.PAGE_SINGLE };
		for (final String mode : List.of("single-side", "double-side", "left-side", "right-side")) {
			for (final var bound : net.zamasoft.foliojet.ua.BoundSide.values()) {
				final PDFUserAgent ua = new PDFUserAgent() { };
				ua.setProperty("output.print-mode", mode);
				final var imposition = new net.zamasoft.foliojet.ua.impl.NopImposition(ua);
				final var context = new net.zamasoft.foliojet.css.StyleContext(new net.zamasoft.foliojet.css.CSSStyleSheet(), null, null);
				final Object sequence = constructor.newInstance(ua, context, imposition, null, null, (Consumer<String>) name -> { });
				imposition.setBoundSide(bound);
				ua.getPassContext().setPageNumber(42);
				for (final CSSElement previous : previousSides) {
					ua.getPassContext().setPageSide(previous);
					final CSSElement first = imposition.nextPageSide();
					hook(type, "firstPageElement").set(sequence, first);
					for (int emitted = 0; emitted < 8; ++emitted) {
						final CSSElement actual = ua.getPassContext().getPageSide();
						assertSame("全初期面のB/C一致: " + mode + "/" + bound + "/" + emitted,
								actual, query.invoke(sequence, emitted));
						assertSame("白紙棄却相当の再照会でも同じ面", actual, query.invoke(sequence, emitted));
						assertSame("過去の照会も可能", first, query.invoke(sequence, 0));
						assertSame("照会で本番の面を進めない", actual, ua.getPassContext().getPageSide());
						assertEquals("表示番号も進めない", 42, ua.getPassContext().getPageNumber());
						imposition.nextPageSide();
					}
				}
			}
		}
	}

	public void testSinkConsumesReportsAndDropsOlderGenerations() throws Exception {
		try (final var fonts = new net.zamasoft.pdfg2d.pdf.font.FontManagerImpl(
				net.zamasoft.pdfg2d.pdf.font.ConfigurablePDFFontSourceManager.getDefaultFontSourceManager())) {
			final Class<?> type = Class.forName("net.zamasoft.foliojet.css.style.RecordingLayoutSink");
			final var constructor = type.getDeclaredConstructor(net.zamasoft.foliojet.layout.DocumentBuilder.class, long.class);
			constructor.setAccessible(true);
			final PDFUserAgent ua = new PDFUserAgent() { };
			ua.getUAContext().setFootnoteArea(net.zamasoft.foliojet.ua.FootnoteArea.DEFAULT
					.withPosition(net.zamasoft.foliojet.ua.FootnoteArea.Position.BOTTOM));
			final var params = new net.zamasoft.foliojet.layout.box.params.BlockParams();
			params.fontStyle = net.zamasoft.foliojet.css.CSSStyle.getCSSStyle(ua, null, CSSElement.ANON).getFontStyle();
			params.fontManager = fonts;
			params.flow = net.zamasoft.foliojet.layout.box.params.WritingMode.RL;
			final var generator = new MeasurePageGenerator(ua, params, 200, 200);
			final AtomicLong mainGeneration = new AtomicLong(1);
			final var doc = new net.zamasoft.foliojet.layout.DocumentBuilder(generator) {
				@Override
				public long getPageGeneration() { return mainGeneration.get(); }
			};
			final Object sink = constructor.newInstance(doc, Long.MAX_VALUE);
			final var take = type.getDeclaredMethod("report", long.class);
			take.setAccessible(true);
			final var close = type.getDeclaredMethod("close");
			close.setAccessible(true);
			try {
				final var start = type.getDeclaredMethod("pageStarted", PageBox.class, double.class, double.class, String.class,
						java.util.function.BiFunction.class);
				start.setAccessible(true);
				start.invoke(sink, generator.nextPage(), 200.0, 200.0, null, null);
				final FootnotePageProbe probe = (FootnotePageProbe) hook(type, "probe").get(sink);
				assertNotNull("実際のsink受信経路を配線", probe);
				@SuppressWarnings("unchecked")
				final Consumer<FootnotePageProbeReport> receive = (Consumer<FootnotePageProbeReport>) hook(FootnotePageProbe.class, "listener").get(probe);
				@SuppressWarnings("unchecked")
				final Map<Long, FootnotePageProbeReport> reports = (Map<Long, FootnotePageProbeReport>) hook(type, "reports").get(sink);
				for (long generation = 1; generation <= 3; ++generation) {
					receive.accept(emptyReport(generation));
				}
				assertNotNull(take.invoke(sink, 2L));
				assertEquals("過去世代と採否判定へ渡した世代を回収し、将来分だけ残す", Set.of(3L), reports.keySet());
				assertNull("同じ報告を二度採用できない", take.invoke(sink, 2L));
				assertNotNull(take.invoke(sink, 3L));
				assertTrue("幾何不一致等で不採用になってもsinkに戻さない", reports.isEmpty());
				assertNull(take.invoke(sink, 4L));
				receive.accept(emptyReport(4));
				assertTrue("報告なしで固定した世代への後着はmapに残らない", reports.isEmpty());
				assertNull("後着を再取得できない", take.invoke(sink, 4L));
				receive.accept(emptyReport(5));
				assertEquals("未解決の将来世代は受信できる", Set.of(5L), reports.keySet());
				assertNotNull(take.invoke(sink, 5L));
				mainGeneration.set(7);
				receive.accept(emptyReport(6));
				assertTrue("明示消費していなくてもCより古い後着はmapに残らない", reports.isEmpty());
				receive.accept(emptyReport(7));
				assertEquals("Cの現世代は受信できる", Set.of(7L), reports.keySet());
			} finally {
				close.invoke(sink);
			}
		}
	}

	private static FootnotePageProbeReport emptyReport(final long generation) {
		return new FootnotePageProbeReport(generation, null, true, 0, 0, 200, 200,
				net.zamasoft.foliojet.layout.box.params.WritingMode.RL, Set.of(), Map.of(), Set.of(), 0, 1,
				FootnotePageProbeReport.Completion.DRAW_PAGE, 0);
	}

	public void testMovingPinReleasesCompletedHandlesAndCompactionRequests() {
		try (final LayoutSource source = new LayoutSource();
				final var owner = new net.zamasoft.foliojet.layout.fragment.ScratchOwner()) {
			for (int i = 0; i < 128; ++i) source.append(new LayoutSource.Chars(i, new char[] { 'x' }, false));
			owner.retainFrom(source, 0);
			final net.zamasoft.foliojet.layout.fragment.RangeHandle handle;
			try (final var attachment = owner.attach()) {
				handle = new net.zamasoft.foliojet.layout.fragment.RangeHandle(source, 0, 0,
						net.zamasoft.foliojet.layout.sizing.IntrinsicSizes.ZERO,
						net.zamasoft.foliojet.layout.fragment.RangeHandle.ReplayMode.CHILDREN_ONLY);
				try (final var lease = source.retainFrom(1)) { }
			}
			owner.reclaimBefore(handle.toId());
			assertEquals("境界以降のハンドルは回収しない",
					net.zamasoft.foliojet.layout.fragment.RangeHandle.State.OPEN, handle.state());
			assertEquals("保護中のハンドル・リースと移動pinを残す", 3, owner.registeredResourceCount());
			try (final var checkpoint = source.checkpointCompaction()) {
				for (long id = 1; id <= source.nextId(); ++id) source.compact(id);
				assertEquals(1, checkpoint.pendingRequestCount());
				assertEquals(128, source.size());
				owner.retainFrom(source, 64);
				owner.reclaimBefore(64);
				assertEquals(net.zamasoft.foliojet.layout.fragment.RangeHandle.State.ABANDONED, handle.state());
				assertEquals(1, owner.registeredResourceCount());
				checkpoint.reapply();
				assertEquals(64, source.size());
				assertNotNull(source.get(64));
				owner.retainFrom(source, 128);
				checkpoint.reapply();
				assertEquals(0, source.size());
				assertEquals(0, checkpoint.pendingRequestCount());
			}
		}
	}

	private static Capture compare(final String html) throws Exception {
		final Capture baseline = transcode(html, false, Map.of());
		final Capture actual = transcode(html, true, Map.of());
		assertFalse("比較元の変換成功", baseline.failed());
		assertFalse("Bを含む変換成功", actual.failed());
		assertFalse(actual.pages().isEmpty());
		assertEquals("listener無しでも本番のBを駆動する", 1L, baseline.created());
		assertParity(baseline, actual);
		assertProbeRan(actual);
		return actual;
	}

	private static void assertParity(final Capture baseline, final Capture actual) {
		TwoPassFlowSealTest.assertPagesEqual("観測の有無で出力を変えない", baseline.pages(), actual.pages());
		assertEquals("Bの有無で主ログの合成境界数は変わらない", baseline.anonymousBoundaries(), actual.anonymousBoundaries());
		assertFalse(actual.sources().isEmpty());
		assertEquals("清算前のLayoutSource保持量", baseline.sources(), actual.sources());
		assertTrue("Cとclose中にはBの接続を残さない", actual.mainScopes().stream().allMatch(Boolean::booleanValue));
	}

	private static void assertProbeRan(final Capture actual) {
		assertEquals("Bは文書先頭から1回だけ生成する", 1L, actual.created());
		final List<FootnotePageProbe.Retention> closed = actual.retention().stream().filter(FootnotePageProbe.Retention::closed).toList();
		assertEquals(1, closed.size());
		assertEquals(0, closed.get(0).leases());
		assertEquals(0, closed.get(0).resources());
		assertEquals(0L, closed.get(0).currentBytes());
		if (closed.get(0).inputFinished()) {
			assertEquals("Bの全生成ページを一度ずつ報告する", closed.get(0).pages(), actual.reports().size());
		}
		for (int i = 0; i < actual.reports().size(); ++i) {
			final FootnotePageProbeReport report = actual.reports().get(i);
			assertEquals((long) i + 1, report.generation());
			assertTrue("B未配達の一致を除外", report.deliveredEvents() > 0);
			assertTrue(report.eventId() >= 0);
		}
		assertFalse("主ログの清算を観測した", actual.sources().isEmpty());
	}

	private static String fixture() throws Exception {
		return Files.readString(Path.of("files/unittest/0125-footnote/footnote-bottom-vertical-rl.html"), StandardCharsets.UTF_8);
	}

	private static String document(final String css, final String body) {
		return "<!doctype html><?jp.cssj.property name='output.page-width' value='180pt'?>"
				+ "<?jp.cssj.property name='output.page-height' value='180pt'?>"
				+ "<html><head><meta charset='UTF-8'><style>@page { margin:18pt; " + AREA + " }"
				+ "body { margin:0; font-size:12pt; line-height:18pt; writing-mode:vertical-rl }"
				+ "p { margin:0 } .note { float:footnote; font-size:10pt; line-height:14pt }"
				+ css + "</style></head><body>" + body + "</body></html>";
	}

	private record Input(long ordinal, int length) { }
	private record TableStage(String stage, boolean probe) { }
	private record PageMetrics(int page, double inset) { }
	private record Note(long id, double height, int page, String text) { }
	private record NameChange(boolean probe, String name) { }
	private record Capture(List<byte[]> pages, List<FootnotePageProbeReport> reports, long created,
			List<LayoutSource.RetentionSnapshot> sources, List<Boolean> mainScopes, List<Input> pageInputs,
			List<TableStage> tables, long anonymousBoundaries, List<PageMetrics> metrics, List<Note> notes,
			boolean failed, List<Short> messages, List<FootnotePageProbe.Retention> retention, List<NameChange> names,
			List<RootBuilder.FootnotePlanSnapshot> plans, long maxChars, long injectedFailures) { }

	private static Field hook(final Class<?> type, final String name) throws Exception {
		final Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	/** ThreadLocalは変換スレッドのcallback内だけで読み、値を並行コレクションへ移します。 */
	private static Capture transcode(final String html, final boolean observe, final Map<String, String> properties)
			throws Exception {
		return transcode(html, observe, properties, "");
	}

	private static Capture transcode(final String html, final boolean observe, final Map<String, String> properties,
			final String failure) throws Exception {
		final CaptureUserAgent ua = new CaptureUserAgent();
		final ConcurrentLinkedQueue<byte[]> pages = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<FootnotePageProbeReport> reports = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<LayoutSource.RetentionSnapshot> sources = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<Boolean> mainScopes = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<Input> pageInputs = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<TableStage> tables = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<Short> messages = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<FootnotePageProbe.Retention> retention = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<NameChange> names = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<RootBuilder.FootnotePlanSnapshot> plans = new ConcurrentLinkedQueue<>();
		final AtomicLong maxChars = new AtomicLong();
		final AtomicReference<Input> input = new AtomicReference<>(new Input(0, 0));
		final AtomicLong boundaries = new AtomicLong();
		final AtomicLong injected = new AtomicLong();
		ua.getUAContext().setFootnotePageProbeListener(observe ? report -> {
			reports.add(report);
			if (failure.equals("listener") && injected.getAndIncrement() == 0) throw new IllegalStateException("F-5 listener failure");
		} : null);
		final Field append = hook(LayoutSource.class, "appendObserver");
		final Field close = hook(LayoutSource.class, "beforeCloseObserver");
		final Field table = hook(RetainedTableBuilder.class, "retentionPlanObserver");
		final Field tableStack = hook(RetainedTableBuilder.class, "layoutStack");
		final Field retained = hook(FootnotePageProbe.class, "retentionObserver");
		final Field named = hook(RootBuilder.class, "pageNameObserver");
		final Field planned = hook(RootBuilder.class, "footnotePlanObserver");
		final Field sealed = hook(net.zamasoft.foliojet.layout.fragment.RangeHandle.class, "sealObserver");
		final Object savedSealed = sealed.get(null);
		final Object savedPlanned = planned.get(null);
		final Object savedRetained = retained.get(null), savedNamed = named.get(null);
		final Object savedAppend = append.get(null), savedClose = close.get(null), savedTable = table.get(null);
		boolean failed = false;
		try (final AutoCloseable output = DisplayListDumper.observePages((drawer, number) -> {
			final StringBuilder text = new StringBuilder();
			drawer.dump(text, "");
			pages.add(text.toString().getBytes(StandardCharsets.UTF_8));
			pageInputs.add(input.get());
			mainScopes.add(ReplayIntent.current() == ReplayIntent.MAIN && ScratchReplayScope.currentOwner() == null);
		})) {
			retained.set(null, (Consumer<FootnotePageProbe.Retention>) retention::add);
			if (failure.equals("probe")) sealed.set(null, (Consumer<net.zamasoft.foliojet.layout.fragment.RangeHandle>) range -> {
				if (ReplayIntent.current() == ReplayIntent.MEASURE && injected.getAndIncrement() == 0) {
					throw new IllegalStateException("F-5 probe seal failure");
				}
			});
			planned.set(null, (Consumer<RootBuilder.FootnotePlanSnapshot>) plans::add);
			named.set(null, (BiConsumer<RootBuilder, String>) (root, name) -> names.add(new NameChange(
					root.getPageGenerator() instanceof MeasurePageGenerator measure && measure.isFootnoteProbe(), name == null ? "" : name)));
			append.set(null, (Consumer<LayoutSource.Event>) event -> {
				input.set(new Input(input.get().ordinal() + 1, event instanceof LayoutSource.Chars chars ? chars.payload().utf16Length() : 0));
				maxChars.accumulateAndGet(input.get().length(), Math::max);
				// 主ログへの全追記を数える。
				// Bの不在中・最初のページ通知前も対象。Bへの配達境界数を表すものではない。
				if (event instanceof LayoutSource.AnonymousItemStart || event instanceof LayoutSource.AnonymousItemEnd) boundaries.incrementAndGet();
			});
			close.set(null, (Consumer<LayoutSource>) source -> {
				sources.add(source.retentionSnapshot());
				mainScopes.add(ReplayIntent.current() == ReplayIntent.MAIN && ScratchReplayScope.currentOwner() == null);
			});
			table.set(null, (BiConsumer<String, RetainedTableBuilder>) (stage, builder) -> {
				try {
					final var stack = (net.zamasoft.foliojet.layout.builder.LayoutStack) tableStack.get(builder);
					final RootBuilder root = stack.getPageContext();
					tables.add(new TableStage(stage, root != null
							&& root.getPageGenerator() instanceof MeasurePageGenerator measure && measure.isFootnoteProbe()));
				} catch (final IllegalAccessException e) {
					throw new AssertionError(e);
				}
			});
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
			try {
				session.setUserAgent(ua);
				session.setResults(new SingleResult(new StreamFragmentedOutput(OutputStream.nullOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.setMessageHandler((code, args, message) -> messages.add(code));
				session.property("input.property-pi", "true");
				session.property("processing.pass-count", "1");
				for (final var property : properties.entrySet()) session.property(property.getKey(), property.getValue());
				try {
					CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
							URI.create("file:///footnote-probe.html"), "text/html", "UTF-8");
				} catch (final TranscoderException expected) {
					failed = true;
				}
			} finally {
				session.close();
			}
		} finally {
			append.set(null, savedAppend);
			close.set(null, savedClose);
			table.set(null, savedTable);
			retained.set(null, savedRetained);
			named.set(null, savedNamed);
			planned.set(null, savedPlanned);
			sealed.set(null, savedSealed);
		}
		return new Capture(List.copyOf(pages), List.copyOf(reports), ua.getUAContext().getFootnotePageProbeCount(),
				List.copyOf(sources), List.copyOf(mainScopes), List.copyOf(pageInputs), List.copyOf(tables), boundaries.get(),
				List.copyOf(ua.metrics), List.copyOf(ua.notes), failed, List.copyOf(messages), List.copyOf(retention), List.copyOf(names),
				List.copyOf(plans), maxChars.get(), injected.get());
	}

	private static final class CaptureUserAgent extends PDFUserAgent {
		private final ConcurrentLinkedQueue<PageMetrics> metrics = new ConcurrentLinkedQueue<>();
		private final ConcurrentLinkedQueue<Note> notes = new ConcurrentLinkedQueue<>();

		@Override
		public void prepare(final PrepareMode mode) {
			super.prepare(mode);
			this.visitor = new CaptureVisitor(this, this);
		}
	}

	private static final class CaptureVisitor extends PDFVisitor {
		private final CaptureUserAgent capture;
		private int page;
		private boolean mainPage;

		CaptureVisitor(final UserAgent ua, final CaptureUserAgent capture) {
			super(ua);
			this.capture = capture;
		}

		@Override
		public void nextPage(final PDFGC gc) {
			super.nextPage(gc);
			++this.page;
			this.mainPage = true;
		}

		@Override
		public void visitBox(final AffineTransform transform, final IBox box, final Drawer drawer, final double x, final double y) {
			super.visitBox(transform, box, drawer, x, y);
			if (this.mainPage && box instanceof PageBox pageBox) {
				this.mainPage = false;
				this.capture.metrics.add(new PageMetrics(this.page, pageBox.getFootInset()));
			}
			if (box instanceof FloatBlockBox note && box.getPos() instanceof FootnotePos) {
				final StringBuilder text = new StringBuilder();
				note.getText(text);
				this.capture.notes.add(new Note(note.getParams().footnoteId, RootBuilder.footnoteBandExtent(note), this.page, text.toString()));
			}
		}
	}
}
