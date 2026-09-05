package jp.cssj.test.unit.displaylist;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.PageFloatPos;
import net.zamasoft.foliojet.layout.box.params.PageMarginNotePos;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 本文の後に書かれた全幅の {@code float: top} を現頁の上端へ平行移動する
 * (translate、2026-09-05)受入試験。設計書§6の1〜12を各test methodへ
 * 対応させ、配置頁・物理座標・本文の保存を表示リスト構築時の箱で検査する。
 */
public class TopFloatTranslateTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final long WATCHDOG_MS = 60_000L;
	private static final double EPSILON = 0.05;

	public TopFloatTranslateTest(final String name) {
		super(name);
	}

	/** §6-1: TB/RL/LRとも全幅topを現頁上端へ置き、全本文をその後ろへ移す。 */
	public void testTranslateAcrossWritingModes() throws Exception {
		for (final String mode : new String[] { "horizontal-tb", "vertical-rl", "vertical-lr" }) {
			final String token = mode.equals("horizontal-tb") ? "AXIS-H"
					: mode.equals("vertical-rl") ? "AXIS-R" : "AXIS-L";
			final String html = basicDocument(mode, fullTopStyle(mode, 20), "",
					"<p>" + token + "-BEFORE-A</p><p>" + token
							+ "-BEFORE-B</p><div class='top'></div><p>" + token + "-AFTER</p>");
			final Capture capture = transcode("axis-" + mode, html, 1, null);
			final BoxBounds top = only(capture.topFloats(), mode + " top");
			final List<BoxBounds> body = lines(capture, token);
			assertEquals(mode + ": translate後は1頁", 1, capture.pageCount());
			assertEquals(mode + ": topは現頁", 1, top.page());
			assertLogicalPageStart(mode, top);
			assertTrue(mode + ": 本文が必要", body.size() >= 3);
			assertNoIntersections(mode, top, body);
			assertAllOnPage(mode, body, 1);

			final String topNext = html.replace("<div class='top'></div>",
					"<p style='page-break-after:always'>FORCED-NEXT</p><div class='top'></div>");
			final Capture baseline = transcode("axis-top-next-" + mode, topNext, 1, null);
			assertTrue(mode + ": top-next対照は2頁以上", baseline.pageCount() >= 2);
			assertEquals(mode + ": translateで本文行を欠落・重複させない",
					lines(baseline, token).size(), body.size());
		}
	}

	/** §6-2: 既存内容と合算して収まらなければtop-next、先行本文は旧頁に残す。 */
	public void testNonFittingTopFallsBackWithoutMovingPriorContent() throws Exception {
		final String html = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 40), "",
				"<p style='height:95pt'>NONFIT-PRE</p><div class='top'></div><p>NONFIT-POST</p>");
		final Capture capture = transcode("non-fitting", html, 1, null);
		final BoxBounds top = only(capture.topFloats(), "収まらないtop");
		assertEquals("収まらないtopは次頁", 2, top.page());
		assertEquals("top-nextは次頁上端", 0, top.bounds().getMinY(), 0.25);
		assertEquals("先行本文は1頁目に残る", 1, only(lines(capture, "NONFIT-PRE"), "先行本文").page());
		assertTrue("後続本文は失われない", !lines(capture, "NONFIT-POST").isEmpty());
	}

	/** §6-3: 複数topはFIFO prefixだけを採り、carry-inも追い越さない。 */
	public void testMultipleTopFloatsKeepFifoPrefix() throws Exception {
		final String both = basicDocument("horizontal-tb", "", """
				.top-a,.top-b { float:top; box-sizing:border-box; width:180pt; background:#ccc }
				.top-a { height:18pt } .top-b { height:24pt }
				""", "<p>FIFO-BOTH</p><div class='top-a'></div><div class='top-b'></div><p>FIFO-END</p>");
		final List<BoxBounds> bothFloats = sortedByPageAxis(transcode("fifo-both", both, 1, null).topFloats());
		assertEquals(2, bothFloats.size());
		assertEquals("2件とも現頁", 1, bothFloats.get(0).page());
		assertEquals("2件とも現頁", 1, bothFloats.get(1).page());
		assertEquals("FIFOで隙間なく積む", bothFloats.get(0).bounds().getMaxY(),
				bothFloats.get(1).bounds().getMinY(), EPSILON);

		final String prefix = basicDocument("horizontal-tb", "", """
				.top-a,.top-b { float:top; box-sizing:border-box; width:180pt; height:20pt; background:#ccc }
				""", "<p style='height:95pt'>FIFO-PREFIX</p><div class='top-a'></div><div class='top-b'></div>");
		final Capture prefixCapture = transcode("fifo-prefix", prefix, 1, null);
		assertEquals("prefixの2件を保持", 2, prefixCapture.topFloats().size());
		assertEquals("収まる先頭だけ現頁", 1,
				prefixCapture.topFloats().stream().filter(f -> f.page() == 1).count());
		assertEquals("収まらない後続だけ次頁", 1,
				prefixCapture.topFloats().stream().filter(f -> f.page() == 2).count());

		final String carry = basicDocument("horizontal-tb", "", """
				.top-a,.top-b { float:top; box-sizing:border-box; width:180pt; background:#ccc }
				.top-a { height:30pt } .top-b { height:10pt }
				""", "<p style='height:105pt'>CARRY-PRE</p><div class='top-a'></div><div class='top-b'></div>"
					+ "<p style='page-break-before:always'>CARRY-AFTER</p>");
		final List<BoxBounds> carryFloats = sortedByPageAxis(transcode("fifo-carry", carry, 1, null).topFloats());
		assertEquals(2, carryFloats.size());
		assertEquals("carry-inは同じ次頁", 2, carryFloats.get(0).page());
		assertEquals("後続も同じ次頁", 2, carryFloats.get(1).page());
		assertEquals("30ptの先行carry-inが先頭", 30, carryFloats.get(0).bounds().getHeight(), 0.25);
		assertEquals("carry-inを追い越さない", carryFloats.get(0).bounds().getMaxY(),
				carryFloats.get(1).bounds().getMinY(), EPSILON);
	}

	/** §6-4: 脚注予約を容量から引き、呼び出し頁・注位置・番号を変えない。 */
	public void testFootnoteReservationAndNumberingStayStable() throws Exception {
		final String body = "<p>FN-CALL<span class='note'>FN-NUMBER</span></p><div class='top'></div><p>FN-TAIL</p>";
		final String html = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 20),
				".note { float:footnote }", body);
		final Capture translated = transcode("footnote-fit", html, 1, null);
		final Capture topNext = transcode("footnote-control",
				html.replace("<div class='top'></div>",
						"<p style='page-break-after:always'>FN-FORCE</p><div class='top'></div>"), 1, null);
		final BoxBounds top = only(translated.topFloats(), "脚注付きtop");
		final BoxBounds translatedNote = only(translated.footnotes(), "translate脚注");
		final BoxBounds controlNote = only(topNext.footnotes(), "top-next脚注");
		assertEquals("topは脚注呼び出しと同じ現頁", 1, top.page());
		assertEquals("呼び出し頁は不変", only(lines(topNext, "FN-CALL"), "対照call").page(),
				only(lines(translated, "FN-CALL"), "translate call").page());
		assertEquals("脚注頁は不変", controlNote.page(), translatedNote.page());
		assertEquals("脚注のblock位置は不変", controlNote.bounds().getMinY(), translatedNote.bounds().getMinY(), 0.25);
		assertEquals("脚注番号を含む行内容は不変", texts(lines(topNext, "FN-NUMBER")),
				texts(lines(translated, "FN-NUMBER")));

		final String reserved = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 35),
				".note { float:footnote }",
				"<p style='height:80pt'>FN-RESERVE<span class='note'>FN-NOTEBODY</span></p><div class='top'></div>");
		final Capture reservedCapture = transcode("footnote-reserved-capacity", reserved, 1, null);
		assertEquals("脚注予約を引くと収まらないtopは次頁", 2,
				only(reservedCapture.topFloats(), "脚注予約top").page());
		assertEquals("脚注呼び出しは旧頁", 1, only(lines(reservedCapture, "FN-RESERVE"), "予約call").page());
	}

	/** §6-5: 通常float台帳とoverflow:hidden台帳を移し、後続行の回避を保つ。 */
	public void testInFlowFloatLedgersShiftAndKeepWrapping() throws Exception {
		for (final boolean hidden : new boolean[] { false, true }) {
			final String scopeStart = hidden ? "<section class='scope'>" : "";
			final String scopeEnd = hidden ? "</section>" : "";
			final String html = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 18), """
					.normal { float:left; width:60pt; height:36pt }
					.scope { overflow:hidden }
					.wrap { text-align:justify }
					""", scopeStart + "<p>LEDGER-PRE</p><div class='normal'>FLOAT-LEDGER</div>"
						+ "<p class='wrap'>" + words("LM", 8) + "</p><div class='top'></div>"
						+ "<p class='wrap'>" + words("LA", 12) + "</p>" + scopeEnd);
			final Capture capture = transcode(hidden ? "ledger-hidden" : "ledger-normal", html, 1, null);
			final BoxBounds top = only(capture.topFloats(), "台帳top");
			final BoxBounds floating = only(capture.normalFloats(), "通常float");
			assertEquals("台帳ケースでもtopは現頁", 1, top.page());
			assertTrue("通常floatもtopの後ろへ移る", floating.bounds().getMinY() >= top.bounds().getMaxY() - EPSILON);
			assertNoIntersections(hidden ? "hidden台帳" : "通常台帳", floating,
					linesOnPage(capture, "LA", floating.page()));
		}
	}

	/** §6-6: static absoluteだけをTB/RL/LRの頁軸へ移し、offset指定とfixedは固定する。 */
	public void testAbsoluteStaticPositionsShiftButOffsetsAndFixedStay() throws Exception {
		for (final String mode : new String[] { "horizontal-tb", "vertical-rl", "vertical-lr" }) {
			// 箱は寸法で見分ける(static 70pt / explicit 60pt / fixed 50pt の正方形)
			final String css = """
					.static { position:absolute; width:70pt; height:70pt; font:8pt/10pt monospace }
					.explicit { position:absolute; top:0; left:0; width:60pt; height:60pt; font:8pt/10pt monospace }
					.fixed { position:fixed; top:0; left:0; width:50pt; height:50pt; font:8pt/10pt monospace }
					""";
			final String content = "<p>ABS-PRE</p><div class='static'>ABS-STATIC</div>"
					+ "<div class='explicit'>ABS-EXPLICIT</div><div class='fixed'>ABS-FIXED</div>"
					+ "<div class='top'></div><p>ABS-AFTER</p>";
			final Capture shifted = transcode("absolute-" + mode,
					basicDocument(mode, fullTopStyle(mode, 20), css, content), 1, null);
			final Capture control = transcode("absolute-control-" + mode,
					basicDocument(mode, ".top { display:none }", css, content), 1, null);
			final double dy = logicalPageExtent(mode, only(shifted.topFloats(), "absolute top").bounds());
			final double sign = mode.equals("vertical-rl") ? -1 : 1;
			final List<BoxBounds> controlStatic = absolutesOfSize(control, 70);
			assertEquals(mode + ": static位置だけdy移動",
					pageCoordinate(mode, only(controlStatic, "対照static").bounds()) + sign * dy,
					pageCoordinate(mode, only(absolutesOfSize(shifted, 70), "移動static").bounds()), 0.75);
			assertEquals(mode + ": 明示offsetは動かない",
					pageCoordinate(mode, only(absolutesOfSize(control, 60), "対照explicit").bounds()),
					pageCoordinate(mode, only(absolutesOfSize(shifted, 60), "移動explicit").bounds()), 0.25);
			assertEquals(mode + ": fixedは動かない",
					pageCoordinate(mode, only(absolutesOfSize(control, 50), "対照fixed").bounds()),
					pageCoordinate(mode, only(absolutesOfSize(shifted, 50), "移動fixed").bounds()), 0.25);
		}
	}

	/** 1 頁目にある絶対配置の箱のうち、辺の長さが size の正方形のもの。 */
	private static List<BoxBounds> absolutesOfSize(final Capture capture, final double size) {
		return capture.absolutes().stream().filter(b -> b.page() == 1
				&& Math.abs(b.bounds().getWidth() - size) < 0.5 && Math.abs(b.bounds().getHeight() - size) < 0.5).toList();
	}

	/** §6-7: 配置済み並列注も移し、同じ側の後着注カーソルを同量進める。 */
	public void testPageMarginNotesShiftAndKeepSideCursors() throws Exception {
		final String css = ".margin-note { float:-cssj-note-start; width:30pt; height:12pt }";
		final String content = "<p>NOTE-PRE</p><aside class='margin-note'>NOTE-ONE</aside>"
				+ "<div class='top'></div><aside class='margin-note'>NOTE-TWO</aside><p>NOTE-AFTER</p>";
		final Capture shifted = transcode("margin-note-shift",
				basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 20), css, content), 1, null);
		final Capture control = transcode("margin-note-control",
				basicDocument("horizontal-tb", ".top { display:none }", css, content), 1, null);
		final List<BoxBounds> shiftedNotes = sortedByPageAxis(shifted.marginNotes());
		final List<BoxBounds> controlNotes = sortedByPageAxis(control.marginNotes());
		assertEquals(2, shiftedNotes.size());
		assertEquals(2, controlNotes.size());
		for (int i = 0; i < 2; ++i) {
			assertEquals("並列注" + i + "をtop高だけ移す", controlNotes.get(i).bounds().getMinY() + 20,
					shiftedNotes.get(i).bounds().getMinY(), 0.75);
		}
		assertFalse("移動後も同じ側の注を重ねない",
				intersects(shiftedNotes.get(0).bounds(), shiftedNotes.get(1).bounds()));

		final Capture capacity = transcode("margin-note-capacity",
				basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 18),
						".margin-note { float:-cssj-note-end; width:30pt; height:105pt }",
						"<p>NOTE-CAPACITY</p><aside class='margin-note'></aside><div class='top'></div>"),
				1, null);
		assertEquals("並列注の実終端を越えるtopは次頁", 2,
				only(capacity.topFloats(), "並列注容量top").page());
		assertEquals("先行する並列注は現頁に残る", 1,
				only(capacity.marginNotes(), "容量判定の並列注").page());
	}

	/** §6-8: 二次元bottomの実配置開始をlimitにし、本文をbottom帯へ入れない。 */
	public void testTwoDimensionalBottomReservationLimitsTranslation() throws Exception {
		final String css = """
				.bottom { float:bottom; width:70pt; height:30pt }
				.body { text-align:justify }
				""";
		final String fitting = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 20), css,
				"<div class='bottom'></div><p class='body'>" + words("BOTTOM-FIT", 10)
						+ "</p><div class='top'></div><p>BOTTOM-TAIL</p>");
		final Capture fittingCapture = transcode("bottom-limit-fit", fitting, 1, null);
		final BoxBounds top = only(fittingCapture.topFloats(), "bottom併存top");
		final BoxBounds bottom = only(fittingCapture.bottomFloats(), "予約bottom");
		assertEquals("bottom手前へ収まるtopは現頁", 1, top.page());
		assertEquals("topとbottomは同頁", top.page(), bottom.page());
		assertNoIntersections("top/bottom本文", bottom, linesOnPage(fittingCapture, "BOTTOM", bottom.page()));

		final String rejected = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 20), css,
				"<div class='bottom'></div><p style='height:85pt'>BOTTOM-LIMIT</p><div class='top'></div>");
		final Capture rejectedCapture = transcode("bottom-limit-reject", rejected, 1, null);
		assertEquals("bottom帯へ入るtopは次頁", 2,
				only(rejectedCapture.topFloats(), "bottom limit top").page());
	}

	/** §6-9: open flowのline-clamp可変状態を移動後も同じ参照で継続する。 */
	public void testLineClampStateSurvivesTranslation() throws Exception {
		final String html = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 18),
				".clamp { line-clamp:2 }",
				"<section class='clamp'><p>CLAMP-A</p><div class='top'></div>"
						+ "<p>CLAMP-B<br/>CLAMP-C</p></section><p>CLAMP-AFTER</p>");
		final Capture capture = transcode("line-clamp", html, 1, null);
		final BoxBounds top = only(capture.topFloats(), "clamp top");
		assertEquals("line-clamp中でもtopは現頁", 1, top.page());
		assertEquals("line-clamp中のtopも頁上端", 0, top.bounds().getMinY(), 0.25);
		assertNoIntersections("line-clamp top", top, linesOnPage(capture, "CLAMP", 1));
		assertEquals("1行目を保持", 1, lines(capture, "CLAMP-A").stream()
				.filter(line -> !line.text().contains("AFTER")).count());
		assertEquals("2行目を保持", 1, lines(capture, "CLAMP-B").size());
		assertTrue("3行目はclampで抑止", lines(capture, "CLAMP-C").isEmpty());
		assertEquals("clamp外の後続は残る", 1, lines(capture, "CLAMP-AFTER").size());
	}

	/** §6-10: 安全条件外はtop-nextのまま、表だけは閉じた後のhookで現頁へ置く。 */
	public void testFallbacksAndTableClosureTrigger() throws Exception {
		final Capture inline = transcode("fallback-inline", basicDocument("horizontal-tb",
				fullTopStyle("horizontal-tb", 18), "",
				"<p>" + words("INLINE-PRE", 70) + "<span class='top'></span>" + words("INLINE-POST", 70) + "</p>"),
				1, null);
		assertTrue("段落内アンカーは次頁以降", only(inline.topFloats(), "段落内top").page() >= 2);

		final Capture columns = transcode("fallback-columns", basicDocument("horizontal-tb",
				fullTopStyle("horizontal-tb", 18), ".columns { column-count:2; height:70pt }",
				"<p>COL-BEFORE</p><section class='columns'><p>COL-IN</p><div class='top'></div></section>"), 1, null);
		// 段組の内側では試みない(fallback)が、段組が閉じた後の hook で現頁の上端へ置ける(表と同じ)
		final BoxBounds columnTop = only(columns.topFloats(), "段組top");
		assertEquals("段組を閉じた後の hook で現頁", 1, columnTop.page());
		assertNoIntersections("段組top", columnTop, linesOnPage(columns, "COL", 1));

		final Capture narrow = transcode("fallback-narrow", basicDocument("horizontal-tb",
				"width:70pt;height:18pt", "", "<p>NARROW-PRE</p><div class='top'></div><p>NARROW-AFTER</p>"), 1, null);
		// 狭幅の新規topは帯として現頁へ(脇に文字は回り込まない): 先行本文は帯の下へ移る
		final BoxBounds narrowTop = only(narrow.topFloats(), "狭幅top");
		assertEquals("狭幅topも帯として現頁", 1, narrowTop.page());
		assertNoIntersections("狭幅top", narrowTop, linesOnPage(narrow, "NARROW", 1));
		for (final BoxBounds line : linesOnPage(narrow, "NARROW", 1)) {
			assertTrue("本文行は帯の下(脇に回り込まない)", line.bounds().getMinY() >= narrowTop.bounds().getMaxY() - 0.75);
		}

		final Capture shape = transcode("fallback-shape", basicDocument("horizontal-tb",
				fullTopStyle("horizontal-tb", 18) + ";shape-outside:circle(50%)", "",
				"<p>SHAPE-PRE</p><div class='top'></div><p>SHAPE-AFTER</p>"), 1, null);
		// ページフロートは shape-outside を持たない(BoxStyleMapper: 脚注・ページフロートは別の Pos)ので、
		// shape 指定は無視されて通常の全幅 top として現頁へ置かれる
		assertEquals("shape指定は無視され現頁", 1, only(shape.topFloats(), "shape top").page());

		final String existingHtml = basicDocument("horizontal-tb", "", """
				.first { float:top; width:70pt; height:18pt; background:#ccc }
				.second { float:top; width:180pt; height:18pt; background:#aaa }
				""", "<div class='first'></div><p>PLACED-NARROW</p><div class='second'></div>");
		final List<BoxBounds> existing = transcode("fallback-existing-narrow", existingHtml, 1, null).topFloats();
		assertEquals(2, existing.size());
		assertEquals("既配置狭幅topは現頁", 1,
				existing.stream().filter(f -> Math.abs(f.bounds().getWidth() - 70) < 0.5).findFirst().orElseThrow().page());
		assertEquals("後着全幅topもtop-next", 2,
				existing.stream().filter(f -> Math.abs(f.bounds().getWidth() - 180) < 0.5).findFirst().orElseThrow().page());

		final String tableHtml = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 18),
				"table { border-collapse:collapse; width:180pt } td { padding:0 }",
				"<p>TABLE-PRE</p><section><table><tr><td><div class='top'></div>"
						+ "<p>TABLE-IN</p></td></tr></table></section>");
		final Capture table = transcode("table-closure", tableHtml, 1, null);
		assertEquals("表を閉じた後のhookで現頁", 1, only(table.topFloats(), "表内top").page());
		assertNoIntersections("表内top", table.topFloats().get(0), linesOnPage(table, "TABLE", 1));

		final String restyleHtml = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 18),
				".restyle { page-break-inside:auto }",
				"<section class='restyle'><p style='height:125pt'>RESTYLE-PRE</p><div class='top'></div>"
						+ "<p>RESTYLE-AFTER</p></section>");
		final Capture restyle = transcode("fallback-restyle", restyleHtml, 1, null);
		assertTrue("再開中に届くtopはtop-next", only(restyle.topFloats(), "restyle top").page() >= 2);
	}

	/** §6-11: translate後の通常改頁でも継続を一度ずつ出し、次頁原点をずらさない。 */
	public void testPaginationAfterTranslationKeepsContinuation() throws Exception {
		final String html = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 18), "",
				"<p>CONT-HEAD</p><div class='top'></div><p>" + words("CONT", 180) + "</p>");
		final Capture capture = transcode("post-translate-pagination", html, 1, null);
		assertEquals("topは最初の頁", 1, only(capture.topFloats(), "継続top").page());
		assertTrue("通常改頁が起きる", capture.pageCount() >= 2);
		assertTrue("頁数は有界", capture.pageCount() <= 8);
		final String allText = texts(capture.lines());
		for (int i = 0; i < 180; ++i) {
			final String word = "CONT" + String.format("%03d", i);
			assertEquals(word + "を一度だけ出す", 1, occurrences(allText, word));
		}
		final BoxBounds secondPageFirst = capture.lines().stream().filter(line -> line.page() == 2)
				.min(Comparator.comparingDouble(line -> line.bounds().getMinY())).orElseThrow();
		assertEquals("2頁目は通常のpage-startから始まる", 0, secondPageFirst.bounds().getMinY(), 0.75);
	}

	/** §6-12: 親TwoPassのpass-count 1/2を一致させ、oversized回帰の頁数を抑える。 */
	public void testTwoPassParityAndOversizedRegression() throws Exception {
		final String html = basicDocument("horizontal-tb", fullTopStyle("horizontal-tb", 18),
				".two-pass { display:inline-block; width:180pt }",
				"<p>TWOPASS-PRE</p><span class='two-pass'><span class='top'></span>TWOPASS-BODY</span>"
						+ "<p>TWOPASS-AFTER</p>");
		final File one = new File("local/unittest/top-float-translate-pass/pass-1");
		final File two = new File("local/unittest/top-float-translate-pass/pass-2");
		final Capture passOne = transcode("two-pass-1", html, 1, one);
		final Capture passTwo = transcode("two-pass-2", html, 2, two);
		assertEquals("pass-count=1はtopを1件だけ登録", 1, passOne.topFloats().size());
		assertEquals("pass-count=2はtopを1件だけ登録", 1, passTwo.topFloats().size());
		assertEquals("pass-count=1のtopは現頁", 1, passOne.topFloats().get(0).page());
		assertEquals("pass-count=2のtopは現頁", 1, passTwo.topFloats().get(0).page());
		assertEquals("pass-count=1のtopは頁上端", 0, passOne.topFloats().get(0).bounds().getMinY(), 0.25);
		assertEquals("pass-count=2のtopは頁上端", 0, passTwo.topFloats().get(0).bounds().getMinY(), 0.25);
		assertDisplayListsEqual(one, two);

		final String oversized = Files.readString(
				new File("files/fuzz-repro/oversized-top-float-before-flow.html").toPath(), StandardCharsets.UTF_8);
		final Capture oversizedCapture = transcode("oversized-regression", oversized, 2, null);
		assertTrue("oversized topは高々1件(背景が無いので捕捉されないこともある)", oversizedCapture.topFloats().size() <= 1);
		assertTrue("oversized回帰の頁数は有界: " + oversizedCapture.pageCount(), oversizedCapture.pageCount() <= 8);
		assertTrue("後続本文を失わない", texts(oversizedCapture.lines()).replace(" ", "").contains("T108"));
	}

	private static String basicDocument(final String writingMode, final String topStyle, final String extraCss,
			final String body) {
		final String topRule = topStyle.contains(".top") ? topStyle
				: ".top { float:top; box-sizing:border-box; margin:0; background:#ccc; " + topStyle + " }";
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p,section,aside { margin:0 }
				body { writing-mode:%s; font:10pt/12pt monospace }
				%s
				%s
				</style></head><body>%s</body></html>
				""".formatted(writingMode, topRule, extraCss, body);
	}

	private static String fullTopStyle(final String writingMode, final double extent) {
		return writingMode.equals("horizontal-tb")
				? "width:180pt;height:" + extent + "pt"
				: "width:" + extent + "pt;height:130pt";
	}

	private static String words(final String prefix, final int count) {
		final StringBuilder text = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			text.append(prefix).append(String.format("%03d", i)).append(' ');
		}
		return text.toString();
	}

	private static Capture transcode(final String name, final String html, final int passCount, final File dumpDir)
			throws Exception {
		final File dir = new File("build/top-float-translate/" + name);
		dir.mkdirs();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		if (dumpDir != null) {
			deleteChildren(dumpDir);
			dumpDir.mkdirs();
		}
		synchronized (DisplayListDumper.class) {
			final String previous = System.getProperty(DisplayListDumper.DIR_PROPERTY);
			if (dumpDir == null) {
				System.clearProperty(DisplayListDumper.DIR_PROPERTY);
			} else {
				System.setProperty(DisplayListDumper.DIR_PROPERTY, dumpDir.getPath());
			}
			try {
				return transcodeWithWatchdog(name, input, new File(dir, "out.pdf"), passCount);
			} finally {
				if (previous == null) {
					System.clearProperty(DisplayListDumper.DIR_PROPERTY);
				} else {
					System.setProperty(DisplayListDumper.DIR_PROPERTY, previous);
				}
			}
		}
	}

	private static Capture transcodeWithWatchdog(final String name, final File input, final File pdf,
			final int passCount) throws Exception {
		final Capture[] capture = new Capture[1];
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				final CapturePDFUserAgent ua = new CapturePDFUserAgent();
				try {
					session.setUserAgent(ua);
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("processing.pass-count", Integer.toString(passCount));
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
				capture[0] = ua.capture();
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "top-float-translate-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		assertNotNull(name + ": 配置結果を取得できない", capture[0]);
		return capture[0];
	}

	private static List<BoxBounds> lines(final Capture capture, final String token) {
		return capture.lines().stream().filter(line -> line.text().contains(token)).toList();
	}

	private static List<BoxBounds> linesOnPage(final Capture capture, final String token, final int page) {
		return capture.lines().stream().filter(line -> line.page() == page && line.text().contains(token)).toList();
	}

	private static BoxBounds only(final List<BoxBounds> boxes, final String label) {
		assertEquals(label + "の件数", 1, boxes.size());
		return boxes.get(0);
	}

	private static void assertNoIntersections(final String label, final BoxBounds floating,
			final List<BoxBounds> lines) {
		for (final BoxBounds line : lines) {
			if (line.page() == floating.page()) {
				assertFalse(label + ": 箱が交差した: float=" + floating.bounds() + " line=" + line.bounds()
						+ " text=" + line.text(), intersects(floating.bounds(), line.bounds()));
			}
		}
	}

	private static void assertAllOnPage(final String label, final List<BoxBounds> boxes, final int page) {
		for (final BoxBounds box : boxes) {
			assertEquals(label + ": 本文頁", page, box.page());
		}
	}

	private static void assertLogicalPageStart(final String mode, final BoxBounds top) {
		if (mode.equals("horizontal-tb")) {
			assertEquals(mode + ": page-start", 0, top.bounds().getMinY(), 0.25);
		} else if (mode.equals("vertical-rl")) {
			assertEquals(mode + ": page-start", 180, top.bounds().getMaxX(), 0.25);
		} else {
			assertEquals(mode + ": page-start", 0, top.bounds().getMinX(), 0.25);
		}
	}

	private static double logicalPageExtent(final String mode, final Rectangle2D bounds) {
		return mode.equals("horizontal-tb") ? bounds.getHeight() : bounds.getWidth();
	}

	private static double pageCoordinate(final String mode, final Rectangle2D bounds) {
		return mode.equals("horizontal-tb") ? bounds.getMinY()
				: mode.equals("vertical-rl") ? bounds.getMaxX() : bounds.getMinX();
	}

	private static List<BoxBounds> sortedByPageAxis(final List<BoxBounds> boxes) {
		final List<BoxBounds> sorted = new ArrayList<>(boxes);
		sorted.sort(Comparator.comparingInt(BoxBounds::page).thenComparingDouble(box -> box.bounds().getMinY()));
		return sorted;
	}

	private static String texts(final List<BoxBounds> boxes) {
		final StringBuilder text = new StringBuilder();
		for (final BoxBounds box : boxes) {
			text.append(box.text());
		}
		return text.toString();
	}

	private static int occurrences(final String text, final String token) {
		int count = 0;
		for (int from = 0; (from = text.indexOf(token, from)) >= 0; from += token.length()) {
			++count;
		}
		return count;
	}

	private static boolean overlapsY(final Rectangle2D a, final Rectangle2D b) {
		return Math.min(a.getMaxY(), b.getMaxY()) - Math.max(a.getMinY(), b.getMinY()) > EPSILON;
	}

	private static boolean overlapsX(final Rectangle2D a, final Rectangle2D b) {
		return Math.min(a.getMaxX(), b.getMaxX()) - Math.max(a.getMinX(), b.getMinX()) > EPSILON;
	}

	private static boolean intersects(final Rectangle2D a, final Rectangle2D b) {
		return overlapsX(a, b) && overlapsY(a, b);
	}

	private static void assertDisplayListsEqual(final File expectedDir, final File actualDir) throws Exception {
		final File[] expectedPages = expectedDir.listFiles((dir, name) -> name.endsWith(".txt"));
		final File[] actualPages = actualDir.listFiles((dir, name) -> name.endsWith(".txt"));
		assertNotNull(expectedPages);
		assertNotNull(actualPages);
		assertTrue("比較元の表示リストが生成されている", expectedPages.length > 0);
		assertTrue("比較先の表示リストが生成されている", actualPages.length > 0);
		Arrays.sort(expectedPages, Comparator.comparing(File::getName));
		Arrays.sort(actualPages, Comparator.comparing(File::getName));
		assertEquals("表示リストのページ数", expectedPages.length, actualPages.length);
		for (int i = 0; i < expectedPages.length; ++i) {
			assertEquals("表示リスト名", expectedPages[i].getName(), actualPages[i].getName());
			assertEquals(expectedPages[i].getName(),
					Files.readString(expectedPages[i].toPath(), StandardCharsets.UTF_8),
					Files.readString(actualPages[i].toPath(), StandardCharsets.UTF_8));
		}
	}

	private static void deleteChildren(final File dir) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			child.delete();
		}
	}

	private record BoxBounds(int page, Rectangle2D bounds, String text) {
	}

	private record Capture(int pageCount, List<BoxBounds> topFloats, List<BoxBounds> bottomFloats,
			List<BoxBounds> footnotes, List<BoxBounds> marginNotes, List<BoxBounds> normalFloats,
			List<BoxBounds> lines, List<BoxBounds> absolutes) {
	}

	private static final class CapturePDFUserAgent extends PDFUserAgent {
		private CaptureVisitor captureVisitor;

		@Override
		public void prepare(final PrepareMode mode) {
			super.prepare(mode);
			this.captureVisitor = new CaptureVisitor(this);
			this.visitor = this.captureVisitor;
		}

		Capture capture() {
			return this.captureVisitor.capture();
		}
	}

	private static final class CaptureVisitor extends PDFVisitor {
		private int page = 0;
		private final List<BoxBounds> topFloats = new ArrayList<>();
		private final List<BoxBounds> bottomFloats = new ArrayList<>();
		private final List<BoxBounds> footnotes = new ArrayList<>();
		private final List<BoxBounds> marginNotes = new ArrayList<>();
		private final List<BoxBounds> normalFloats = new ArrayList<>();
		private final List<BoxBounds> lines = new ArrayList<>();
		private final List<BoxBounds> absolutes = new ArrayList<>();

		CaptureVisitor(final UserAgent ua) {
			super(ua);
		}

		@Override
		public void nextPage(final PDFGC gc) {
			super.nextPage(gc);
			++this.page;
		}

		@Override
		public void visitBox(final AffineTransform transform, final IBox box, final Drawer drawer, final double x,
				final double y) {
			super.visitBox(transform, box, drawer, x, y);
			final Shape shape = transform == null ? new Rectangle2D.Double(x, y, box.getWidth(), box.getHeight())
					: transform.createTransformedShape(new Rectangle2D.Double(x, y, box.getWidth(), box.getHeight()));
			final Rectangle2D bounds = shape.getBounds2D();
			if (box instanceof FloatBlockBox && box.getPos() instanceof PageFloatPos pageFloat) {
				final BoxBounds found = new BoxBounds(this.page, bounds, "");
				(pageFloat.top ? this.topFloats : this.bottomFloats).add(found);
			} else if (box instanceof FloatBlockBox && box.getPos() instanceof FootnotePos) {
				this.footnotes.add(new BoxBounds(this.page, bounds, ""));
			} else if (box instanceof FloatBlockBox && box.getPos() instanceof PageMarginNotePos) {
				this.marginNotes.add(new BoxBounds(this.page, bounds, ""));
			} else if (box instanceof FloatBlockBox && box.getPos() instanceof FloatPos) {
				this.normalFloats.add(new BoxBounds(this.page, bounds, ""));
			} else if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox) {
				this.absolutes.add(new BoxBounds(this.page, bounds, ""));
			} else if (box instanceof AbstractLineBox) {
				final StringBuilder text = new StringBuilder();
				box.getText(text);
				if (text.length() > 0) {
					this.lines.add(new BoxBounds(this.page, bounds, text.toString()));
				}
			}
		}

		Capture capture() {
			return new Capture(this.page, List.copyOf(this.topFloats), List.copyOf(this.bottomFloats),
					List.copyOf(this.footnotes), List.copyOf(this.marginNotes), List.copyOf(this.normalFloats),
					List.copyOf(this.lines), List.copyOf(this.absolutes));
		}
	}
}
