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
