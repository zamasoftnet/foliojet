package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Slides (2026-10-23): {@code projection} in output.media_types typesets the document as slides. Pages break only at
 * forced breaks and named-page changes, a page grows along the block axis from its {@code @page} size to hold its
 * content (output.auto-height drops that least size), footnotes and bottom floats follow the content, and the
 * overflow-block media feature is {@code optional-paged} ({@code paged}, {@code scroll} or {@code none} otherwise).
 * Page sizes are read from the page background, which covers the whole sheet.
 */
public class SlidesTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final String SLIDES = "all projection paged visual bitmap static";

	private static final Pattern PAGE = Pattern.compile("BackgroundBorder\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern TEXT = Pattern.compile("x=([-\\d.]+) y=([-\\d.]+) Text\\[\"([^\"]*)\"");

	public SlidesTest(final String name) {
		super(name);
	}

	/** Four slides, the second with {@code lines} lines of 19.6 pt, and a named page 640 pt wide. */
	private static String deck(final String props, final String writingMode, final int lines) {
		final StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		html.append(props);
		html.append("""
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page { size: 480pt 270pt; margin: 18pt; background: #eef; }
				@page wide { size: 640pt 270pt; }
				body { margin: 0; font: 14pt/1.4 serif; writing-mode: %s }
				p { margin: 0 }
				section { break-after: page }
				section.wide { page: wide }
				</style></head><body>
				<section><p>S1</p></section>
				<section><p>S2</p>
				""".formatted(writingMode));
		for (int i = 1; i <= lines; ++i) {
			html.append("<p>L").append(i).append("</p>\n");
		}
		html.append("""
				</section>
				<section class="wide"><p>S3</p></section>
				<section><p>S4</p></section>
				</body></html>
				""");
		return html.toString();
	}

	private static String media(final String types) {
		return "<?jp.cssj.property name=\"output.media_types\" value=\"" + types + "\"?>\n";
	}

	/**
	 * As print the long slide (15 lines, 294 pt, in a page area of 234 pt) takes two pages; as slides every slide is one
	 * page, and only the long one grows.
	 */
	public void testOnlyForcedBreaksAndGrowingPages() throws Exception {
		final List<String> print = convert("print", deck("", "horizontal-tb", 14));
		assertEquals("印刷では長いスライドが2頁に分かれる", 5, print.size());

		final List<String> slides = convert("slides", deck(media(SLIDES), "horizontal-tb", 14));
		assertEquals("スライドでは強制改頁でだけ分かれる", 4, slides.size());
		assertEquals("短いスライドは@pageの大きさ", 270.0, pageSize(slides.get(0))[1], 0.01);
		final double tall = pageSize(slides.get(1))[1];
		assertEquals("長いスライドは中身に合わせて伸びる", 15 * 19.6 + 36, tall, 1);
		final double[] l14 = text(slides.get(1), "L14");
		assertTrue("最後の行が頁の中: " + l14[1], l14[1] + 20 < tall - 18);
		assertEquals("名前付きの頁の大きさ", 640.0, pageSize(slides.get(2))[0], 0.01);
		assertEquals("名前付きの頁も伸びない向きは@pageのまま", 270.0, pageSize(slides.get(2))[1], 0.01);
		assertEquals(270.0, pageSize(slides.get(3))[1], 0.01);
	}

	/** output.auto-height drops the least size: each slide is as tall as its content. */
	public void testAutoHeightSlides() throws Exception {
		final List<String> slides = convert("auto-height",
				deck(media(SLIDES) + "<?jp.cssj.property name=\"output.auto-height\" value=\"true\"?>\n",
						"horizontal-tb", 14));
		assertEquals(4, slides.size());
		assertEquals("短いスライドは中身の高さ", 19.6 + 36, pageSize(slides.get(0))[1], 1);
		assertEquals(15 * 19.6 + 36, pageSize(slides.get(1))[1], 1);
	}

	/**
	 * In vertical writing the block axis is horizontal: the long slide (31 lines, 608 pt, in a page area of 444 pt)
	 * grows in width and keeps its height.
	 */
	public void testVerticalSlidesGrowInWidth() throws Exception {
		final List<String> slides = convert("vertical", deck(media(SLIDES), "vertical-rl", 30));
		assertEquals(4, slides.size());
		final double[] size = pageSize(slides.get(1));
		assertEquals("縦書きでは幅が伸びる", 31 * 19.6 + 36, size[0], 1);
		assertEquals("高さは@pageのまま", 270.0, size[1], 0.01);
		final double[] l30 = text(slides.get(1), "L30");
		assertTrue("最後の行が紙の中: " + l30[0], l30[0] >= -18 && l30[0] < size[0]);
	}

	/**
	 * vertical-lr is bound on the left, and output.print-mode can bind vertical-rl on the left: the page still grows in
	 * width (2026-10-24; the auto axis followed the binding side, and PageBox, which takes a vertical page's line from
	 * its height, failed on an auto height). Without slides, output.auto-height does the same.
	 */
	public void testVerticalSlidesGrowInWidthWhicheverSideIsBound() throws Exception {
		final String leftBound = "<?jp.cssj.property name=\"output.print-mode\" value=\"left-side\"?>\n";
		for (final String[] c : new String[][] { { "vertical-lr", "" }, { "vertical-rl", leftBound } }) {
			final List<String> slides = convert("vertical-" + c[0] + (c[1].isEmpty() ? "" : "-left"),
					deck(media(SLIDES) + c[1], c[0], 30));
			assertEquals(c[0], 4, slides.size());
			final double[] size = pageSize(slides.get(1));
			assertEquals(c[0] + ": 縦書きでは幅が伸びる", 31 * 19.6 + 36, size[0], 1);
			assertEquals(c[0] + ": 高さは@pageのまま", 270.0, size[1], 0.01);
			final double[] l30 = text(slides.get(1), "L30");
			assertTrue(c[0] + ": 最後の行が紙の中: " + l30[0], l30[0] >= 0 && l30[0] < size[0]);
		}
		// output.auto-height makes no breaks at all: one page as wide as the whole deck
		final List<String> auto = convert("auto-height-vertical-lr",
				deck("<?jp.cssj.property name=\"output.auto-height\" value=\"true\"?>\n", "vertical-lr", 30));
		assertEquals(1, auto.size());
		final double[] size = pageSize(auto.get(0));
		assertEquals("auto-height の縦書きも幅が伸びる", 34 * 19.6 + 36, size[0], 1);
		final double[] s4 = text(auto.get(0), "S4");
		assertTrue("最後の行が紙の中: " + s4[0], s4[0] >= 0 && s4[0] < size[0]);
	}


	/** A slide with one paragraph of 300 words, about 25 lines in a page area of 234 pt. */
	private static String longParagraph(final String css) {
		final StringBuilder words = new StringBuilder("S2");
		for (int i = 1; i <= 300; ++i) {
			words.append(" w").append(i);
		}
		return single(css, "<section><p>S1</p></section><section><p>" + words
				+ "</p></section><section><p>S3</p></section><section><p>S4</p></section>");
	}

	/**
	 * A slide whose paragraph runs past the page asks for a break at its end. The refusal must leave the next slide's
	 * break-before and a pending break-after in place (2026-10-24, TECH-20261024-005: every later slide went onto the
	 * long one, past its end).
	 */
	public void testBreaksAfterAnOverflowingSlide() throws Exception {
		// The first slide takes no break-before: with the page background it would leave a blank page before it, a
		// separate matter (print alike)
		for (final String css : new String[] { "", "section { break-after: auto } section + section { break-before: page }" }) {
			final List<String> slides = convert("overflow" + (css.isEmpty() ? "-after" : "-before"), longParagraph(css));
			assertEquals(css, 4, slides.size());
			assertTrue(css + ": 長いスライドは伸びる", pageSize(slides.get(1))[1] > 400);
			text(slides.get(1), "w300");
			assertTrue(css + ": 次のスライドは次の頁の頭", text(slides.get(2), "S3")[1] < 40);
			assertTrue(css, text(slides.get(3), "S4")[1] < 40);
		}
	}

	/**
	 * The lines of a paragraph that runs past a slide are those of the same paragraph on a page tall enough, greedy or
	 * {@code text-wrap-style: pretty} (2026-10-24, TECH-20261024-005). Each line past the least page asked for a refused
	 * break that closed the text block: a greedy paragraph lost words from the line before (a line of one word), and
	 * the replay of a pretty paragraph met the closed block (NullPointerException).
	 */
	public void testLongParagraphKeepsItsLines() throws Exception {
		for (final String css : new String[] { "", "p { text-wrap-style: pretty }" }) {
			final List<String> slides = convert("long-paragraph" + (css.isEmpty() ? "" : "-pretty"), longParagraph(css));
			final String tall = longParagraph(css + "\n@page { size: 480pt 2000pt }").replace(media(SLIDES), "");
			final List<String> print = convert("long-paragraph-tall" + (css.isEmpty() ? "" : "-pretty"), tall);
			for (int i = 1; i <= 300; ++i) {
				final double[] a = text(slides.get(1), "w" + i);
				final double[] b = text(print.get(1), "w" + i);
				assertEquals(css + " w" + i + " x", b[0], a[0], 0.01);
				assertEquals(css + " w" + i + " y", b[1], a[1], 0.01);
			}
		}
	}

	private static String words(final int count) {
		final StringBuilder words = new StringBuilder();
		for (int i = 1; i <= count; ++i) {
			words.append(" w").append(i);
		}
		return words.toString();
	}

	/**
	 * A single-item flex streams in the page's flow, so forced breaks inside it still part the slides, also after one
	 * that runs past the page (codex review 2026-10-24: asking whether automatic breaks can come made it a plain box).
	 */
	public void testForcedBreaksInsideSingleItemFlex() throws Exception {
		final List<String> slides = convert("flex-sections", single("<div style=\"display:flex\"><div style=\"flex:1\">"
				+ "<section><p>A1</p></section><section><p>A2" + words(300) + "</p></section><section><p>A3</p></section>"
				+ "</div></div>"));
		assertEquals(3, slides.size());
		text(slides.get(1), "w300");
		text(slides.get(2), "A3");
	}

	/**
	 * Floats cleared past a float taller than the slide are placed one after another: no later fragment comes to defer
	 * them to (codex review 2026-10-24: they overlapped).
	 */
	public void testClearingFloatsAfterATallFloat() throws Exception {
		// The tall float paints its whole height (a background), so it waits for a split (codex review)
		final List<String> slides = convert("clearing-floats", single(".f { float: left; width: 80pt }",
				"<div class=\"f\" style=\"height:400pt;background:#ccc\">TALL</div><div style=\"clear:both\"></div>"
						+ "<div class=\"f\" style=\"clear:both\">F1</div><div class=\"f\" style=\"clear:both\">F2</div>"
						+ "<p>END</p>"));
		assertEquals(1, slides.size());
		final double f1 = text(slides.get(0), "F1")[1];
		final double f2 = text(slides.get(0), "F2")[1];
		assertTrue("F2はF1の下: " + f1 + " " + f2, f2 > f1 + 15);
		assertTrue("ENDはfloatの横", text(slides.get(0), "END")[0] >= 80);

		// Within one paragraph that runs past the slide (codex review: no refused break between its lines drops the wait)
		final List<String> inline = convert("clearing-floats-inline", single(
				".f { float: left; width: 80pt } .s { float: left; clear: both; width: 60pt }",
				"<div class=\"f\" style=\"height:400pt;background:#ccc\">TALL</div><p>" + words(300)
						+ " <span class=\"s\">G1</span> x1 x2 x3 <span class=\"s\">G2</span>" + words(300) + "</p>"));
		assertEquals(1, inline.size());
		final double g1 = text(inline.get(0), "G1")[1];
		final double g2 = text(inline.get(0), "G2")[1];
		assertTrue("G2はG1の下: " + g1 + " " + g2, g2 > g1 + 15);

		// Right after the tall float, with no block between (codex review; since 19147)
		final List<String> adjacent = convert("clearing-floats-adjacent", single(".f { float: left; width: 80pt }",
				"<div class=\"f\" style=\"height:400pt;background:#ccc\">TALL</div>"
						+ "<div class=\"f\" style=\"clear:both\">H1</div><div class=\"f\" style=\"clear:both\">H2</div>"));
		final double h1 = text(adjacent.get(0), "H1")[1];
		final double h2 = text(adjacent.get(0), "H2")[1];
		assertTrue("H1はTALLの下: " + h1, h1 >= 400);
		assertTrue("H2はH1の下: " + h1 + " " + h2, h2 > h1 + 15);
	}

	/**
	 * A column break refused inside a multicol on a slide leaves the next slide's break-before in place, as a refused
	 * page break does (codex review 2026-10-24: the next slide went onto the multicol's).
	 */
	public void testRefusedColumnBreakKeepsTheNextSlide() throws Exception {
		final String block = "<div style=\"break-inside:avoid\"><p>" + words(150) + "</p></div>";
		final List<String> slides = convert("refused-column-break",
				single("section { break-after: auto } section + section { break-before: page }",
						"<section><div style=\"columns:2;column-fill:auto\">" + block + block
								+ "</div></section><section><p>NEXT</p></section>"));
		assertEquals(2, slides.size());
		assertTrue("次のスライドは次の頁の頭", text(slides.get(1), "NEXT")[1] < 40);
	}

	/** A footnote and a bottom float go after the content, and the page grows by them. */
	public void testFootnoteAndBottomFloatFollowTheContent() throws Exception {
		final StringBuilder html = new StringBuilder("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		html.append(media(SLIDES));
		html.append("""
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page { size: 480pt 270pt; margin: 18pt; background: #eef; }
				body { margin: 0; font: 14pt/1.4 serif }
				p { margin: 0 }
				.fn { float: footnote }
				.bf { float: bottom; width: 100pt; height: 40pt; background: #fcc }
				</style></head><body>
				<p>E4<span class="fn">FN1</span></p><div class="bf">BF</div>
				""");
		for (int i = 1; i <= 14; ++i) {
			html.append("<p>N").append(i).append("</p>\n");
		}
		html.append("</body></html>\n");
		final List<String> slides = convert("footnote", html.toString());
		assertEquals(1, slides.size());
		final double n14 = text(slides.get(0), "N14")[1];
		final double bf = text(slides.get(0), "BF")[1];
		final double fn = text(slides.get(0), "FN1")[1];
		assertTrue("下のfloatは本文の後: N14 " + n14 + " BF " + bf, bf > n14 + 15);
		assertTrue("脚注は下のfloatの後: BF " + bf + " FN1 " + fn, fn > bf + 30);
		assertTrue("頁は脚注まで伸びる", fn + 15 < pageSize(slides.get(0))[1] - 18);
		final double n5 = text(slides.get(0), "N5")[0];
		assertEquals("行は下のfloatを避けて縮まない", 0.0, n5, 0.01);
	}

	/** A fixed table cuts itself before asking for a break; on slides it stays whole (codex review). */
	public void testFixedTableStaysWhole() throws Exception {
		final StringBuilder rows = new StringBuilder();
		for (int i = 1; i <= 30; ++i) {
			rows.append("<tr><td>R").append(i).append("</td><td>cell</td></tr>\n");
		}
		final List<String> slides = convert("fixed-table", single("<section><table style=\"table-layout:fixed;"
				+ "width:300pt\" border=\"1\">" + rows + "</table></section><section><p>NEXT</p></section>"));
		assertEquals("表は割れずに1枚", 2, slides.size());
		text(slides.get(0), "R30");
		text(slides.get(1), "NEXT");
	}

	/** Notes more than the least page holds stay with their calls (codex review). */
	public void testAllFootnotesStayOnTheirSlide() throws Exception {
		final StringBuilder lines = new StringBuilder();
		for (int i = 1; i <= 30; ++i) {
			lines.append("<p>M").append(i).append("<span class=\"fn\">F").append(i).append("</span></p>\n");
		}
		final List<String> slides = convert("many-notes",
				single("<section>" + lines + "</section><section><p>NEXT</p></section>"));
		assertEquals(2, slides.size());
		assertTrue("最後の脚注は本文の後", text(slides.get(0), "F30")[1] > text(slides.get(0), "M30")[1]);
		assertFalse("次のスライドに脚注を送らない", slides.get(1).contains("Text[\"F"));
	}

	/** Lines beside a top float taller than the least page keep avoiding it (codex review). */
	public void testTallTopFloatKeepsLinesBeside() throws Exception {
		final StringBuilder lines = new StringBuilder("<div class=\"tf\">TF</div>");
		for (int i = 1; i <= 20; ++i) {
			lines.append("<p>W").append(i).append("</p>\n");
		}
		final List<String> slides = convert("tall-top-float", single("<section>" + lines + "</section>"));
		assertEquals(1, slides.size());
		assertEquals("頁はfloatの高さまで伸びる", 400 + 36, pageSize(slides.get(0))[1], 0.01);
		assertTrue("最後の行もfloatの横", text(slides.get(0), "W20")[0] >= 60);
	}

	/** Notes inside balanced columns stay on their slide, after the content (codex review). */
	public void testColumnFootnotesStayOnTheirSlide() throws Exception {
		final StringBuilder lines = new StringBuilder();
		for (int i = 1; i <= 20; ++i) {
			lines.append("<p>K").append(i).append("<span class=\"fn\">N").append(i).append("</span></p>\n");
		}
		final List<String> slides = convert("column-notes", single("<section><p>FIRST</p></section><section>"
				+ "<div style=\"columns:2;column-fill:balance\">" + lines + "</div><p>AFTER</p></section>"));
		assertEquals("脚注だけのスライドを足さない", 2, slides.size());
		final double after = text(slides.get(1), "AFTER")[1];
		final double n1 = text(slides.get(1), "N1")[1];
		assertTrue("脚注は本文の後: AFTER " + after + " N1 " + n1, n1 > after);
		assertTrue("頁は脚注まで伸びる", text(slides.get(1), "N20")[1] < pageSize(slides.get(1))[1]);
	}

	/** Parallel notes stay beside their calls past the least page size, and the page grows by them (codex review). */
	public void testMarginNotesBesideTheirCalls() throws Exception {
		final StringBuilder lines = new StringBuilder();
		for (int i = 1; i <= 30; ++i) {
			lines.append("<p>L").append(i);
			if (i % 6 == 0) {
				lines.append("<span style=\"float:-cssj-note-end;width:14pt;font-size:8pt\">M").append(i).append("</span>");
			}
			lines.append("</p>\n");
		}
		final List<String> slides = convert("margin-notes", single("<section>" + lines + "</section>"));
		assertEquals(1, slides.size());
		final double l30 = text(slides.get(0), "L30")[1];
		final double m30 = text(slides.get(0), "M30")[1];
		assertEquals("並列注は呼び出しの行の横", l30, m30, 10);
	}

	/** The first slide, opened before any content, reserves a footnote min-height like the others (codex review). */
	public void testFootnoteMinHeightOnEverySlide() throws Exception {
		final List<String> slides = convert("footnote-min-height", single("@footnote { min-height: 300pt }",
				"<section><p>A<span class=\"fn\">FA</span></p></section><section><p>B<span class=\"fn\">FB</span></p></section>"));
		assertEquals(2, slides.size());
		final double first = pageSize(slides.get(0))[1];
		assertTrue("最小の高さの分だけ伸びる: " + first, first > 300 + 36);
		assertEquals("1枚目も2枚目と同じ", pageSize(slides.get(1))[1], first, 0.01);
	}

	/** A fixed @footnote height is a least height on slides: no note-only slides (codex review). */
	public void testFixedFootnoteHeightGrows() throws Exception {
		final StringBuilder lines = new StringBuilder();
		for (int i = 1; i <= 20; ++i) {
			lines.append("<p>Q").append(i).append("<span class=\"fn\">R").append(i).append("</span></p>\n");
		}
		final List<String> slides = convert("footnote-height", single("@footnote { height: 60pt }",
				"<section>" + lines + "</section><section><p>NEXT</p></section>"));
		assertEquals("脚注だけのスライドを足さない", 2, slides.size());
		assertTrue("最後の脚注は本文の後", text(slides.get(0), "R20")[1] > text(slides.get(0), "Q20")[1]);
		assertFalse("次のスライドに脚注を送らない", slides.get(1).contains("Text[\"R"));
	}

	private static String single(final String body) {
		return single("", body);
	}

	/** A slide document with footnotes, 60 x 400 pt top floats and the given extra CSS. */
	private static String single(final String css, final String body) {
		return "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n" + media(SLIDES) + """
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page { size: 480pt 270pt; margin: 18pt; background: #eef; }
				body { margin: 0; font: 14pt/1.4 serif }
				p { margin: 0 }
				section { break-after: page }
				.fn { float: footnote }
				.tf { float: top; width: 60pt; height: 400pt; background: #cfc }
				""" + css + "\n</style></head><body>\n" + body + "</body></html>\n";
	}

	/** overflow-block and the projection media type. */
	public void testMediaQueries() throws Exception {
		assertEquals("[Qpaged, Qany]", queries("q-print", ""));
		assertEquals("[Qoptional, Qany, Qprojection]", queries("q-slides", media(SLIDES)));
		assertEquals("[Qscroll, Qany]",
				queries("q-auto", "<?jp.cssj.property name=\"output.auto-height\" value=\"true\"?>\n"));
		assertEquals("[Qnone]",
				queries("q-nobreak", "<?jp.cssj.property name=\"output.no-page-break\" value=\"true\"?>\n"));
	}

	private static String queries(final String name, final String props) throws Exception {
		final String html = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n" + props + """
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page { size: 480pt 270pt; margin: 18pt; background: #eef; }
				body { margin: 0; font: 14pt/1.4 serif }
				p { margin: 0; display: none }
				@media (overflow-block: paged) { .paged { display: block } }
				@media (overflow-block: optional-paged) { .optional { display: block } }
				@media (overflow-block: scroll) { .scroll { display: block } }
				@media (overflow-block: none) { .none { display: block } }
				@media (overflow-block) { .any { display: block } }
				@media projection { .projection { display: block } }
				</style></head><body>
				<p class="paged">Qpaged</p><p class="optional">Qoptional</p><p class="scroll">Qscroll</p>
				<p class="none">Qnone</p><p class="any">Qany</p><p class="projection">Qprojection</p>
				</body></html>
				""";
		final List<String> found = new ArrayList<>();
		for (final String page : convert(name, html)) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				found.add(m.group(3));
			}
		}
		return found.toString();
	}

	/** The page's width and height from its background. */
	private static double[] pageSize(final String page) {
		final Matcher m = PAGE.matcher(page);
		assertTrue("頁の背景が無い:\n" + page, m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}

	private static double[] text(final String page, final String text) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(text)) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
			}
		}
		throw new AssertionError(text + " が無い:\n" + page);
	}

	/** Converts a document on a separate thread with a timeout and returns the display list of each page. */
	private static List<String> convert(final String name, final String html) throws Exception {
		final File dir = new File("local/slides/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "slides-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] files = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(name + ": 頁が出ていない", files);
		java.util.Arrays.sort(files);
		final List<String> pages = new ArrayList<>(files.length);
		for (final File f : files) {
			pages.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
