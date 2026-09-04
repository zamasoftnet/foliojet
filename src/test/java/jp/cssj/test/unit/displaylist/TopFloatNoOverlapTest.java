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
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.PageFloatPos;
import net.zamasoft.foliojet.layout.constraint.AxisSpan;
import net.zamasoft.foliojet.layout.constraint.ExclusionSpace;
import net.zamasoft.foliojet.layout.constraint.FloatExclusion;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 上下ページフロートの二次元排除、頁先頭top、top-nextの回帰テスト。 */
public class TopFloatNoOverlapTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final long WATCHDOG_MS = 60_000L;
	private static final double EPSILON = 0.05;
	private static final double PT_PER_MM = 72.0 / 25.4;

	public TopFloatNoOverlapTest(final String name) {
		super(name);
	}

	/** 部分幅topでは同じページの脇へ流れ、帯を過ぎた行は全幅へ戻る。 */
	public void testPartialWidthTopFloatWrapsOnSamePage() throws Exception {
		final String html = document("horizontal-tb", "width:100pt;height:24pt;padding:2pt;border:1pt solid black",
				"", "<p class='body'>" + words("BODY", 180) + "</p>");
		final Capture capture = transcode("partial-width", html, 1, null);
		final BoxBounds floating = only(capture.topFloats(), "部分幅top");
		assertEquals("top-nextは次ページ", 2, floating.page());

		final List<BoxBounds> lines = linesOnPage(capture, "BODY", floating.page());
		assertTrue("部分幅topのページに本文行が必要", !lines.isEmpty());
		assertNoIntersections("部分幅top", floating, lines);

		int fullLengthLines = 0;
		for (final BoxBounds line : lines) {
			if (!overlapsY(line.bounds(), floating.bounds())
					&& Math.abs(line.bounds().getWidth() - 180) <= 0.75) {
				++fullLengthLines;
			}
		}
		assertTrue("topのblock帯を過ぎた本文行は180ptの全幅へ戻る", fullLengthLines > 0);
	}

	/** 行方向・block方向とも全面を塞ぐtopはfloat-onlyページを1枚だけ作る。 */
	public void testFullExtentTopFloatMakesOneFloatOnlyPage() throws Exception {
		// 行方向も頁を超える高さにして初めて「全面」になる(130pt では下に本文が流れる=それは正しい挙動)
		final String html = document("horizontal-tb",
				"box-sizing:border-box;width:180pt;height:1000pt;border:1pt solid black", "",
				"<p class='body'>BODY-A BODY-B BODY-C</p>");
		final Capture capture = transcode("full-extent", html, 1, null);
		final BoxBounds floating = only(capture.topFloats(), "全面top");
		assertEquals("top-nextは次ページ", 2, floating.page());
		// top-next では後続本文が短ければ図版の前の頁(1 頁目)に収まる。float-only 頁は 1 枚だけで、頁数は有限
		assertTrue("ページ数は有限: " + capture.pageCount(), capture.pageCount() >= 2 && capture.pageCount() <= 4);
		assertTrue("全面topのページは本文を持たない", linesOnPage(capture, "BODY", floating.page()).isEmpty());

		final List<BoxBounds> body = lines(capture, "BODY");
		assertTrue("本文が失われていない", !body.isEmpty());
		for (final BoxBounds line : body) {
			assertTrue("本文は全面topの頁には置かれない", line.page() != floating.page());
		}
	}

	/** repro-6: 92x45mmの台紙を右上へ置き、縦行は下の約148.8mmを使う。 */
	public void testVerticalRlReproSixUsesRemainingInlineExtent() throws Exception {
		final String html = metricDocument("vertical-rl", "width:92mm;height:45mm",
				"<p class='body'>" + words("BODYV", 260) + "</p>");
		final Capture capture = transcode("vertical-rl-repro-6", html, 1, null);
		final BoxBounds floating = only(capture.topFloats(), "vertical-rl top");
		assertEquals(2, floating.page());
		assertEquals(92 * PT_PER_MM, floating.bounds().getWidth(), 0.75);
		assertEquals(45 * PT_PER_MM, floating.bounds().getHeight(), 0.75);

		final List<BoxBounds> lines = linesOnPage(capture, "BODYV", floating.page());
		assertTrue("台紙のページに縦組本文が必要", !lines.isEmpty());
		assertNoIntersections("vertical-rl repro-6", floating, lines);
		boolean usedRemaining = false;
		for (final BoxBounds line : lines) {
			if (line.bounds().getMinY() >= floating.bounds().getMaxY() - EPSILON
					&& Math.abs(line.bounds().getHeight() - 148.8 * PT_PER_MM) <= 1.5) {
				usedRemaining = true;
			}
		}
		assertTrue("縦行は台紙の下の約148.8mmを使う", usedRemaining);
	}

	/** horizontal-tbの鏡像では同じ92x45mm台紙の直下から全幅行を始める。 */
	public void testHorizontalTbMirrorStartsBelowPlate() throws Exception {
		final String html = metricDocument("horizontal-tb", "width:92mm;height:45mm",
				"<p class='body'>" + words("BODYH", 220) + "</p>");
		final Capture capture = transcode("horizontal-tb-mirror", html, 1, null);
		final BoxBounds floating = only(capture.topFloats(), "horizontal-tb top");
		final List<BoxBounds> lines = linesOnPage(capture, "BODYH", floating.page());
		assertTrue("台紙のページに横組本文が必要", !lines.isEmpty());
		assertNoIntersections("horizontal-tb mirror", floating, lines);
		assertEquals("最初の横組行は台紙の直下", floating.bounds().getMaxY(), lines.get(0).bounds().getMinY(), 0.75);
		assertEquals("横組行は92mmの全幅", 92 * PT_PER_MM, lines.get(0).bounds().getWidth(), 1.0);
	}

	/** 複数topはFIFOでpageAxis方向へ積み、各実配置矩形が本文を排除する。 */
	public void testTwoTopFloatsStackFifoOnOnePage() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 } body { font:10pt/12pt monospace }
				.top-a { float:top; width:40pt; height:18pt; background:#ccc }
				.top-b { float:top; width:60pt; height:24pt; background:#aaa }
				.body { page-break-before:always; text-align:justify }
				</style></head><body><p>PRE</p><div class='top-a'></div><div class='top-b'></div>
				<p class='body'>%s</p></body></html>
				""".formatted(words("FIFO", 100));
		final Capture capture = transcode("two-top-fifo", html, 1, null);
		assertEquals("top配置数", 2, capture.topFloats().size());
		final List<BoxBounds> floats = new ArrayList<>(capture.topFloats());
		floats.sort(Comparator.comparingDouble(f -> f.bounds().getMinY()));
		final BoxBounds first = floats.get(0), second = floats.get(1);
		assertEquals("同じページへ積む", first.page(), second.page());
		assertEquals("先に現れた18ptのtopが上", 18, first.bounds().getHeight(), 0.25);
		assertEquals("後の24ptのtopが下", 24, second.bounds().getHeight(), 0.25);
		assertEquals("矩形は隙間なくpageAxis方向へ積む", first.bounds().getMaxY(), second.bounds().getMinY(), EPSILON);
		final List<BoxBounds> lines = linesOnPage(capture, "FIFO", first.page());
		assertTrue("積んだtopのページに本文が必要", !lines.isEmpty());
		assertNoIntersections("FIFO first", first, lines);
		assertNoIntersections("FIFO second", second, lines);
	}

	/** B-1: 頁先頭で連続登録したtopも、横組の現頁へFIFOで即時配置する。 */
	public void testConsecutiveFirstContentTopFloatsStackOnCurrentHorizontalPage() throws Exception {
		final Capture capture = transcode("current-page-two-top-horizontal",
				consecutiveFirstContentTopDocument("horizontal-tb", "width:40pt;height:18pt",
						"width:60pt;height:24pt", "IH"), 1, null);
		assertEquals("連続top配置数", 2, capture.topFloats().size());
		final List<BoxBounds> floats = new ArrayList<>(capture.topFloats());
		floats.sort(Comparator.comparingDouble(f -> f.bounds().getMinY()));
		final BoxBounds first = floats.get(0), second = floats.get(1);
		assertEquals("先頭topは現頁", 1, first.page());
		assertEquals("後続topも同じ現頁", first.page(), second.page());
		assertEquals("先頭18pt top", 18, first.bounds().getHeight(), 0.25);
		assertEquals("後続24pt top", 24, second.bounds().getHeight(), 0.25);
		assertEquals("現頁でもFIFOで隙間なく積む", first.bounds().getMaxY(), second.bounds().getMinY(), EPSILON);
		assertFalse("連続top同士は交差しない", intersects(first.bounds(), second.bounds()));
		final List<BoxBounds> lines = linesOnPage(capture, "IH", 1);
		assertTrue("連続topと同じ現頁に本文が必要", !lines.isEmpty());
		assertNoIntersections("現頁FIFO first", first, lines);
		assertNoIntersections("現頁FIFO second", second, lines);
		assertTrue("先頭topの帯では本文を右へ回り込ませる",
				lines.stream().anyMatch(line -> overlapsY(line.bounds(), first.bounds())
						&& Math.abs(line.bounds().getMinX() - first.bounds().getMaxX()) <= 0.75));
		assertTrue("後続topの帯でも本文を右へ回り込ませる",
				lines.stream().anyMatch(line -> overlapsY(line.bounds(), second.bounds())
						&& Math.abs(line.bounds().getMinX() - second.bounds().getMaxX()) <= 0.75));
	}

	/** B-1のvertical-rl鏡像でも、右から左へFIFOで積み二次元排除する。 */
	public void testConsecutiveFirstContentTopFloatsStackOnCurrentVerticalRlPage() throws Exception {
		final Capture capture = transcode("current-page-two-top-vertical",
				consecutiveFirstContentTopDocument("vertical-rl", "width:18pt;height:40pt",
						"width:24pt;height:60pt", "IV"), 1, null);
		assertEquals("連続vertical top配置数", 2, capture.topFloats().size());
		final List<BoxBounds> floats = new ArrayList<>(capture.topFloats());
		floats.sort(Comparator.comparingDouble((BoxBounds f) -> f.bounds().getMaxX()).reversed());
		final BoxBounds first = floats.get(0), second = floats.get(1);
		assertEquals("先頭vertical topは現頁", 1, first.page());
		assertEquals("後続vertical topも同じ現頁", first.page(), second.page());
		assertEquals("先頭18pt top", 18, first.bounds().getWidth(), 0.25);
		assertEquals("後続24pt top", 24, second.bounds().getWidth(), 0.25);
		assertEquals("vertical-rlでもFIFOで隙間なく積む", first.bounds().getMinX(), second.bounds().getMaxX(), EPSILON);
		assertFalse("連続vertical top同士は交差しない", intersects(first.bounds(), second.bounds()));
		final List<BoxBounds> lines = linesOnPage(capture, "IV", 1);
		assertTrue("連続vertical topと同じ現頁に本文が必要", !lines.isEmpty());
		assertNoIntersections("現頁vertical FIFO first", first, lines);
		assertNoIntersections("現頁vertical FIFO second", second, lines);
		assertTrue("先頭topの列はその下へ回り込む",
				lines.stream().anyMatch(line -> overlapsX(line.bounds(), first.bounds())
						&& Math.abs(line.bounds().getMinY() - first.bounds().getMaxY()) <= 0.75));
		assertTrue("後続topの列もその下へ回り込む",
				lines.stream().anyMatch(line -> overlapsX(line.bounds(), second.bounds())
						&& Math.abs(line.bounds().getMinY() - second.bounds().getMaxY()) <= 0.75));
	}

	/** B-1即時配置はTwoPassでも2件だけで、pass-count 1/2を一致させる。 */
	public void testConsecutiveFirstContentTopFloatsTwoPassParity() throws Exception {
		final String html = consecutiveFirstContentTopDocument("horizontal-tb", "width:40pt;height:18pt",
				"width:60pt;height:24pt", "IP");
		final File one = new File("local/unittest/current-page-top-pass-parity/pass-1");
		final File two = new File("local/unittest/current-page-top-pass-parity/pass-2");
		final Capture passOne = transcode("current-page-top-pass-1", html, 1, one);
		final Capture passTwo = transcode("current-page-top-pass-2", html, 2, two);
		assertEquals("pass-count=1の即時top配置数", 2, passOne.topFloats().size());
		assertEquals("pass-count=2の即時top配置数", 2, passTwo.topFloats().size());
		assertTrue("pass-count=1は2件とも現頁",
				passOne.topFloats().stream().allMatch(floating -> floating.page() == 1));
		assertTrue("pass-count=2は2件とも現頁",
				passTwo.topFloats().stream().allMatch(floating -> floating.page() == 1));
		assertDisplayListsEqual(one, two);
	}

	/** top・bottom・脚注が同居しても各実配置矩形を交差させない。 */
	public void testTopFloatWithFootnoteKeepsBottomAndFootnotePlacement() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 } body { font:10pt/12pt monospace }
				.top { float:top; width:70pt; height:24pt; background:#ccc }
				.bottom { float:bottom; width:80pt; height:20pt; background:#999 }
				.note { float:footnote }
				.body { page-break-before:always }
				</style></head><body>
				<p>PRE<span class='note'>FN1</span></p><div class='bottom'></div><div class='top'></div>
				<p class='body'>BODYNOTE<span class='note'>FN2</span></p>
				</body></html>
				""";
		final Capture capture = transcode("top-footnote-bottom", html, 1, null);
		final BoxBounds top = only(capture.topFloats(), "top");
		final BoxBounds bottom = only(capture.bottomFloats(), "bottom");
		assertEquals("脚注は2ページに1件ずつ", 2, capture.footnotes().size());
		final BoxBounds firstNote = capture.footnotes().stream().filter(n -> n.page() == bottom.page()).findFirst()
				.orElseThrow(() -> new AssertionError("bottomと同じページの脚注がない"));
		final BoxBounds secondNote = capture.footnotes().stream().filter(n -> n.page() == top.page()).findFirst()
				.orElseThrow(() -> new AssertionError("topと同じページの脚注がない"));
		assertFalse("bottomと脚注は交差しない", intersects(bottom.bounds(), firstNote.bounds()));
		assertTrue("bottomは脚注の上", bottom.bounds().getMaxY() <= firstNote.bounds().getMinY() + EPSILON);
		assertFalse("topと同頁の脚注は交差しない", intersects(top.bounds(), secondNote.bounds()));
		assertNoIntersections("top+footnote本文", top, linesOnPage(capture, "BODYNOTE", top.page()));
	}

	/** nested BFCはRoot行走査で一度だけ避け、直交builderへ頁座標を渡さない。 */
	public void testNestedBfcAndOrthogonalFlowDoNotDoubleAvoid() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p,section { margin:0 } body { font:10pt/12pt monospace }
				.top { float:top; width:72pt; height:36pt; background:#ccc }
				.body { page-break-before:always }
				.outer,.inner { display:flow-root }
				.orth { writing-mode:vertical-rl; width:30pt; height:50pt; margin-top:50pt }
				</style></head><body><p>PRE</p><div class='top'></div>
				<section class='body outer'><section class='inner'><p>BFC-LINE</p></section></section>
				<section class='orth'><p>ORTH-LINE</p></section>
				</body></html>
				""";
		final Capture capture = transcode("nested-bfc-orthogonal", html, 1, null);
		final BoxBounds floating = only(capture.topFloats(), "nested top");
		final BoxBounds bfc = only(linesOnPage(capture, "BFC-LINE", floating.page()), "BFC line");
		assertFalse("nested BFC lineはtopと交差しない", intersects(floating.bounds(), bfc.bounds()));
		assertEquals("nested BFCはtop幅を一度だけ避ける", floating.bounds().getMaxX(), bfc.bounds().getMinX(), 0.75);
		// 縦行の文字列は1字ずつの run に分かれて記録されるので、先頭の "O" を含む行で探す
		final List<BoxBounds> orthLines = capture.lines().stream()
				.filter(line -> line.page() == floating.page() && line.text().replace(" ", "").startsWith("O"))
				.toList();
		final BoxBounds orth = only(orthLines, "orthogonal line");
		assertFalse("外枠が帯を過ぎた直交flowへ頁座標を二重適用しない", intersects(floating.bounds(), orth.bounds()));
	}

	/** TwoPass replayは同じtopを再登録せず、pass-count 1/2の表示リストも一致する。 */
	public void testTwoPassReplayHasOneFloatAndIdenticalDisplayLists() throws Exception {
		final String html = document("horizontal-tb", "width:max-content;height:24pt", "",
				"<p class='body'>" + words("TWOPASS", 120) + "</p>");
		final File one = new File("local/unittest/top-float-pass-parity/pass-1");
		final File two = new File("local/unittest/top-float-pass-parity/pass-2");
		final Capture passOne = transcode("two-pass-parity-1", html, 1, one);
		final Capture passTwo = transcode("two-pass-parity-2", html, 2, two);
		assertEquals("pass-count=1のtop配置数", 1, passOne.topFloats().size());
		assertEquals("pass-count=2のtop配置数", 1, passTwo.topFloats().size());
		assertDisplayListsEqual(one, two);
	}

	/** 横組bottomは左下のblock帯だけを塞ぎ、上の行は全幅、脚注はその下へ置く。 */
	public void testPartialWidthBottomFloatWrapsOnlyIntersectingLinesAboveFootnote() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 } body { font:10pt/12pt monospace }
				.seed { font-size:0; line-height:0 }
				.note { float:footnote; font:10pt/12pt monospace }
				.bottom { float:bottom; width:60pt; height:30pt; background:#999 }
				.body { text-align:justify }
				</style></head><body><p class='seed'><span class='note'>BOTTOM-FN</span></p>
				<div class='bottom'></div><p class='body'>%s</p></body></html>
				""".formatted(words("BOTTOMH", 100));
		final Capture capture = transcode("partial-bottom-footnote", html, 1, null);
		final BoxBounds floating = only(capture.bottomFloats(), "部分幅bottom");
		final BoxBounds footnote = only(capture.footnotes(), "bottom下の脚注");
		final List<BoxBounds> lines = linesOnPage(capture, "BOTTOMH", floating.page());
		assertTrue("bottomと同じページに本文行が必要", !lines.isEmpty());
		assertNoIntersections("部分幅bottom", floating, lines);
		assertFalse("bottomと脚注は交差しない", intersects(floating.bounds(), footnote.bounds()));
		assertTrue("脚注はbottomの下", floating.bounds().getMaxY() <= footnote.bounds().getMinY() + EPSILON);
		boolean full = false, wrapped = false;
		for (final BoxBounds line : lines) {
			if (!overlapsY(line.bounds(), floating.bounds())
					&& Math.abs(line.bounds().getWidth() - 180) <= 0.75) {
				full = true;
			}
			if (overlapsY(line.bounds(), floating.bounds())
					&& Math.abs(line.bounds().getMinX() - floating.bounds().getMaxX()) <= 0.75
					&& Math.abs(line.bounds().getWidth() - 120) <= 0.75) {
				wrapped = true;
			}
		}
		assertTrue("bottomより上の行は180ptの全幅", full);
		assertTrue("bottomのblock帯だけ左の60ptを避ける", wrapped);
	}

	/** 報告の判型でもvertical-rl bottomは左上の実矩形だけを排除する。 */
	public void testVerticalRlBottomUsesRemainingInlineExtent() throws Exception {
		final String html = bottomMetricDocument("vertical-rl", "width:30mm;height:45mm",
				"<p class='body'>" + words("BOTTOMV", 260) + "</p>");
		final Capture capture = transcode("vertical-rl-bottom", html, 1, null);
		final BoxBounds floating = only(capture.bottomFloats(), "vertical-rl bottom");
		final List<BoxBounds> lines = linesOnPage(capture, "BOTTOMV", floating.page());
		assertTrue("bottomのページに縦組本文が必要", !lines.isEmpty());
		assertNoIntersections("vertical-rl bottom", floating, lines);
		boolean full = false, wrapped = false;
		for (final BoxBounds line : lines) {
			if (!overlapsX(line.bounds(), floating.bounds())
					&& Math.abs(line.bounds().getHeight() - 193.8 * PT_PER_MM) <= 1.5) {
				full = true;
			}
			if (overlapsX(line.bounds(), floating.bounds())
					&& line.bounds().getMinY() >= floating.bounds().getMaxY() - EPSILON
					&& Math.abs(line.bounds().getHeight() - 148.8 * PT_PER_MM) <= 1.5) {
				wrapped = true;
			}
		}
		assertTrue("bottomのblock帯より前の縦行は全長", full);
		assertTrue("bottomと交差する縦行は台紙の下148.8mmを使う", wrapped);
	}

	/** 本文の後で現れても現在位置がplacedStart以前なら二次元排除を使う。 */
	public void testLateBottomRegistrationBeforePlacedStartUsesTwoDimensionalExclusion() throws Exception {
		final String html = bottomDocument("horizontal-tb", "width:60pt;height:30pt", """
				<p style='text-align:justify'>%s</p><div class='bottom'></div>
				<p style='text-align:justify'>%s</p>
				""".formatted(words("LATEPRE", 24), words("LATEPOST", 80)));
		final Capture capture = transcode("late-bottom", html, 1, null);
		final BoxBounds floating = only(capture.bottomFloats(), "遅延bottom");
		final List<BoxBounds> lines = capture.lines().stream()
				.filter(line -> line.page() == floating.page()
						&& (line.text().contains("LATEPRE") || line.text().contains("LATEPOST")))
				.toList();
		assertTrue("遅延bottomのページに本文が必要", !lines.isEmpty());
		assertHorizontalStartBottomWraps("遅延bottomの二次元排除", floating, lines, 180);
	}

	/** 登録時の現在位置がplacedStartを越えていれば、そのページだけ一次元へ戻す。 */
	public void testBottomRegistrationPastPlacedStartFallsBackWithoutOverlap() throws Exception {
		final String html = bottomDocument("horizontal-tb", "width:60pt;height:30pt", """
				<p style='box-sizing:border-box;height:108pt'>FALLBACK-PRE</p>
				<div class='bottom'></div><p>FALLBACK-POST</p>
				""");
		final Capture capture = transcode("bottom-past-placed-start", html, 1, null);
		final BoxBounds floating = only(capture.bottomFloats(), "placedStart通過後のbottom");
		assertNoIntersections("placedStart通過後のbottom", floating,
				linesOnPage(capture, "FALLBACK-PRE", floating.page()));
		assertTrue("fallback後の本文はbottomのページへ追加しない",
				linesOnPage(capture, "FALLBACK-POST", floating.page()).isEmpty());
		assertTrue("fallback後の本文は次ページに残る", !lines(capture, "FALLBACK-POST").isEmpty());
	}

	/** 脚注増加後も現在位置が新placedStart以前なら排除矩形だけを組み直す。 */
	public void testFootnoteGrowthBeforeNewPlacedStartRebuildsTwoDimensionalExclusion() throws Exception {
		final String html = bottomDocument("horizontal-tb", "width:70pt;height:30pt", """
				<div class='bottom'></div><p style='text-align:justify'>%s<span class='note'>GROW-FN</span>%s</p>
				""".formatted(words("GROWPRE", 8), words("GROWPOST", 60)));
		final Capture capture = transcode("bottom-footnote-growth", html, 1, null);
		final BoxBounds floating = only(capture.bottomFloats(), "脚注増加bottom");
		final BoxBounds footnote = only(capture.footnotes(), "増加した脚注");
		assertFalse("移動後のbottomと脚注は交差しない", intersects(floating.bounds(), footnote.bounds()));
		assertTrue("脚注は移動後bottomの下", floating.bounds().getMaxY() <= footnote.bounds().getMinY() + EPSILON);
		final List<BoxBounds> lines = capture.lines().stream()
				.filter(line -> line.page() == floating.page()
						&& (line.text().contains("GROWPRE") || line.text().contains("GROWPOST")))
				.toList();
		assertHorizontalStartBottomWraps("脚注増加後の二次元排除", floating, lines, 180);
	}

	/** 複数bottomはFIFOでblock-end側へ積み、各実配置矩形だけを排除する。 */
	public void testTwoBottomFloatsStackFifoOnOnePage() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 } body { font:10pt/12pt monospace }
				.a { float:bottom; width:40pt; height:18pt; background:#ccc }
				.b { float:bottom; width:60pt; height:24pt; background:#999 }
				.body { text-align:justify }
				</style></head><body><div class='a'></div><div class='b'></div><p class='body'>%s</p></body></html>
				""".formatted(words("BOTTOMFIFO", 100));
		final Capture capture = transcode("two-bottom-fifo", html, 1, null);
		assertEquals("bottom配置数", 2, capture.bottomFloats().size());
		final List<BoxBounds> floats = new ArrayList<>(capture.bottomFloats());
		floats.sort(Comparator.comparingDouble(f -> f.bounds().getMinY()));
		final BoxBounds first = floats.get(0), second = floats.get(1);
		assertEquals("同じページへ積む", first.page(), second.page());
		assertEquals("先に現れた18ptのbottomが上", 18, first.bounds().getHeight(), 0.25);
		assertEquals("後の24ptのbottomが下", 24, second.bounds().getHeight(), 0.25);
		assertEquals("矩形は隙間なくblock方向へ積む", first.bounds().getMaxY(), second.bounds().getMinY(), EPSILON);
		final List<BoxBounds> lines = linesOnPage(capture, "BOTTOMFIFO", first.page());
		assertTrue("bottomのページに本文が必要", !lines.isEmpty());
		assertHorizontalStartBottomWraps("FIFO first bottom", first, lines, 180);
		assertHorizontalStartBottomWraps("FIFO second bottom", second, lines, 180);
	}

	/** carry-in bottomとtop-nextが同じページに来ても両矩形を一度ずつ避ける。 */
	public void testTopAndBottomOnSamePageDoNotOverlapBody() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 } body { font:10pt/12pt monospace }
				.bottom-a { float:bottom; width:180pt; height:100pt; background:#bbb }
				.bottom-b { float:bottom; width:60pt; height:20pt; background:#999 }
				.top { float:top; width:70pt; height:20pt; background:#ddd }
				</style></head><body><div class='bottom-a'></div><div class='bottom-b'></div>
				<div class='top'></div><p>%s</p></body></html>
				""".formatted(words("TOPBOTTOM", 160));
		final Capture capture = transcode("top-and-bottom", html, 1, null);
		final BoxBounds top = only(capture.topFloats(), "top");
		final BoxBounds bottom = capture.bottomFloats().stream().filter(f -> f.page() == top.page()).findFirst()
				.orElseThrow(() -> new AssertionError("topと同じページのcarry-in bottomがない"));
		assertFalse("topとbottomは交差しない", intersects(top.bounds(), bottom.bounds()));
		final List<BoxBounds> lines = linesOnPage(capture, "TOPBOTTOM", top.page());
		assertTrue("top+bottomページに本文が必要", !lines.isEmpty());
		assertNoIntersections("top+bottom top", top, lines);
		assertNoIntersections("top+bottom bottom", bottom, lines);
	}

	/** bottomのTwoPass replayも1件だけ登録し、pass-count 1/2の表示リストを一致させる。 */
	public void testBottomTwoPassReplayHasOneFloatAndIdenticalDisplayLists() throws Exception {
		final String html = bottomDocument("horizontal-tb", "width:60pt;height:30pt",
				"<div class='bottom'></div><p style='text-align:justify'>" + words("BOTTOMPASS", 100) + "</p>");
		final File one = new File("local/unittest/bottom-float-pass-parity/pass-1");
		final File two = new File("local/unittest/bottom-float-pass-parity/pass-2");
		final Capture passOne = transcode("bottom-pass-parity-1", html, 1, one);
		final Capture passTwo = transcode("bottom-pass-parity-2", html, 2, two);
		assertEquals("pass-count=1のbottom配置数", 1, passOne.bottomFloats().size());
		assertEquals("pass-count=2のbottom配置数", 1, passTwo.bottomFloats().size());
		final BoxBounds passOneFloat = passOne.bottomFloats().get(0);
		final BoxBounds passTwoFloat = passTwo.bottomFloats().get(0);
		assertHorizontalStartBottomWraps("pass-count=1のbottom", passOneFloat,
				linesOnPage(passOne, "BOTTOMPASS", passOneFloat.page()), 180);
		assertHorizontalStartBottomWraps("pass-count=2のbottom", passTwoFloat,
				linesOnPage(passTwo, "BOTTOMPASS", passTwoFloat.page()), 180);
		assertDisplayListsEqual(one, two);
	}

	/** 行・block両方向を超えるbottomはfloat-onlyページを1枚だけ作る。 */
	public void testOversizedBottomFloatMakesOneBoundedFloatOnlyPage() throws Exception {
		final String html = bottomDocument("horizontal-tb", "width:1000pt;height:1000pt",
				"<div class='bottom'></div><p>OVERSIZED-BODY</p>");
		final Capture capture = transcode("oversized-bottom", html, 1, null);
		final BoxBounds floating = only(capture.bottomFloats(), "全面bottom");
		assertTrue("ページ数は有限: " + capture.pageCount(), capture.pageCount() >= 2 && capture.pageCount() <= 4);
		assertTrue("全面bottomページは本文を持たない",
				linesOnPage(capture, "OVERSIZED-BODY", floating.page()).isEmpty());
		final List<BoxBounds> body = lines(capture, "OVERSIZED-BODY");
		assertTrue("本文は次ページに残る", !body.isEmpty());
		assertNoIntersections("全面bottom", floating, body);
	}

	/** B-1: 頁の最初のtopは横組の左上へ即時配置し、本文は実矩形を避ける。 */
	public void testFirstContentTopFloatUsesCurrentHorizontalPage() throws Exception {
		final String html = firstContentTopDocument("horizontal-tb", "width:70pt;height:24pt",
				words("CURRENT-H", 100));
		final Capture capture = transcode("current-page-top-horizontal", html, 1, null);
		final BoxBounds floating = only(capture.topFloats(), "頁先頭の横組top");
		assertEquals("頁先頭のtopは現頁", 1, floating.page());
		assertEquals("横組topは版面左", 0, floating.bounds().getMinX(), 0.25);
		assertEquals("横組topは版面上", 0, floating.bounds().getMinY(), 0.25);
		final List<BoxBounds> lines = linesOnPage(capture, "CURRENT-H", 1);
		assertTrue("現頁に本文行が必要", !lines.isEmpty());
		assertNoIntersections("現頁横組top", floating, lines);
		assertTrue("横組本文はtopの右へ二次元回り込みする",
				lines.stream().anyMatch(line -> overlapsY(line.bounds(), floating.bounds())
						&& Math.abs(line.bounds().getMinX() - floating.bounds().getMaxX()) <= 0.75));
	}

	/** B-1: vertical-rlの頁先頭topは右上へ即時配置し、縦行は下へ回り込む。 */
	public void testFirstContentTopFloatUsesCurrentVerticalRlPage() throws Exception {
		// 図版脇の残り(130-70=60pt)に入る短い語にする(長い語だと脇の列が飛ばされる)
		final String html = firstContentTopDocument("vertical-rl", "width:24pt;height:70pt",
				words("CV", 120));
		final Capture capture = transcode("current-page-top-vertical", html, 1, null);
		final BoxBounds floating = only(capture.topFloats(), "頁先頭の縦組top");
		assertEquals("頁先頭のtopは現頁", 1, floating.page());
		assertEquals("vertical-rl topは版面右", 180, floating.bounds().getMaxX(), 0.25);
		assertEquals("vertical-rl topは版面上", 0, floating.bounds().getMinY(), 0.25);
		final List<BoxBounds> lines = linesOnPage(capture, "CV", 1);
		assertTrue("現頁に縦組本文行が必要", !lines.isEmpty());
		assertNoIntersections("現頁vertical-rl top", floating, lines);
		assertTrue("縦組本文はtopの下へ二次元回り込みする",
				lines.stream().anyMatch(line -> overlapsX(line.bounds(), floating.bounds())
						&& Math.abs(line.bounds().getMinY() - floating.bounds().getMaxY()) <= 0.75));
	}

	/** B-1: 本文が一度でも置かれた後のtopは従来どおり次頁へ送る。 */
	public void testTopFloatAfterContentStillUsesNextPage() throws Exception {
		final String html = document("horizontal-tb", "width:70pt;height:24pt", "",
				"<p class='body'>" + words("AFTER-CONTENT", 60) + "</p>");
		final Capture capture = transcode("top-after-content", html, 1, null);
		assertEquals("本文後のtopはtop-next", 2, only(capture.topFloats(), "本文後のtop").page());
	}

	/** B-1: 強制改頁直後なら、新しい空PageBoxへtopを即時配置する。 */
	public void testTopFloatImmediatelyAfterPageBreakUsesNewCurrentPage() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 } body { font:10pt/12pt monospace }
				.break { page-break-after:always }
				.top { float:top; width:70pt; height:24pt; background:#ccc }
				.body { text-align:justify }
				</style></head><body><p class='break'>PAGE-ONE</p><div class='top'></div>
				<p class='body'>%s</p></body></html>
				""".formatted(words("AFTER-BREAK", 80));
		final Capture capture = transcode("current-page-top-after-break", html, 1, null);
		final BoxBounds floating = only(capture.topFloats(), "改頁直後のtop");
		assertEquals("改頁直後のtopは空の2頁目", 2, floating.page());
		final List<BoxBounds> lines = linesOnPage(capture, "AFTER-BREAK", 2);
		assertTrue("topと同じ新頁に本文が必要", !lines.isEmpty());
		assertNoIntersections("改頁直後top", floating, lines);
	}

	/** future開始で打ち切る通常走査と分離し、ページ集合は後続要素まで見る。 */
	public void testPageFloatScanContinuesPastFutureStart() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(
				new FloatExclusion(0, FloatSide.START, new AxisSpan(30, 100), new AxisSpan(0, 50)));
		space = space.plus(
				new FloatExclusion(1, FloatSide.END, new AxisSpan(0, 200), new AxisSpan(400, 500)));
		final ExclusionSpace.LineScan scan = space.scanLineBandFully(0, 20, 0, 500);
		assertTrue(scan.maxPageSizeSet());
		assertEquals(30.0, scan.maxPageSize(), 0);
		assertNotNull("未来開始より後ろの要素も走査する", scan.endExclusion());
		assertEquals(1, scan.endExclusion().order());
		assertEquals(400.0, scan.lineEnd(), 0);
	}

	private static String document(final String writingMode, final String floatStyle, final String extraCss,
			final String body) {
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 }
				body { writing-mode:%s; font:10pt/12pt monospace }
				.top { float:top; box-sizing:content-box; margin:0; background:#ccc; %s }
				.body { page-break-before:always; text-align:justify }
				%s
				</style></head><body><p>PRE</p><div class='top'></div>%s</body></html>
				""".formatted(writingMode, floatStyle, extraCss, body);
	}

	private static String metricDocument(final String writingMode, final String floatStyle, final String body) {
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:103.2mm 206.4mm; margin:6.3mm 5.6mm }
				html,body,p { margin:0 }
				body { writing-mode:%s; font:10pt/15.9pt monospace }
				.top { float:top; box-sizing:border-box; margin:0; %s; border:.5pt solid black; background:#ccc }
				.body { page-break-before:always; text-align:justify }
				</style></head><body><p>PRE</p><div class='top'></div>%s</body></html>
				""".formatted(writingMode, floatStyle, body);
	}

	private static String firstContentTopDocument(final String writingMode, final String floatStyle,
			final String body) {
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 }
				body { writing-mode:%s; font:10pt/12pt monospace }
				.top { float:top; box-sizing:border-box; margin:0; background:#ccc; %s }
				.body { text-align:justify }
				</style></head><body><div class='top'></div><p class='body'>%s</p></body></html>
				""".formatted(writingMode, floatStyle, body);
	}

	private static String consecutiveFirstContentTopDocument(final String writingMode, final String firstStyle,
			final String secondStyle, final String token) {
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 }
				body { writing-mode:%s; font:10pt/12pt monospace }
				.top-a,.top-b { float:top; box-sizing:border-box; margin:0; background:#ccc }
				.top-a { %s } .top-b { %s; background:#aaa }
				.body { text-align:justify }
				</style></head><body><div class='top-a'></div><div class='top-b'></div>
				<p class='body'>%s</p></body></html>
				""".formatted(writingMode, firstStyle, secondStyle, words(token, 120));
	}

	private static String bottomDocument(final String writingMode, final String floatStyle, final String body) {
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 }
				body { writing-mode:%s; font:10pt/12pt monospace }
				.bottom { float:bottom; box-sizing:border-box; margin:0; %s; background:#999 }
				.note { float:footnote }
				</style></head><body>%s</body></html>
				""".formatted(writingMode, floatStyle, body);
	}

	private static String bottomMetricDocument(final String writingMode, final String floatStyle, final String body) {
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:103.2mm 206.4mm; margin:6.3mm 5.6mm }
				html,body,p { margin:0 }
				body { writing-mode:%s; font:10pt/15.9pt monospace }
				.bottom { float:bottom; box-sizing:border-box; margin:0; %s; background:#999 }
				.body { text-align:justify }
				</style></head><body><div class='bottom'></div>%s</body></html>
				""".formatted(writingMode, floatStyle, body);
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
		final File dir = new File("build/top-float-no-overlap/" + name);
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
		}, "top-float-no-overlap-" + name, 64L * 1024 * 1024);
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
				assertFalse(label + ": 本文行がページフロートと重なった: float=" + floating.bounds()
						+ " line=" + line.bounds() + " text=" + line.text(),
						intersects(floating.bounds(), line.bounds()));
			}
		}
	}

	private static void assertHorizontalStartBottomWraps(final String label, final BoxBounds floating,
			final List<BoxBounds> lines, final double fullWidth) {
		assertTrue(label + ": 同じページに本文行が必要", !lines.isEmpty());
		assertNoIntersections(label, floating, lines);
		boolean full = false, wrapped = false;
		final double wrappedWidth = fullWidth - floating.bounds().getWidth();
		for (final BoxBounds line : lines) {
			if (!overlapsY(line.bounds(), floating.bounds())
					&& Math.abs(line.bounds().getWidth() - fullWidth) <= 0.75) {
				full = true;
			}
			// 交差する行は図版の右端から始まり、残り幅(inline寸法ぶん短い)に
			// 収まる。単語1つだけの行はjustifyで伸びないので幅の一致は求めない
			if (overlapsY(line.bounds(), floating.bounds())
					&& Math.abs(line.bounds().getMinX() - floating.bounds().getMaxX()) <= 0.75
					&& line.bounds().getWidth() <= wrappedWidth + 0.75) {
				wrapped = true;
			}
		}
		assertTrue(label + ": bottomのblock帯より上の行は全幅", full);
		assertTrue(label + ": bottomと交差する行はinline寸法ぶん短い", wrapped);
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
			List<BoxBounds> footnotes, List<BoxBounds> lines) {
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
		private final List<BoxBounds> lines = new ArrayList<>();

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
					List.copyOf(this.footnotes), List.copyOf(this.lines));
		}
	}
}
