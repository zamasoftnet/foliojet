package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Grid defects found by the real-world A/B of 2026-10-08 (tmp/ab/NOTES.md); expected values measured in Chrome.
 *
 * <ul>
 * <li>An empty grid or flex container with an intrinsic width failed the conversion (TwoPass NO_RANGE).</li>
 * <li>Inline SVGs with {@code display: grid}/{@code flex} nested into each other, and from the fourth the conversion
 * failed: the image arrived after display was computed.</li>
 * <li>A track {@code calc()} mixing a percentage and a length dropped the whole {@code grid-template-columns}.</li>
 * <li>A grid without items lost its explicit rows (height 0).</li>
 * <li>A track {@code min()}/{@code max()}/{@code clamp()} mixing a percentage and a length dropped the whole
 * {@code grid-template-columns}, and a custom property declared {@code initial} was substituted as the word itself
 * (2026-10-09, stripe-docs: 3 images in 1 column).</li>
 * </ul>
 */
public class GridEdgeCasesTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	public GridEdgeCasesTest(final String name) {
		super(name);
	}

	private static String document(final String style, final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 500pt 600pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 20pt }
				p { margin: 0 }
				%s
				</style></head><body>%s</body></html>
				""".formatted(style, body);
	}

	/** Empty grid/flex containers sized by their content (width: fit-content/max-content, orthogonal flow). */
	public void testEmptyIntrinsicContainers() throws Exception {
		final String page = convert("empty", document("""
				div { background: #ccc; padding: 5pt }
				""", "<div style=\"display: grid; width: fit-content\"></div>"
				+ "<div style=\"display: flex; width: max-content\"></div>"
				+ "<div style=\"display: grid; width: fit-content; grid-template-columns: 100pt\"></div>"
				+ "<div style=\"display: grid; writing-mode: vertical-rl; height: 40pt\"></div><p>A</p>"));
		final List<double[]> frames = frames(page);
		assertEquals("枠が4つ:\n" + page, 4, frames.size());
		assertEquals("空の grid(fit-content)は padding だけの幅", 10, frames.get(0)[2], 0.01);
		assertEquals("空の grid(fit-content)は padding だけの高さ", 10, frames.get(0)[3], 0.01);
		assertEquals("空の flex(max-content)は padding だけの幅", 10, frames.get(1)[2], 0.01);
		assertEquals("空の grid の固定トラックは幅に効く", 110, frames.get(2)[2], 0.01);
		assertEquals("縦組みの空の grid は padding だけの幅", 10, frames.get(3)[2], 0.01);
		assertEquals("縦組みの空の grid の高さ", 50, frames.get(3)[3], 0.01);
		assertEquals("後ろの段落", 10 + 10 + 10 + 50, y(page, "A"), 0.01);
	}

	/** Inline SVGs with display: grid/flex/table are block-level replaced elements, one under the other. */
	public void testSvgWithContainerDisplay() throws Exception {
		final String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"%s\" viewBox=\"0 0 200 50\">"
				+ "<rect width=\"200\" height=\"50\"/></svg>";
		final String page = convert("svg", document("""
				svg { width: 100pt }
				.g { display: grid } .f { display: flex } .t { display: table }
				""", svg.formatted("g") + svg.formatted("g") + svg.formatted("g") + svg.formatted("g")
				+ svg.formatted("f") + svg.formatted("f") + svg.formatted("t") + "<p>A</p>"));
		final List<double[]> frames = frames(page);
		assertEquals("SVG が7つ:\n" + page, 7, frames.size());
		for (int i = 0; i < frames.size(); ++i) {
			assertEquals("SVG " + i + " の x", 0, frames.get(i)[0], 0.01);
			assertEquals("SVG " + i + " の y", 25 * i, frames.get(i)[1], 0.01);
			assertEquals("SVG " + i + " の幅", 100, frames.get(i)[2], 0.01);
			assertEquals("SVG " + i + " の高さ", 25, frames.get(i)[3], 0.01);
		}
		assertEquals("後ろの段落", 25 * 7, y(page, "A"), 0.01);
	}

	/** Track calc() mixing a percentage with a length or a font-relative unit. */
	public void testCalcTracks() throws Exception {
		final String page = convert("calc", document("""
				.g { display: grid; width: 400pt }
				.g > div.x { background: #ccc; height: 10pt }
				.a { grid-template-columns: 30pt calc(50% + 10pt) 30pt }
				.b { grid-template-columns: 30pt calc(100% - 60pt) 30pt }
				.c { grid-template-columns: 30pt calc(2em + 5pt) 1fr }
				.d { grid-template-columns: repeat(2, calc(25% - 5pt)) 1fr }
				.e { grid-template-columns: minmax(calc(25% + 5pt), 1fr) 300pt }
				.f { grid-template-columns: 30pt calc(50%) 30pt }
				""", "<div class=\"g a\"><div></div><div class=\"x\"></div></div>"
				+ "<div class=\"g b\"><div></div><div class=\"x\"></div></div>"
				+ "<div class=\"g c\"><div></div><div class=\"x\"></div></div>"
				+ "<div class=\"g d\"><div></div><div class=\"x\"></div></div>"
				+ "<div class=\"g e\"><div class=\"x\"></div><div></div></div>"
				+ "<div class=\"g f\"><div></div><div class=\"x\"></div></div>"));
		final List<double[]> frames = frames(page);
		assertEquals("枠が6つ:\n" + page, 6, frames.size());
		final double[][] expected = { { 30, 210 }, { 30, 340 }, { 30, 25 }, { 95, 95 }, { 0, 105 }, { 30, 200 } };
		final String[] names = { "calc(50% + 10pt)", "calc(100% - 60pt)", "calc(2em + 5pt)",
				"repeat(2, calc(25% - 5pt))", "minmax(calc(25% + 5pt), 1fr)", "calc(50%)" };
		for (int i = 0; i < expected.length; ++i) {
			assertEquals(names[i] + " の x", expected[i][0], frames.get(i)[0], 0.01);
			assertEquals(names[i] + " の幅", expected[i][1], frames.get(i)[2], 0.01);
		}
	}

	/**
	 * Track min()/max()/clamp() whose arguments mix percentages and lengths, resolved against the container's width
	 * (360pt, column gap 12pt); the x of each item as in Chrome.
	 */
	public void testMathFunctionTracks() throws Exception {
		final String[][] grids = {
				{ "repeat(auto-fill, minmax(min(calc(100% / 3 - 16px), 100%), 1fr))", "0 124 248 0" },
				{ "min(50%, 100pt) 1fr", "0 112" },
				{ "max(20%, 50pt) max(20%, 100pt) 1fr", "0 84 196" },
				{ "clamp(60pt, 30%, 90pt) clamp(60pt, 10%, 90pt) clamp(10pt, 50%, 90pt) 1fr", "0 102 174 276" },
				{ "minmax(min(100pt, 20%), max(30%, 50pt)) 1fr", "0 120" },
				{ "repeat(auto-fill, min(100pt, 40%))", "0 112 224" },
				{ "repeat(auto-fit, minmax(max(25%, 100pt), 1fr))", "0 124 248 0 124" },
				{ "min(calc(50% - 10pt), max(30%, 150pt)) 1fr", "0 162" } };
		assertItemColumns("math", "", grids);
	}

	/**
	 * A custom property declared with a CSS-wide keyword: {@code initial} makes {@code var()} take its fallback,
	 * {@code inherit} and {@code unset} take the parent's value (stripe-docs: {@code --col-repeat: initial} for
	 * {@code repeat(var(--col-repeat, auto-fill), ...)}). The repeat count shows the value, as in Chrome.
	 */
	public void testCssWideKeywordCustomProperty() throws Exception {
		final String[][] grids = {
				{ "repeat(var(--col-repeat, auto-fill), minmax(min(calc(100% / 3 - 16px), 100%), 1fr))", "0 124 248 0" },
				{ "repeat(var(--x, 3), 1fr)", "0 124 248 0" },
				{ "repeat(var(--y, 3), 1fr)", "0 93 186 279 0" },
				{ "repeat(var(--z, 3), 1fr)", "0 93 186 279 0" } };
		assertItemColumns("keyword", """
				body { --x: 2; --y: 2; --z: 2 }
				.g0 { --col-repeat: initial }
				.p { --y: 4; --z: 4 }
				.g1 { --x: initial } .g2 { --y: inherit } .g3 { --z: unset }
				""", grids);
	}

	/**
	 * One grid per row of {@code grids} (its grid-template-columns, then the expected x of its items in order), each
	 * 360pt wide with 12pt gaps; the third and fourth sit in {@code <div class="p">}.
	 */
	private static void assertItemColumns(final String name, final String style, final String[][] grids)
			throws Exception {
		final StringBuilder css = new StringBuilder(style).append(".g { display: grid; width: 360pt; gap: 12pt }\n");
		final StringBuilder body = new StringBuilder();
		for (int i = 0; i < grids.length; ++i) {
			css.append(".g").append(i).append(" { grid-template-columns: ").append(grids[i][0]).append(" }\n");
			if (i == 2) {
				body.append("<div class=\"p\">");
			}
			body.append("<div class=\"g g").append(i).append("\">");
			final String[] xs = grids[i][1].split(" ");
			for (int k = 0; k < xs.length; ++k) {
				body.append("<div>G").append(i).append('x').append(k).append("</div>");
			}
			body.append("</div>");
			if (i == 3) {
				body.append("</div>");
			}
		}
		final String page = convert(name, document(css.toString(), body.toString()));
		for (int i = 0; i < grids.length; ++i) {
			final String[] xs = grids[i][1].split(" ");
			for (int k = 0; k < xs.length; ++k) {
				assertEquals(grids[i][0] + " の項目 " + (k + 1) + " の x", Double.parseDouble(xs[k]),
						x(page, "G" + i + "x" + k), 0.01);
			}
		}
	}

	/** An empty grid keeps its explicit rows; implicit rows (grid-auto-rows) need items. */
	public void testEmptyGridRows() throws Exception {
		final String page = convert("rows", document("""
				div { background: #ccc }
				""", "<div style=\"display: grid; grid-template-rows: 50pt\"></div>"
				+ "<div style=\"display: grid; grid-template-rows: 20pt 30pt; row-gap: 10pt\"></div>"
				+ "<div style=\"display: grid; width: fit-content; grid-template-columns: 100pt;"
				+ " grid-template-rows: 50pt\"></div>"
				+ "<div style=\"display: grid; grid-auto-rows: 50pt\"></div>"
				+ "<div style=\"display: grid; grid-template-areas: 'x' 'y'; grid-auto-rows: 15pt\"></div>"
				+ "<div style=\"display: grid; grid-template-rows: auto 40pt\"></div><p>A</p>"));
		final List<double[]> frames = frames(page);
		final double[][] expected = { { 500, 50 }, { 500, 60 }, { 100, 50 }, { 500, 0 }, { 500, 30 }, { 500, 40 } };
		final String[] names = { "50pt", "20pt 30pt と行間 10pt", "fit-content", "grid-auto-rows だけ", "領域の 2 行",
				"auto 40pt" };
		assertEquals("枠が6つ:\n" + page, 6, frames.size());
		for (int i = 0; i < expected.length; ++i) {
			assertEquals(names[i] + " の幅", expected[i][0], frames.get(i)[2], 0.01);
			assertEquals(names[i] + " の高さ", expected[i][1], frames.get(i)[3], 0.01);
		}
		assertEquals("後ろの段落", 50 + 60 + 50 + 0 + 30 + 40, y(page, "A"), 0.01);
	}

	/**
	 * A row whose remainder reaches a fresh page with its content fitting but its recorded height not (the lower
	 * bound kept for a remainder includes the item's trailing margin) neither fitted nor split: the whole grid was
	 * kept on that page and every later row ran off the paper (openprops, 46 pages became 7). The later rows go
	 * on to the next page.
	 */
	public void testRemainderRowAtPageTop() throws Exception {
		final StringBuilder lines = new StringBuilder();
		for (int i = 0; i < 44; ++i) {
			lines.append("<div style=\"width: 100%\">L").append(i).append("</div>");
		}
		final List<String> pages = convertPages("remainder", """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 300pt 300pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 20pt }
				.g { display: grid; grid-template-columns: 1fr }
				.big { display: flex; flex-wrap: wrap; margin-bottom: 40pt; background: #ccc }
				</style></head><body><div class="g"><div class="big">%s</div><div>AFTER</div><div>LAST</div></div>
				</body></html>
				""".formatted(lines));
		assertEquals("頁数", 4, pages.size());
		assertTrue("AFTER は 4 頁目:\n" + pages.get(3), pages.get(3).contains("Text[\"AFTER\""));
		assertTrue("AFTER は紙面の中", y(pages.get(3), "AFTER") < 300);
		assertTrue("LAST は紙面の中", y(pages.get(3), "LAST") < 300);
	}

	/**
	 * A nested grid in a later row of a grid at the page top is not itself at the page top: when its first row does
	 * not fit, it moves to the next page whole instead of keeping that row across the page bottom (primer-css's prop
	 * tables, 2026-10-09).
	 */
	public void testNestedGridInLaterRowMoves() throws Exception {
		final List<String> pages = convertPages("nested-later-row", """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 300pt 300pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 20pt }
				.o, .i { display: grid; grid-template-columns: 1fr }
				.i > div { padding: 10pt 0 }
				</style></head><body><div class="o"><div style="height: 285pt">TOP</div>
				<div class="i"><div>A</div><div>B</div><div>C</div></div></div>
				</body></html>
				""");
		assertEquals("頁数", 2, pages.size());
		assertFalse("A は 1 頁目に残らない:\n" + pages.get(0), pages.get(0).contains("Text[\"A\""));
		for (final String s : new String[] { "A", "B", "C" }) {
			assertTrue(s + " は 2 頁目の紙面の中:\n" + pages.get(1), y(pages.get(1), s) < 300);
		}
	}

	/**
	 * Items in a later row are not at the page top, so orphans and widows hold: a row of two-line items that crosses
	 * the page bottom moves whole (Chrome: A1 A2 B1 B2 all on page 2; Copper split each item 1 + 1 before 2026-10-09).
	 */
	public void testLaterRowKeepsOrphansAndWidows() throws Exception {
		final List<String> pages = convertPages("later-row-widows", """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 300pt 300pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 20pt }
				.g { display: grid; grid-template-columns: 1fr 1fr; gap: 3pt }
				.g > div { border: 1pt solid; padding: 3pt }
				</style></head><body><div class="g"><div style="height: 250pt">TOP</div><div>TOP2</div>
				<div>A1<br/>A2</div><div>B1<br/>B2</div></div>
				</body></html>
				""");
		assertEquals("頁数", 2, pages.size());
		for (final String s : new String[] { "A1", "A2", "B1", "B2" }) {
			assertTrue(s + " は 2 頁目:\n" + pages.get(1), pages.get(1).contains("Text[\"" + s + "\""));
		}
	}

	/**
	 * A remainder row that grew past its recorded lower bound once laid out is split again at the next page bottom
	 * (2026-10-09). The inner row's split kept A's lines and moved the tall B whole, so the article's remainder is
	 * taller than "its height less what page 1 took"; FOOT used to sit across the bottom of page 2 (smolcss). Chrome:
	 * FOOT and NEXT on page 3.
	 */
	public void testRemainderRowGrowsPastPage() throws Exception {
		final List<String> pages = convertPages("remainder-grows", """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 300pt 300pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 15pt }
				p { margin: 0 }
				.o { display: grid; grid-template-columns: 1fr }
				.i { display: grid; grid-template-columns: 1fr 1fr }
				.m { display: inline-block; width: 50pt; height: 290pt; vertical-align: top }
				</style></head><body><div class="o"><article><div style="height: 200pt">TOP</div>
				<div class="i"><div>A1<br/>A2<br/>A3<br/>A4<br/>A5<br/>A6<br/>A7<br/>A8</div><div>B<span class="m"></span></div></div>
				<p>FOOT</p></article><div>NEXT</div></div>
				</body></html>
				""");
		assertEquals("頁数", 3, pages.size());
		assertFalse("FOOT は 2 頁目に残らない:\n" + pages.get(1), pages.get(1).contains("Text[\"FOOT\""));
		assertTrue("FOOT は 3 頁目:\n" + pages.get(2), y(pages.get(2), "FOOT") < 300);
		assertTrue("NEXT は FOOT の後", y(pages.get(2), "NEXT") > y(pages.get(2), "FOOT"));
	}

	/**
	 * An item that align-items: center puts below its row start is split at the cut line as it lies in the item
	 * (2026-10-09). Split at the cut line as it lies in the row, a centered item that the cut line passed above stayed
	 * whole on the first page, below the paper (smolcss: the tags beside a long form at y=1103 on a 770pt page).
	 * Chrome: an item wholly below the cut line goes on the next page where the unbroken row put it, one that the cut
	 * line crosses starts the next page, and a centered list that the cut line crosses splits between its lines.
	 */
	public void testCenteredItemInSplitRow() throws Exception {
		final String style = "@page { size: 300pt 300pt; margin: 0 } body { margin: 0; font: 10pt/15pt serif }"
				+ " p, ul { margin: 0 } .a { display: grid; grid-auto-flow: column; align-items: center;"
				+ " justify-content: start; gap: 10pt }";
		// 50 lines (750pt) from the page top: TAGS at 367.5, page 2 at 67.5.
		List<String> pages = convertPages("center-below", centeredRow(style, 0, 50, "<li>TAGS</li>"));
		assertFalse("TAGS は 1 頁目に残らない:\n" + pages.get(0), pages.get(0).contains("Text[\"TAGS\""));
		assertEquals("TAGS は 2 頁目の行の中ほど", 67.5, y(pages.get(1), "TAGS"), 0.5);
		// 40 lines (600pt): TAGS at 292.5 crosses the page bottom and starts page 2.
		pages = convertPages("center-across", centeredRow(style, 0, 40, "<li>TAGS</li>"));
		assertFalse("TAGS は 1 頁目に残らない:\n" + pages.get(0), pages.get(0).contains("Text[\"TAGS\""));
		assertEquals("TAGS は 2 頁目の頭", 0, y(pages.get(1), "TAGS"), 0.5);
		// 30 lines after 100pt, 8 tags (120pt) at 265: two lines fit on page 1, the rest start page 2.
		pages = convertPages("center-split", centeredRow(style, 100, 30,
				"<li>TAGS</li><li>T2</li><li>T3</li><li>T4</li><li>T5</li><li>T6</li><li>T7</li><li>T8</li>"));
		assertEquals("TAGS", 265, y(pages.get(0), "TAGS"), 0.5);
		assertEquals("T2", 280, y(pages.get(0), "T2"), 0.5);
		assertEquals("T3 は 2 頁目の頭", 0, y(pages.get(1), "T3"), 0.5);
		assertOnPaper(pages);
		// An explicit row taller than the page with nothing that splits: TAGS at 392.5 goes on where the row continues
		// (Chrome: 92.25 on page 2; Copper leaves out the blank first page).
		pages = convertPages("center-tall-row", document(style.replace("grid-auto-flow: column;",
				"grid-template-rows: 800pt;"), "<div class=\"a\"><ul><li>TAGS</li></ul></div><p>END</p>"));
		final String tags = pages.stream().filter(p -> p.contains("Text[\"TAGS\"")).findFirst().orElseThrow();
		assertEquals("TAGS は行の続きの中ほど", 92.5, y(tags, "TAGS"), 0.5);
		assertOnPaper(pages);
	}

	private static void assertOnPaper(final List<String> pages) {
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				assertTrue(m.group(3) + " が紙面の外:\n" + page, Double.parseDouble(m.group(2)) < 300);
			}
		}
	}

	private static String centeredRow(final String style, final int before, final int lines, final String tags) {
		final StringBuilder body = new StringBuilder();
		if (before > 0) {
			body.append("<div style=\"height: ").append(before).append("pt\">PRE</div>");
		}
		body.append("<div class=\"a\"><ul>").append(tags).append("</ul><div>");
		for (int i = 0; i < lines; ++i) {
			body.append("<p>D").append(i).append(" lorem ipsum dolor sit amet.</p>");
		}
		body.append("</div></div><p>END</p>");
		return document(style, body.toString());
	}

	/**
	 * A grid item contributes its padding and margins to its column, and an outside list marker nothing (2026-10-09).
	 * The padding and margins of an item that is its own box were resolved only after the columns were sized, so its
	 * column was as wide as its text; the marker, which hangs in front of the line, was counted. A ul with padding-left:
	 * 40px got a column of marker + text, its text wrapped below the marker line or ran into the next column. Chrome: the
	 * column is as wide as the same list floated (55.3pt), 25.3pt without the padding, 89.99 for width: 50pt + 40px.
	 */
	public void testItemFrameAndOutsideMarkerContribute() throws Exception {
		final String page = convertPages("item-frame", document("""
				@page { size: 300pt 300pt; margin: 0 } body { font: 10pt/15pt serif }
				ul { margin: 0; padding: 0 0 0 40px }
				.a { display: grid; grid-auto-flow: column; justify-content: start; gap: 10pt }
				""", """
				<div class="a"><ul><li>T1</li></ul><div>G1</div></div>
				<div><ul style="float: left"><li>T2</li></ul>F1</div>
				<div class="a" style="clear: both"><ul style="padding: 0"><li>T3</li></ul><div>G3</div></div>
				<div class="a"><div style="margin-left: 40px">T4</div><div>G4</div></div>
				<div class="a"><div style="width: 50pt; padding-left: 40px">T5</div><div>G5</div></div>
				""")).get(0);
		final double floated = x(page, "F1");
		assertEquals("padding を数え、マーカーは数えない(浮動と同じ幅):\n" + page, floated + 10, x(page, "G1"), 0.5);
		assertEquals("T1 は折り返さない", y(page, "G1"), y(page, "T1"), 0.5);
		assertEquals("padding 0 ならマーカーを数えず字の幅だけ", floated - 30 + 10, x(page, "G3"), 0.5);
		assertEquals("margin も数える", floated + 10, x(page, "G4"), 0.5);
		assertEquals("width: 50pt と padding 30pt", 90, x(page, "G5"), 0.5);
	}

	/**
	 * A descendant's max-inline-size caps what a grid or flex item contributes to its container's min-content size,
	 * as it already did for a plain shrink-to-fit block (Chrome: the boxes are 300px = 225pt).
	 */
	public void testMaxSizeInItemContribution() throws Exception {
		final String line = "d".repeat(80);
		final String page = convert("max-size", document("""
				body { font: 12px/1.5 monospace }
				pre { margin: 0; max-inline-size: 300px }
				.box { width: min-content; background: #fcc }
				.grid { display: grid; grid-template-columns: 1fr }
				.flex { display: flex }
				""", "<div class=\"box\"><div class=\"grid\"><div><pre>%s</pre></div></div></div>".formatted(line)
				+ "<div class=\"box\"><div class=\"flex\"><div><pre>%s</pre></div></div></div>".formatted(line)
				+ "<div class=\"box\"><div class=\"grid\"><div><div><pre>%s</pre></div></div></div></div>"
						.formatted(line)
				+ "<div class=\"box\"><div><pre>%s</pre></div></div>".formatted(line)));
		final List<double[]> frames = frames(page);
		assertEquals("枠が4つ:\n" + page, 4, frames.size());
		final String[] names = { "grid > div > pre", "flex > div > pre", "grid > div > div > pre", "div > pre" };
		for (int i = 0; i < names.length; ++i) {
			assertEquals(names[i] + " の箱の幅", 225, frames.get(i)[2], 0.01);
		}
	}

	/** Frames in display-list order: x, y, w, h. */
	private static List<double[]> frames(final String page) {
		final List<double[]> frames = new ArrayList<>();
		final Matcher m = FRAME.matcher(page);
		while (m.find()) {
			frames.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
					Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) });
		}
		return frames;
	}

	/**
	 * A column subgrid's cells size the parent's intrinsic tracks as if they were the parent's own items
	 * (css-grid-2 §9, 2026-10-09). Before, the subgrid counted as one item spanning all three tracks: its contribution
	 * went to the 1fr track alone, the auto tracks stayed at zero and the cells were drawn on top of each other
	 * (primer-css's prop tables). Each subgrid row (line numbers, reversed lines, span, a subgrid in a subgrid)
	 * places its cells where the same cells sit as direct items.
	 */
	public void testSubgridCellsSizeAutoTracks() throws Exception {
		// Cell texts are digits (equal advances): "1r", "22222r", "3r" for row r.
		final String cells = "<div class=\"c\">1%1$d</div><div class=\"c\">22222%1$d</div><div class=\"c\">3%1$d</div>";
		final StringBuilder body = new StringBuilder();
		body.append("<div class=\"g\">").append(cells.formatted(0)).append("</div>");
		final String[] subgrids = { "grid-column: 1 / -1", "grid-column: -1 / 1", "grid-column: span 3" };
		for (int r = 1; r <= subgrids.length; ++r) {
			body.append("<div class=\"g\"><div class=\"s\" style=\"").append(subgrids[r - 1]).append("\">")
					.append(cells.formatted(r)).append("</div></div>");
		}
		body.append("<div class=\"g\"><div class=\"s\" style=\"grid-column: 1 / -1\"><div class=\"s\" style=\"grid-column: 1 / -1\">")
				.append(cells.formatted(4)).append("</div></div></div>");
		final String page = convert("subgrid-auto", document("""
				.g { display: grid; grid-template-columns: auto auto 1fr; margin-bottom: 8pt }
				.s { display: grid; grid-template-columns: subgrid }
				.c { padding: 2pt }
				""", body.toString()));
		final double[] direct = { x(page, "10"), x(page, "222220"), x(page, "30") };
		assertTrue("直下の項目の列が並ぶ:\n" + page, direct[0] < direct[1] && direct[1] < direct[2]);
		for (int r = 1; r <= 4; ++r) {
			assertEquals(r + " 行目の 1 列目:\n" + page, direct[0], x(page, "1" + r), 0.01);
			assertEquals(r + " 行目の 2 列目:\n" + page, direct[1], x(page, "22222" + r), 0.01);
			assertEquals(r + " 行目の 3 列目:\n" + page, direct[2], x(page, "3" + r), 0.01);
		}
	}

	/**
	 * Auto-only parent tracks take their widths from the subgrid's cells and share the rest equally, as Chrome does
	 * (Chrome: A1 1.7pt, AAAAAA2 116.5pt, A3 267.3pt in a 380pt grid; the cells used to get 126.7pt each).
	 */
	public void testSubgridCellsSizeAutoOnlyTracks() throws Exception {
		final String page = convert("subgrid-auto-only", """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 400pt 300pt; margin: 10pt }
				body { margin: 0; font: 10pt/15pt serif }
				.g { display: grid; grid-template-columns: auto auto auto }
				.s { display: grid; grid-template-columns: subgrid; grid-column: 1 / -1 }
				.c { padding: 2pt }
				</style></head><body>
				<div class="g"><div class="s"><div class="c">A1</div><div class="c">AAAAAA2</div><div class="c">A3</div></div></div>
				</body></html>
				""");
		assertEquals("AAAAAA2 の位置:\n" + page, 116.5, x(page, "AAAAAA2"), 1.5);
		assertEquals("A3 の位置", 267.3, x(page, "A3"), 1.5);
	}

	/**
	 * Table parts as grid or flex items are blockified one by one (CSS Display 3 §2.7, 2026-10-09). Before, consecutive
	 * table cells shared one anonymous table: in a grid all three went into its first column, in a column flex they
	 * stood side by side, and a table whose rows are subgrids lost its columns. Chrome: G1 G2 G3 at 0, 100, 200pt;
	 * F2 below F1; the rows of the subgrid table share their columns.
	 */
	public void testTablePartsAreBlockifiedItems() throws Exception {
		final String page = convert("table-parts-items", document("""
				.g { display: grid; grid-template-columns: 100pt 100pt 100pt; margin-bottom: 8pt }
				.fc { display: flex; flex-direction: column; margin-bottom: 8pt }
				.tc { display: table-cell }
				.t { display: grid; grid-template-columns: auto auto 1fr; margin: 0 }
				.t > tbody, .t > tbody > tr { display: grid; grid-column: -1 / 1; grid-template-columns: subgrid }
				td { padding: 2pt }
				""", "<div class=\"g\"><div class=\"tc\">G1</div><div class=\"tc\">G2</div><div class=\"tc\">G3</div></div>"
				+ "<div class=\"fc\"><div class=\"tc\">F1</div><div class=\"tc\">F2</div></div>"
				+ "<table class=\"t\"><tbody><tr><td>A1</td><td>A2</td><td>AAAA3</td></tr>"
				+ "<tr><td>BBBBBB1</td><td>BBBB2</td><td>B3</td></tr></tbody></table>"));
		assertEquals("G2:\n" + page, 100, x(page, "G2"), 0.01);
		assertEquals("G3", 200, x(page, "G3"), 0.01);
		assertEquals("F2 は F1 の下", x(page, "F1"), x(page, "F2"), 0.01);
		assertTrue("F2 は F1 の下", y(page, "F2") > y(page, "F1"));
		assertEquals("表の 2 列目がそろう", x(page, "A2"), x(page, "BBBB2"), 0.01);
		assertEquals("表の 3 列目がそろう", x(page, "AAAA3"), x(page, "B3"), 0.01);
		assertTrue("表の列が並ぶ", x(page, "BBBBBB1") < x(page, "BBBB2") && x(page, "BBBB2") < x(page, "B3"));
	}

	/**
	 * An auto-width table that is a grid item fills its stretched area; with justify-self: start it keeps its
	 * content width (2026-10-09). It used to keep its content width in both. Chrome: GA2 at 250pt (the table spans
	 * the 500pt grid), GB2 at 125pt (one 250pt column), GC2 right after GC1.
	 */
	public void testTableItemFillsItsArea() throws Exception {
		final String page = convert("table-item-stretch", document("""
				.g { display: grid; grid-template-columns: 1fr 1fr; margin-bottom: 8pt }
				table { border-spacing: 0 }
				td { padding: 0 }
				""", "<div class=\"g\"><table style=\"grid-column: 1 / -1\"><tr><td>GA1</td><td>GA2</td></tr></table></div>"
				+ "<div class=\"g\"><table><tr><td>GB1</td><td>GB2</td></tr></table><div>other</div></div>"
				+ "<div class=\"g\"><table style=\"justify-self: start\"><tr><td>GC1</td><td>GC2</td></tr></table></div>"));
		assertEquals("GA2:\n" + page, 250, x(page, "GA2"), 0.5);
		assertEquals("GB2", 125, x(page, "GB2"), 0.5);
		assertTrue("GC2 は GC1 のすぐ後", x(page, "GC2") < 30);
	}

	/**
	 * A grid item's auto margins take the free space of its area, and keep it on every page the item runs over
	 * (css-grid-1 §11.1, 2026-10-09). Before, the first page kept the item at the start of the area and each later
	 * page moved it right by another 40pt (materialui). Chrome: the text starts at 70pt on every page (a 200pt
	 * border-box item centered in a 320pt column, 10pt padding). A width: 100% item with border-box spans the whole
	 * area (the percentage refers to the area, not the area less the item's frame): its text starts at its padding.
	 */
	public void testItemAutoMarginsCenterOnEveryPage() throws Exception {
		final StringBuilder paras = new StringBuilder();
		for (int i = 0; i < 30; ++i) {
			paras.append("<p>P").append(i).append(" lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do.</p>");
		}
		final List<String> pages = convertPages("item-auto-margins", """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 400pt 300pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 15pt }
				p { margin: 0 0 4pt }
				.g { display: grid; grid-template-columns: 1fr 80pt }
				.c { margin: 0 auto; max-width: 200pt; padding: 0 10pt; box-sizing: border-box; background: #eee }
				.w { width: 100%%; padding: 0 18pt; margin: 0 auto; box-sizing: border-box }
				.f { width: 100%%; padding: 0 18pt; box-sizing: border-box; background: #ccc }
				</style></head><body><div class="g"><div class="c">%s</div><div>NAV</div></div>
				<div class="g"><div class="w"><p>WIDE</p></div></div>
				<div class="g"><div class="f"><p>FULL</p></div></div>
				</body></html>
				""".formatted(paras));
		assertTrue("3 頁以上", pages.size() >= 3);
		for (int i = 0; i < 3; ++i) {
			final Matcher m = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"P\\d+\"").matcher(pages.get(i));
			assertTrue((i + 1) + " 頁目に本文が無い", m.find());
			assertEquals((i + 1) + " 頁目の本文の左端:\n" + pages.get(i), 70, Double.parseDouble(m.group(1)), 0.5);
		}
		// A percentage refers to the grid area (320pt), not the area less the item's padding (Chrome: 320).
		final String last = pages.get(pages.size() - 1);
		final Matcher full = Pattern.compile("x=0\\.00 y=[\\d.]+ AbsoluteRectFrame\\[w=([\\d.]+)").matcher(last);
		assertTrue("width: 100% の項目の背景が無い:\n" + last, full.find());
		assertEquals("width: 100% の項目の幅:\n" + last, 320, Double.parseDouble(full.group(1)), 0.5);
		assertEquals("width: 100% で auto margin の項目", 18, x(last, "WIDE"), 0.5);
	}

	private static double x(final String page, final String text) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(text)) {
				return Double.parseDouble(m.group(1));
			}
		}
		throw new AssertionError(text + " が無い:\n" + page);
	}

	private static double y(final String page, final String text) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(text)) {
				return Double.parseDouble(m.group(2));
			}
		}
		throw new AssertionError(text + " が無い:\n" + page);
	}

	/** Convert and return the first page's display list. */
	private static String convert(final String name, final String html) throws Exception {
		return convertPages(name, html).get(0);
	}

	/** Convert and return every page's display list. */
	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/grid-edge-cases/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "grid-edge-cases-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		Arrays.sort(pages);
		final List<String> result = new ArrayList<>();
		for (final File page : pages) {
			result.add(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return result;
	}
}
