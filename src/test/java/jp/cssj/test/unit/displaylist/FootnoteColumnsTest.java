package jp.cssj.test.unit.displaylist;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import junit.framework.TestCase;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.FootnotePageProbe;
import net.zamasoft.foliojet.layout.FootnotePageProbeReport;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.content.ColumnsContainer;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** F-4: 段組の包含寸法・ページ共通の帯・例外経路と長文の保持窓。 */
public final class FootnoteColumnsTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String AREA = "@footnote { float: bottom; writing-mode: horizontal-tb }";
	private static final double EPSILON = 0.01;

	public void testCountAndBalanceUseReservedContainingHeight() throws Exception {
		for (final String fill : List.of("auto", "balance")) {
			final Capture capture = transcode(fixture().replace("column-fill: balance", "column-fill: " + fill));
			assertEquals(2, capture.pages.size());
			assertEquals(2, capture.notes.size());
			assertColumns(capture);
			assertTrue(capture.columns.stream().allMatch(column -> column.count() == 2));
			if (fill.equals("balance")) {
				assertTrue("均等割りで実際に二段を構築", capture.columns.stream().anyMatch(column -> column.actual() == 2));
			}
			for (final Page page : capture.pages) assertTrue("両ページとも帯を予約", page.inset() > 0);
		}
	}

	public void testColumnWidthIsResolvedAfterReservation() throws Exception {
		final String html = fixture().replace("column-count: 2", "column-width: 75pt");
		final Capture capture = transcode(html);
		assertColumns(capture);
		assertEquals(2, capture.notes.size());
		for (final Column column : capture.columns) {
			assertEquals("帯なしなら三段、予約後は二段になる幅", 2, column.count());
		}
		final Capture baseline = transcode(html.replace(AREA, ""));
		assertFalse(baseline.columns.isEmpty());
		assertTrue("column-widthの段数判定を実際に跨ぐ", baseline.columns.stream().allMatch(column -> column.count() == 3));
	}

	public void testTwoCallsShareOneBandAndOneGap() throws Exception {
		final Capture capture = transcode(document(".columns { column-count:2; column-gap:12pt; column-fill:balance }",
				"<div class='columns'><p>一<span class='note'>第一の注。</span>本文。</p>"
				+ "<p>二<span class='note'>第二の注。</span>本文。</p></div>"));
		assertEquals(1, capture.pages.size());
		assertEquals(2, capture.notes.size());
		assertColumns(capture);
		assertTrue(capture.columns.stream().anyMatch(column -> column.actual() == 2));
		assertEquals("ページ共通の帯でgapを一度だけ足す",
				6 + capture.notes.stream().mapToDouble(note -> note.placement().height()).sum(), capture.page(1).inset(), EPSILON);
		for (final Note note : capture.notes) assertEquals(252.0, note.placement().width(), EPSILON);
	}

	public void testColumnWidthCanResolveToOneColumn() throws Exception {
		final Capture capture = transcode(fixture().replace("column-count: 2", "column-width: 130pt"));
		assertColumns(capture);
		assertTrue(capture.columns.stream().allMatch(column -> column.count() == 1));
		assertEquals(2, capture.notes.size());
	}

	public void testBodyColumnsAndSpanUseTheSameContainingHeight() throws Exception {
		final String html = document("body { column-count:2; column-gap:12pt; column-fill:balance }"
				+ ".span { column-span:all }", "<p>先頭<span class='note'>横書きの注。</span>本文。</p>"
				+ "<p>段の本文。</p><p class='span'>ぶち抜きの見出し。</p><p>再開した段。</p><p>最後の段。</p>");
		final Capture capture = transcode(html);
		assertEquals(1, capture.pages.size());
		assertEquals(1, capture.notes.size());
		assertColumns(capture);
		assertTrue("spanの後に段組を開き直す", capture.columns.size() >= 2);
		assertFalse("ぶち抜き箱を実際に配置", capture.spans.isEmpty());
		for (final Placement span : capture.spans) {
			assertEquals("spanは段の行長でなく予約後の全高", capture.page(span.page()).innerHeight(), span.height(), EPSILON);
		}
	}

	public void testContinuousColumnsResizeOnFollowingPage() throws Exception {
		final Capture capture = transcode(document(".columns { column-count:2; column-gap:12pt; column-fill:auto }",
				"<div class='columns'><p>先頭<span class='note'>第一頁の注。</span></p>"
				+ "<p>段組の本文です。</p>".repeat(44) + "</div>"));
		assertTrue("一つの段組が自然改頁する", capture.pages.size() >= 2);
		assertTrue(capture.pages.get(0).inset() > 0);
		assertEquals("同頁に置いた注を次頁で二重予約しない", 0.0, capture.pages.get(1).inset(), 0.0);
		assertTrue("二頁目に継続段がある", capture.columns.stream().anyMatch(column -> column.page() == 2));
		assertColumns(capture);
		assertEquals(1, capture.notes.size());
	}

	public void testFirstNoteOnSecondNaturalPageShrinksContinuingColumns() throws Exception {
		final Capture capture = transcode(document(".columns { column-count:2; column-gap:12pt; column-fill:auto }",
				"<div class='columns'>" + "<p>本文。</p>".repeat(30)
				+ "<p>参照<span class='note'>二頁目の注。</span>続き。</p>" + "<p>末尾。</p>".repeat(4) + "</div>"));
		assertTrue("強制改頁なしで二頁目へ進む", capture.pages.size() >= 2);
		assertEquals(0.0, capture.page(1).inset(), 0.0);
		assertEquals(1, capture.calls.size());
		assertEquals(2, capture.calls.get(0).page());
		assertEquals(1, capture.notes.size());
		assertEquals("初出の注も同頁", 2, capture.notes.get(0).placement().page());
		assertTrue(capture.page(2).inset() > 0);
		assertTrue(capture.columns.stream().anyMatch(column -> column.page() == 2));
		assertColumns(capture);
	}

	public void testPageNumbersRestartAndFooterStaysOutsideBand() throws Exception {
		final Capture capture = transcode(fixture());
		// 規則だけを消すと既定block-endの段組脚注になり、このfixtureは一頁になる。
		// 柱の比較用には注も非表示にし、帯なしの二頁を組む。
		final Capture baseline = transcode(fixture().replace(AREA, "").replace("float: footnote", "display: none"));
		assertEquals(2, capture.pages.size());
		assertEquals("柱の比較対象も二ページ", 2, baseline.pages.size());
		assertTrue("比較対象は脚注なし", baseline.notes.isEmpty());
		assertEquals(2, capture.notes.size());
		assertEquals(2, capture.calls.size());
		for (int pageNumber = 1; pageNumber <= 2; ++pageNumber) {
			final int number = pageNumber;
			final List<Label> calls = capture.calls.stream().filter(call -> call.page() == number).toList();
			final List<Note> notes = capture.notes.stream().filter(note -> note.placement().page() == number).toList();
			assertEquals("各ページのcallを観測", 1, calls.size());
			assertEquals("callと同頁の帯に載る", 1, notes.size());
			assertEquals(calls.get(0).id(), notes.get(0).id());
			assertEquals("callの番号は毎頁1", "1", calls.get(0).text().trim());
			assertTrue("注もcallページの番号", notes.get(0).placement().text().startsWith("1. "));
			final Placement footer = footer(capture, number);
			assertEquals("ノンブルの座標・寸法・文字は帯なしと同じ", footer(baseline, number), footer);
			final Page page = capture.page(number);
			assertEquals(300.0, page.height(), 0.0);
			assertEquals(24.0, page.marginBottom(), 0.0);
			final Placement note = notes.get(0).placement();
			assertEquals("帯は段幅によらず版面全幅", 252.0, note.width(), EPSILON);
			assertEquals(0.0, note.x(), EPSILON);
			assertTrue(note.y() >= page.innerHeight() + 6 - EPSILON);
			assertTrue("脚注が下余白へ出ない", note.y() + note.height() <= 252 + EPSILON);
			assertTrue("段組でも柱と重ならない", note.y() + note.height() <= footer.y() + EPSILON);
		}
	}

	public void testColumnWarningOnlyChangesForVerticalBottom() throws Exception {
		final Logger logger = Logger.getLogger("net.zamasoft.foliojet.css.style.StyleEventMachine");
		final AtomicLong warnings = new AtomicLong();
		final Handler handler = new Handler() {
			public void publish(final LogRecord record) {
				if (record.getMessage().contains("footnote inside a multi-column ancestor")) warnings.incrementAndGet();
			}
			public void flush() { }
			public void close() { }
		};
		logger.addHandler(handler);
		try {
			transcode(fixture());
			assertEquals(0L, warnings.get());
			transcode(fixture().replace("float: bottom", "float: block-end"));
			assertTrue("既定の警告は残す", warnings.get() > 0);
			warnings.set(0);
			transcode(fixture().replace("writing-mode: vertical-rl", "writing-mode: horizontal-tb"));
			assertTrue("横組みbottomの警告も従来どおり", warnings.get() > 0);
		} finally {
			logger.removeHandler(handler);
		}
	}

	public void testNormalFloatCallIsMeasuredAndPlacedOnSamePage() throws Exception {
		assertHostCall(".host { float:left; writing-mode:horizontal-tb }", "<div class='host'>", "</div>", true);
	}

	public void testTableCellCallIsMeasuredAndPlacedOnSamePage() throws Exception {
		assertHostCall("table { writing-mode:horizontal-tb; border-spacing:0 } td { padding:0 }",
				"<table><tr><td>", "</td></tr></table>", true);
	}

	public void testPageFloatCallIsNotReportedAndUsesLaterBand() throws Exception {
		assertHostCall(".host { float:top; writing-mode:horizontal-tb }", "<div class='host'>", "</div>", false);
	}

	public void testAbsoluteCallIsNotReportedAndUsesLaterBand() throws Exception {
		assertHostCall(".host { position:absolute; top:0; left:0; writing-mode:horizontal-tb }",
				"<div class='host'>", "</div>", false);
	}

	public void testMarginNoteCallIsNotReportedAndUsesLaterBand() throws Exception {
		assertHostCall(".host { float:-cssj-note-start; writing-mode:horizontal-tb }",
				"<div class='host'>", "</div>", false);
	}

	public void testHostCallsInsideColumns() throws Exception {
		final String columns = ".columns { column-count:2; column-gap:12pt; column-fill:auto }";
		for (final String host : List.of("float", "cell", "absolute")) {
			final String css = columns + switch (host) {
			case "float" -> ".host { float:left; writing-mode:horizontal-tb }";
			case "cell" -> "table { writing-mode:horizontal-tb; border-spacing:0 } td { padding:0 }";
			default -> ".host { position:absolute; top:0; left:0; writing-mode:horizontal-tb }";
			};
			final String open = host.equals("cell") ? "<table><tr><td>" : "<div class='host'>";
			final String close = host.equals("cell") ? "</td></tr></table>" : "</div>";
			final Capture capture = assertHostCall(css, "<div class='columns'><p>段の本文。</p>" + open,
					close + "<p>段の末尾。</p></div>", !host.equals("absolute"));
			assertColumns(capture);
		}
	}

	public void testDefaultBlockEndFloatCallsInsideColumnsStayOnCallPage() throws Exception {
		for (final String flow : List.of("vertical-rl", "horizontal-tb")) {
			// 短い本文とfloatの均等割りは実段数を保証しない。明示改段で
			// ColumnsContainerを作り、両書字方向で段内floatの走査を検査する。
			final String html = document(".columns { column-count:2; column-gap:12pt; column-fill:auto }"
					+ ".host { float:left } .next { page-break-before:always } .second { break-before:column }",
					"<p>先行<span class='note'>先行する注。</span>本文。</p><p class='next'>次頁の本文。</p>"
					+ "<div class='columns'><p>段の先頭。</p><div class='host'>呼出<span class='note'>段内の注。</span>続き</div>"
					+ "<p>段の本文。</p><p class='second'>次段の本文。</p></div>")
					.replace(AREA, "").replace("writing-mode:vertical-rl", "writing-mode:" + flow);
			final Capture capture = transcode(html);
			assertTrue("既定block-endではBを作らない", capture.reports.isEmpty());
			assertTrue("実際に複数段へ分ける: " + flow, capture.columns.stream().anyMatch(column -> column.actual() > 1));
			assertEquals(2, capture.calls.size());
			assertEquals(2, capture.notes.size());
			assertTrue("段内callは先行注とは別頁", capture.calls.get(1).page() > capture.calls.get(0).page());
			for (final Label call : capture.calls) {
				final Note note = capture.notes.stream().filter(value -> value.id() == call.id()).findFirst().orElseThrow();
				assertEquals("既定でも段内floatの注をcallと同頁へ: " + flow, call.page(), note.placement().page());
				assertEquals("文書通番でなく各頁の番号", "1", call.text().trim());
				assertTrue(note.placement().text(), note.placement().text().startsWith("1. "));
			}
			assertTrue("EOF救済を使わない: " + capture.footnoteWarnings, capture.footnoteWarnings.isEmpty());
		}
	}

	public void testContinuedPageSidesReserveAlternatingNamedDimensionsOnCallPage() throws Exception {
		// UAの面と表示番号を引き継ぐEPUB後続章相当。最初の面は順に
		// RIGHT_EVEN、SINGLE、RIGHT_ODD、LEFT_ODDとなる。
		final String[] modes = { "right-side", "single-side", "left-side", "double-side" };
		final CSSElement[] previous = { CSSElement.PAGE_FIRST_LEFT, CSSElement.PAGE_SINGLE_FIRST,
				CSSElement.PAGE_LEFT_EVEN, CSSElement.PAGE_RIGHT_EVEN };
		final CSSElement[] first = { CSSElement.PAGE_RIGHT_EVEN, CSSElement.PAGE_SINGLE,
				CSSElement.PAGE_RIGHT_ODD, CSSElement.PAGE_LEFT_ODD };
		final String body = "<p>参照<span class='note'>短い注。</span>本文。</p>"
				+ "<p class='next'>参照<span class='note'>次の注。</span>本文。</p>".repeat(3);
		final String html = document("html { page:named } .next { page-break-before:always }"
				+ "@page named { size:240pt 270pt; margin:18pt; padding:2pt; border:1pt solid }"
				+ "@page named:left { size:260pt 290pt; margin:20pt; padding:3pt }"
				+ "@page named:right { size:320pt 350pt; margin:24pt; padding:4pt; border:2pt solid }", body);
		for (int i = 0; i < modes.length; ++i) {
			final Capture capture = transcode(html, modes[i], previous[i], 42);
			assertEquals(4, capture.calls.size());
			assertEquals(4, capture.notes.size());
			assertEquals("named", capture.reports.get(0).pageName());
			assertTrue("初回名の引継ぎで不要な白紙世代を作らない", capture.reports.get(0).emitted());
			assertSame("指定した途中面から開始: " + modes[i], first[i], capture.pageSides.get(0));
			for (final Label call : capture.calls) {
				final Note note = capture.notes.stream().filter(value -> value.id() == call.id()).findFirst().orElseThrow();
				final FootnotePageProbeReport report = capture.reports.stream()
						.filter(value -> value.callIds().contains(call.id())).findFirst().orElseThrow();
				final Page page = capture.page(call.page());
				final CSSElement side = capture.pageSides.get(call.page() - 1);
				final double expectedWidth = side.isPseudoClass(CSSElement.PC_LEFT) ? 212.0
						: side.isPseudoClass(CSSElement.PC_RIGHT) ? 260.0 : 198.0;
				assertEquals(expectedWidth, page.innerWidth(), EPSILON);
				assertEquals("named", report.pageName());
				assertEquals("Bの照会面はCの実際の面と同じ寸法", page.innerWidth(), report.innerWidth(), EPSILON);
				assertEquals(page.innerHeight() + page.inset(), report.innerHeight(), EPSILON);
				assertEquals("途中面開始でも同頁", call.page(), note.placement().page());
				assertEquals(page.innerWidth(), note.placement().width(), EPSILON);
				assertTrue("左右別寸法の報告を採用", capture.plans.stream().anyMatch(plan -> plan.generation() == report.generation()
						&& plan.usable() && plan.reservedIds().contains(call.id())));
			}
		}
	}

	public void testAlternatingNamedPageDimensionsReserveOnCallPage() throws Exception {
		final String body = "<p>無名の本文。</p>" + ("<section class='small'><p>小<span class='note'>小の注。</span>本文。</p></section>"
				+ "<section class='large'><p>大<span class='note'>大の注。</span>本文。</p></section>").repeat(3);
		final Capture capture = transcode(document("@page small { size:240pt 270pt; margin:18pt; padding:3pt; border:1pt solid }"
				+ "@page large { size:360pt 390pt; margin:30pt; padding:4pt; border:2pt solid }"
				+ ".small { page:small } .large { page:large }", body));
		assertEquals(6, capture.calls.size());
		assertEquals(6, capture.notes.size());
		for (int i = 0; i < capture.notes.size(); ++i) {
			final Label call = capture.calls.get(i);
			final Note note = capture.notes.get(i);
			assertEquals(call.id(), note.id());
			assertEquals("寸法が交互に変わっても同頁", call.page(), note.placement().page());
			final Page page = capture.page(call.page());
			final FootnotePageProbeReport report = capture.reports.stream()
					.filter(value -> value.callIds().contains(call.id())).findFirst().orElseThrow();
			assertEquals(i % 2 == 0 ? "small" : "large", report.pageName());
			assertEquals(i % 2 == 0 ? 196.0 : 288.0, page.innerWidth(), EPSILON);
			assertEquals(i % 2 == 0 ? 226.0 : 318.0, report.innerHeight(), EPSILON);
			assertEquals(page.innerWidth(), report.innerWidth(), EPSILON);
			assertEquals(page.innerHeight() + page.inset(), report.innerHeight(), EPSILON);
			assertEquals("横書き注は遅れているCでなく呼出頁の幅", page.innerWidth(), note.placement().width(), EPSILON);
			assertTrue(capture.plans.stream().anyMatch(plan -> plan.generation() == report.generation()
					&& plan.usable() && plan.reservedIds().contains(call.id())));
		}
	}

	public void testFlexAnonymousItemsWithColumns() throws Exception {
		final AtomicLong boundaries = new AtomicLong();
		try (final Hook hook = new Hook(LayoutSource.class, "appendObserver", (Consumer<LayoutSource.Event>) event -> {
			if (event instanceof LayoutSource.AnonymousItemStart || event instanceof LayoutSource.AnonymousItemEnd) boundaries.incrementAndGet();
		})) {
			final Capture capture = transcode(document(".columns { column-count:2; column-gap:12pt }"
					+ ".flex { display:flex; writing-mode:horizontal-tb }",
					"<div class='columns'><p>本文<span class='note'>横書きの注。</span></p>"
					+ "<div class='flex'>直接文字<span>続き</span><p>要素項目</p>末尾</div></div>"));
			assertTrue("匿名項目を実際に合成し、Cの境界照合を通る", boundaries.get() > 0);
			assertFalse(capture.pages.isEmpty());
			assertEquals(1, capture.notes.size());
		}
	}

	public void testDifferentNamedPagesKeepWindowAndMainLogBounded() throws Exception {
		for (final int initial : List.of(480, 144)) {
			final Capture shortRun = transcode(namedDocument(initial, 4));
			final Capture longRun = transcode(namedDocument(initial, 16));
			assertTrue("名前付きページを数十頁出力", longRun.pages.size() >= 32);
			assertTrue("実際に幅と高さの違うページへ切り替える", longRun.pages.stream().map(Page::innerWidth).distinct().count() >= 2);
			assertTrue(longRun.pages.stream().map(page -> page.innerHeight() + page.inset()).distinct().count() >= 2);
			assertTrue("名前付きページの幾何を採用できる", longRun.plans.stream().anyMatch(plan -> plan.usable()));
			assertEquals("報告保持数は世代差+1以下", 0L, longRun.maxReportExcess);
			assertTrue("page-windowを実際に保持", shortRun.maxWindowBytes > 0);
			assertTrue("主ログの文字とイベントを実際に保持", shortRun.maxSourceBytes > 0 && shortRun.maxSourceEvents > 0);
			assertTrue("文書4倍でもCの文字キューは比例しない: " + shortRun.maxWindowBytes + " -> " + longRun.maxWindowBytes,
					longRun.maxWindowBytes <= shortRun.maxWindowBytes * 2 + 512);
			assertTrue("イベントキューも比例しない", longRun.maxDeliveries <= shortRun.maxDeliveries * 2 + 32);
			assertTrue("主ログも比例しない: " + shortRun.maxSourceEvents + " -> " + longRun.maxSourceEvents,
					longRun.maxSourceEvents <= shortRun.maxSourceEvents * 2 + 32);
			assertTrue("sliceに移した文字も含む保持量", longRun.maxSourceBytes <= shortRun.maxSourceBytes * 2 + 512);
			assertEquals("EOFで未配達文字を残さない", 0L, longRun.lastWindowBytes);
			assertEquals(0, longRun.lastDeliveries);
		}
	}

	public void testReportRetentionWithPersistentGenerationDrift() throws Exception {
		// 無名だけの文書のBは初回幾何を保つ。:firstとの寸法差で双方向の世代差を作る。
		for (final int first : List.of(144, 480)) {
			final int later = first == 144 ? 480 : 144;
			final Capture capture = transcode(document("@page { size:" + later + "pt " + later + "pt }"
					+ "@page :first { size:" + first + "pt " + first + "pt }",
					"<p>世代差のある本文です。</p>".repeat(320)));
			assertTrue(capture.pages.size() >= 10);
			if (first == 144) assertTrue("Bが恒常的に多く改頁", capture.maxProbeLead > 4);
			else assertTrue("Cが先行しても入力位置で配達", capture.windowDeliveries > 0);
			assertTrue("保持報告を実際に観測", capture.maxRetainedReports > 0);
			assertEquals("どの安全点も報告数<=max(0,B-C)+1", 0L, capture.maxReportExcess);
			assertFalse(capture.retention.isEmpty());
			assertTrue("B側Retentionにも報告数を含める", capture.retention.stream().anyMatch(state -> state.retainedReports() > 0));
			assertTrue(capture.retention.stream().allMatch(state ->
					state.retainedReports() <= Math.max(0, state.pages() - state.mainGeneration()) + 1));
			assertEquals(0L, capture.lastWindowBytes);
			assertEquals(0, capture.lastDeliveries);
		}
	}

	private static Capture assertHostCall(final String css, final String open, final String close, final boolean measured) throws Exception {
		final Capture capture = transcode(document(css, "<p>本文。</p>" + open
				+ "呼出<span class='note'>横書きの注。</span>続き" + close + "<p>終わり。</p>"));
		assertEquals("callを一度配置", 1, capture.calls.size());
		assertEquals("注を欠落・重複させない", 1, capture.notes.size());
		final Label call = capture.calls.get(0);
		final Note note = capture.notes.get(0);
		assertEquals(call.id(), note.id());
		assertFalse("Bの報告を観測", capture.reports.isEmpty());
		assertEquals("Bの確定木にcallが載るか: " + css, measured,
				capture.reports.stream().anyMatch(report -> report.callIds().contains(call.id())));
		if (measured) {
			assertTrue(capture.reports.stream().anyMatch(report -> report.measuredHeights().containsKey(call.id())));
			assertEquals("短い通常float・セルの注は同頁", call.page(), note.placement().page());
		} else {
			assertTrue("報告にないcallの注は後続ページの帯", note.placement().page() > call.page());
		}
		assertTrue(note.placement().text().startsWith("1. "));
		return capture;
	}

	private static void assertColumns(final Capture capture) {
		assertFalse("段組箱を実際に構築", capture.columns.isEmpty());
		for (final Column column : capture.columns) {
			final Page page = capture.page(column.page());
			assertEquals("帯をページから一度だけ引く", 252.0, page.innerHeight() + page.inset(), EPSILON);
			assertEquals("段組の包含寸法は予約済みのページ内寸", page.innerHeight(), column.innerHeight(), EPSILON);
			assertEquals("(L-H-(n-1)gap)/n", (252 - page.inset() - (column.count() - 1) * column.gap()) / column.count(),
					column.lineSize(), EPSILON);
		}
		final List<Placement> vertical = capture.lines.stream().filter(line -> line.flow().isVertical()).toList();
		assertFalse("本文行を実際に描画", vertical.isEmpty());
		for (final Placement line : vertical) {
			assertTrue("各段の本文が地の帯の手前で閉じる", line.y() + line.height() <= capture.page(line.page()).innerHeight() + EPSILON);
		}
	}

	private static Placement footer(final Capture capture, final int page) {
		final List<Placement> found = capture.lines.stream().filter(line -> line.page() == page
				&& line.y() >= 252 && line.text().trim().equals(Integer.toString(page))).toList();
		assertEquals("下余白のノンブルを観測: page=" + page + ", pages=" + capture.pages.size()
				+ ", lines=" + capture.lines.stream().filter(line -> line.y() >= 252).toList(), 1, found.size());
		return found.get(0);
	}

	private static String fixture() throws Exception {
		return Files.readString(Path.of("files/unittest/0125-footnote/footnote-bottom-columns.html"), StandardCharsets.UTF_8);
	}

	private static String document(final String css, final String body) {
		return "<!doctype html><?jp.cssj.property name='output.page-width' value='300pt'?>"
				+ "<?jp.cssj.property name='output.page-height' value='300pt'?>"
				+ "<html><head><meta charset='UTF-8'><style>@page { margin:24pt; " + AREA + " }"
				+ "body { margin:0; font-size:12pt; line-height:18pt; writing-mode:vertical-rl }"
				+ "p { margin:0 } .note { float:footnote; font-size:10pt; line-height:14pt }"
				+ css + "</style></head><body>" + body + "</body></html>";
	}

	private static String namedDocument(final int initial, final int repeats) {
		final String paragraphs = "<p>寸法の違う本文を読みます。</p>".repeat(10);
		final String body = "<p>初回の版面。</p>" + ("<section class='small'>" + paragraphs + "</section>"
				+ "<section class='large'>" + paragraphs + "</section>").repeat(repeats);
		return document("@page small { size:144pt 180pt } @page large { size:420pt 480pt }"
				+ ".small { page:small } .large { page:large }", body).replace("value='300pt'", "value='" + initial + "pt'");
	}

	private record Page(double height, double innerWidth, double innerHeight, double inset, double marginBottom) { }
	private record Column(int page, double innerHeight, double lineSize, int count, int actual, double gap) { }
	private record Placement(int page, double x, double y, double width, double height, WritingMode flow, String text) { }
	private record Note(long id, Placement placement) { }
	private record Label(int page, long id, String text) { }

	/** 可変木を残さず、変換スレッドで値だけを採る。DirectSession終了後に検査する。 */
	private static final class Capture {
		final List<Page> pages = new ArrayList<>();
		final List<CSSElement> pageSides = new ArrayList<>();
		final List<String> footnoteWarnings = new ArrayList<>();
		final List<Column> columns = new ArrayList<>();
		final List<Placement> lines = new ArrayList<>(), spans = new ArrayList<>();
		final List<Note> notes = new ArrayList<>();
		final List<Label> calls = new ArrayList<>();
		final List<FootnotePageProbeReport> reports = new ArrayList<>();
		final List<RootBuilder.FootnotePlanSnapshot> plans = new ArrayList<>();
		final List<FootnotePageProbe.Retention> retention = new ArrayList<>();
		long maxReportExcess;
		int maxRetainedReports;
		long maxWindowBytes, maxSourceBytes, lastWindowBytes, windowDeliveries, maxProbeLead;
		int maxDeliveries, maxSourceEvents, lastDeliveries;
		Page page(final int number) { return this.pages.get(number - 1); }
		void window(final FootnotePageProbe.WindowRetention window) {
			this.maxRetainedReports = Math.max(this.maxRetainedReports, window.retainedReports());
			this.maxReportExcess = Math.max(this.maxReportExcess,
					window.retainedReports() - Math.max(0, window.reportGeneration() - window.mainGeneration()) - 1);
			this.maxWindowBytes = Math.max(this.maxWindowBytes, window.currentBytes());
			this.maxDeliveries = Math.max(this.maxDeliveries, window.deliveries());
			this.maxSourceEvents = Math.max(this.maxSourceEvents, window.source().retainedEvents() + window.source().slicedEvents());
			this.maxSourceBytes = Math.max(this.maxSourceBytes, window.sourceBytes());
			this.maxProbeLead = Math.max(this.maxProbeLead, window.reportGeneration() - window.mainGeneration());
			this.windowDeliveries = window.windowDeliveries();
			this.lastWindowBytes = window.currentBytes();
			this.lastDeliveries = window.deliveries();
		}
	}

	private static final class Hook implements AutoCloseable {
		private final Field field;
		private final Object saved;
		Hook(final Class<?> type, final String name, final Object value) throws Exception {
			this.field = type.getDeclaredField(name);
			this.field.setAccessible(true);
			this.saved = this.field.get(null);
			this.field.set(null, value);
		}
		public void close() throws Exception { this.field.set(null, this.saved); }
	}

	private static Capture transcode(final String html) throws Exception {
		return transcode(html, null, null, 0);
	}

	private static Capture transcode(final String html, final String printMode,
			final CSSElement previousSide, final int previousPageNumber) throws Exception {
		final Capture capture = new Capture();
		final PDFUserAgent ua = new PDFUserAgent() {
			@Override
			public void prepare(final PrepareMode mode) {
				super.prepare(mode);
				if (printMode != null) {
					this.getPassContext().setPageSide(previousSide);
					this.getPassContext().setPageNumber(previousPageNumber);
				}
				this.visitor = new CaptureVisitor(this, capture);
			}
		};
		ua.getUAContext().setFootnotePageProbeListener(capture.reports::add);
		final Logger logger = Logger.getLogger(RootBuilder.class.getName());
		final Handler warnings = new Handler() {
			public void publish(final LogRecord record) {
				if (record.getMessage().startsWith("giving up on footnotes")) capture.footnoteWarnings.add(record.getMessage());
			}
			public void flush() { }
			public void close() { }
		};
		logger.addHandler(warnings);
		try (final Hook window = new Hook(Class.forName("net.zamasoft.foliojet.css.style.RecordingLayoutSink"), "windowObserver",
				(Consumer<FootnotePageProbe.WindowRetention>) capture::window);
				final Hook plan = new Hook(RootBuilder.class, "footnotePlanObserver",
						(Consumer<RootBuilder.FootnotePlanSnapshot>) capture.plans::add);
				final Hook retained = new Hook(FootnotePageProbe.class, "retentionObserver",
						(Consumer<FootnotePageProbe.Retention>) capture.retention::add)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
			try {
				session.setUserAgent(ua);
				session.setResults(new SingleResult(new StreamFragmentedOutput(OutputStream.nullOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.property-pi", "true");
				session.property("processing.pass-count", "1");
				if (printMode != null) session.property("output.print-mode", printMode);
				CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
						URI.create("file:///footnote-columns.html"), "text/html", "UTF-8");
			} finally {
				session.close();
			}
		} finally {
			logger.removeHandler(warnings);
		}
		return capture;
	}

	private static final class CaptureVisitor extends PDFVisitor {
		private final UserAgent ua;
		private final Capture capture;
		private int page;
		private boolean mainPage;
		CaptureVisitor(final UserAgent ua, final Capture capture) { super(ua); this.ua = ua; this.capture = capture; }
		@Override
		public void nextPage(final PDFGC gc) {
			super.nextPage(gc);
			++this.page;
			this.mainPage = true;
		}
		@Override
		public void visitBox(final AffineTransform transform, final IBox box, final Drawer drawer, final double x, final double y) {
			super.visitBox(transform, box, drawer, x, y);
			if (box instanceof PageBox pageBox && this.mainPage) {
				this.mainPage = false;
				this.capture.pageSides.add(this.ua.getPassContext().getPageSide());
				this.capture.pages.add(new Page(pageBox.getHeight(), pageBox.getInnerWidth(), pageBox.getInnerHeight(),
						pageBox.getFootInset(), pageBox.getFrame().margin.bottom));
			} else if (box instanceof MulticolumnBlockBox column) {
				this.capture.columns.add(new Column(this.page, column.getInnerHeight(), column.getLineSize(), column.getColumnCount(),
						column.getContainer() instanceof ColumnsContainer columns ? columns.getColumnCount() : 1, column.getBlockParams().columns.gap));
			} else if (box instanceof FlowBlockBox flow && flow.getFlowPos().columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.capture.spans.add(this.placement(box, flow.getBlockParams().flow, x, y));
			} else if (box instanceof FloatBlockBox note && box.getPos() instanceof FootnotePos) {
				this.capture.notes.add(new Note(note.getParams().footnoteId, this.placement(box, note.getBlockParams().flow, x, y)));
			} else if (box instanceof AbstractLineBox line) {
				this.capture.lines.add(this.placement(box, line.getLineParams().flow, x, y));
			} else if (box instanceof AbstractReplacedBox replaced
					&& replaced.getReplacedParams().image instanceof FootnoteLabelImage label && !label.isMarker()) {
				this.capture.calls.add(new Label(this.page, label.getFootnoteId(), label.getAltString()));
			}
		}
		private Placement placement(final IBox box, final WritingMode flow, final double x, final double y) {
			final StringBuilder text = new StringBuilder();
			box.getText(text);
			return new Placement(this.page, x, y, box.getWidth(), box.getHeight(), flow, text.toString());
		}
	}
}
