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
 * A float with a percentage width in a shrink-to-fit box counts as auto while the box is measured (2026-11-02).
 *
 * <p>
 * The percentage refers to the size being measured, so it is cyclic (CSS Sizing 3 §5.2, {@code CyclicPercent}). The
 * simulated measurement read the float's used width instead, which is not known yet. The cell came out 0 wide and the
 * float's text ran one word to a line, 60 paragraphs on 28 pages (Chrome 4). In an inline-block the 0 clipped all of the
 * text away, and in a vertical cell it took the paragraphs off the paper. A block around the float only hid this: the
 * cell stayed 0 wide, the float took its share of the page, and the text of the next cell was drawn over it.
 * </p>
 *
 * <p>
 * Counted as auto, such a float showed what auto floats had always lacked (codex): their own min-width and max-width were
 * not in the contribution, and the content of a float in a box of a definite width went out through its advance.
 * </p>
 */
public class PercentFloatInShrinkToFitTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"([^\"]*)\" asc=[^\\]]*\\]( clip=\\[[^\\]]*\\])?");

	private static final String WORDS = " word".repeat(18);

	private static String paragraphs(final int from, final int count) {
		final StringBuilder s = new StringBuilder();
		for (int i = from; i < from + count; ++i) {
			s.append("<p>T").append(i).append('Z').append(WORDS).append("</p>");
		}
		return s.toString();
	}

	private static String floated(final String style, final String content) {
		return "<div style='float:left;" + style + "'>" + content + "</div>";
	}

	private static String document(final String body) {
		return "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\"><head><meta charset=\"UTF-8\"/><style>"
				+ "@page { size: 300pt 200pt; margin: 0 } body { margin: 0; font: 10pt/12pt sans-serif } p { margin: 0 }"
				+ "td { padding: 1pt } table { border-spacing: 0 }</style></head><body>" + body + "</body></html>";
	}

	/** The first line holds what the percentage of the page's 300pt leaves, not a word. */
	public void testFloatTakesItsShareOfTheLine() throws Exception {
		final String p = paragraphs(0, 12);
		final Object[][] forms = { //
				{ "cell", "<table><tr><td>" + floated("width:90%", p) + "</td></tr></table>", 270.0 }, //
				{ "cell-half", "<table><tr><td>" + floated("width:50%", p) + "</td></tr></table>", 150.0 }, //
				{ "cell-calc", "<table><tr><td>" + floated("width:calc(50% - 10pt)", p) + "</td></tr></table>", 140.0 }, //
				{ "cell-frame", "<table><tr><td>" + floated("width:90%;padding:0 5pt;box-sizing:border-box", p)
						+ "</td></tr></table>", 260.0 }, //
				{ "cell-right", "<table><tr><td><div style='float:right;width:90%'>" + p + "</div></td></tr></table>", 270.0 }, //
				{ "display-table-cell", "<div style='display:table'><div style='display:table-cell'>" + floated("width:90%", p)
						+ "</div></div>", 270.0 }, //
				{ "inline-block", "<div style='display:inline-block'>" + floated("width:90%", p) + "</div>", 270.0 }, //
				{ "float", "<div style='float:left'>" + floated("width:90%", p) + "</div>", 270.0 } };
		final List<String> wrong = new ArrayList<>();
		for (final Object[] form : forms) {
			final String name = (String) form[0];
			final List<String> pages = convertPages(name, document((String) form[1]));
			try {
				assertAllDrawn(name, pages, 12);
				final double line = lastWordOfFirstLine(pages.get(0));
				final double share = (Double) form[2];
				assertTrue(name + ": 1 行目の最後の語の位置 " + line, line > share * 0.75 && line < share);
			} catch (final junit.framework.AssertionFailedError e) {
				wrong.add(e.getMessage());
			}
		}
		assertTrue(wrong.toString(), wrong.isEmpty());
	}

	/** Floats side by side add up, as with auto width. */
	public void testFloatsSideBySide() throws Exception {
		final String name = "side-by-side";
		final List<String> pages = convertPages(name, document("<table><tr><td>" + floated("width:30%", paragraphs(0, 6))
				+ floated("width:70%", paragraphs(6, 6)) + "</td></tr></table>"));
		assertAllDrawn(name, pages, 12);
		assertEquals(name + ": 2 つ目の float の左端", 90.4, position(pages, "T6Z")[0], 0.1);
	}

	/** A percentage of a block with a length is not cyclic. */
	public void testDefiniteBoxInCellStaysTheBasis() throws Exception {
		final String name = "definite";
		final List<String> pages = convertPages(name,
				document("<table><tr><td><div style='width:200pt'>" + floated("width:50%", paragraphs(0, 3)) + "</div></td></tr></table>"));
		assertAllDrawn(name, pages, 3);
		final double line = lastWordOfFirstLine(pages.get(0));
		assertTrue(name + ": 1 行目の最後の語の位置 " + line, line > 60 && line < 100);
	}

	/** In a block in a cell, a float asks for its content: the next cell starts past it. */
	public void testShortFloatAsksForItsContent() throws Exception {
		final String name = "short-in-block";
		final List<String> pages = convertPages(name, document(
				"<table><tr><td><div>" + floated("width:50%", "T0Z") + "</div></td><td>T1Z</td></tr></table>"));
		assertAllDrawn(name, pages, 2);
		final double next = position(pages, "T1Z")[0];
		assertTrue(name + ": 隣のセルの左端 " + next, next > 15 && next < 60);
	}

	/** The float's own min-width and max-width hold in what it asks of its host, with a percentage width or auto. */
	public void testOwnMinAndMaxWidth() throws Exception {
		final String[][] forms = { //
				{ "min-percent", "width:90%;min-width:120pt;text-align:right", "T0Z" }, //
				{ "min-auto", "min-width:120pt;text-align:right", "T0Z" }, //
				{ "max-percent", "width:50%;max-width:100pt", "T0Z" + WORDS }, //
				{ "max-auto", "max-width:100pt", "T0Z" + WORDS } };
		final List<String> wrong = new ArrayList<>();
		for (final String[] form : forms) {
			final String name = form[0];
			final List<String> pages = convertPages(name,
					document("<table><tr><td>" + floated(form[1], form[2]) + "</td><td>T1Z</td></tr></table>"));
			final double next = position(pages, "T1Z")[0];
			final double width = name.startsWith("min") ? 122 : 102;
			if (Math.abs(next - width - 1) > 0.5) {
				wrong.add(name + ": 隣のセルの左端 " + next);
			}
		}
		assertTrue(wrong.toString(), wrong.isEmpty());
		// Clipped by a host narrower than the float, the text was gone
		final String name = "min-clipped";
		final List<String> pages = convertPages(name, document("<div style='display:inline-block;overflow:hidden'>"
				+ floated("min-width:120pt;text-align:right", "T0Z") + "</div>"));
		assertAllDrawn(name, pages, 1);
		assertTrue(name + ": 字が箱の中にない", position(pages, "T0Z")[0] > 90);
		assertTrue(name + ": 箱の幅 " + pages.get(0), pages.get(0).contains("clip=[0.00 0.00 120.00"));
	}

	/** Its min-width: max-content and its padding beat its max-width, as in its layout: the host does not cut it off. */
	public void testMaxWidthDoesNotCutTheHost() throws Exception {
		final String[][] forms = { //
				{ "max-with-min-keyword", "min-width:max-content;max-width:20pt" }, //
				{ "max-under-padding", "max-width:10pt;padding-left:100pt;box-sizing:border-box" } };
		for (final String[] form : forms) {
			final String name = form[0];
			final List<String> pages = convertPages(name, document("<div style='display:inline-block;overflow:hidden'>"
					+ floated(form[1], "T0Zlongword") + "</div>"));
			final Matcher m = Pattern.compile("clip=\\[0\\.00 0\\.00 ([\\d.]+) ").matcher(pages.get(0));
			assertTrue(name + ": " + pages.get(0), m.find());
			assertTrue(name + ": 箱の幅 " + m.group(1), Double.parseDouble(m.group(1)) >= (name.startsWith("max-with") ? 40 : 100));
		}
	}

	/** A clear inside a block of a definite width ended the floats before it: they stay ended after the block. */
	public void testClearInDefiniteBox() throws Exception {
		final String name = "clear-in-definite-box";
		final List<String> pages = convertPages(name, document("<table style='width:max-content'><tr><td style='text-align:right'>"
				+ floated("width:180pt;height:10pt", "") + "<div style='width:1pt'><div style='clear:both;height:1pt'></div></div>"
				+ "<span style='display:inline-block;width:50pt'>T0Z</span></td></tr></table>"));
		assertTrue(name + ": 紙の外", position(pages, "T0Z")[0] < 200);
	}

	/** A float in a box of a definite width stays in it: its content does not widen the lines after the box. */
	public void testFloatInDefiniteBoxStaysInIt() throws Exception {
		for (final String width : new String[] { "width:100%", "width:auto" }) {
			final String name = "definite-box-" + width.substring(6, 9);
			final List<String> pages = convertPages(name, document("<table><tr><td><div style='width:100pt;overflow:hidden'>"
					+ floated(width, "T0Z" + WORDS) + "</div><div>T1Z</div></td><td>T2Z</td></tr></table>"));
			assertAllDrawn(name, pages, 3);
			assertEquals(name + ": 隣のセルの左端", 103, position(pages, "T2Z")[0], 0.5);
		}
	}

	/** A vertical cell: the line axis is the height, and every paragraph stays on the paper. */
	public void testVerticalCell() throws Exception {
		final String name = "vertical";
		final String html = document("<div style='writing-mode:vertical-rl;height:200pt'><table><tr><td>"
				+ "<div style='float:left;height:90%'>" + paragraphs(0, 5) + "</div></td></tr></table></div>");
		final List<String> pages = convertPages(name, html);
		assertAllDrawn(name, pages, 5);
		for (int i = 0; i < 5; ++i) {
			final double x = position(pages, "T" + i + "Z")[0];
			assertTrue(name + ": T" + i + " の x=" + x, x >= 0 && x < 300);
		}
	}

	/** Every token is drawn, once, inside a clip with room. */
	private static void assertAllDrawn(final String name, final List<String> pages, final int count) {
		final boolean[] seen = new boolean[count];
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				final String text = m.group(3);
				if (text.startsWith("T") && text.endsWith("Z")) {
					final int i = Integer.parseInt(text.substring(1, text.length() - 1));
					assertFalse(name + ": 2 回描いた " + text, seen[i]);
					seen[i] = true;
					assertFalse(name + ": 幅 0 で切られた " + text + m.group(4),
							m.group(4) != null && m.group(4).matches(" clip=\\[\\S+ \\S+ 0\\.00 \\S+\\]"));
				}
			}
		}
		for (int i = 0; i < count; ++i) {
			assertTrue(name + ": T" + i + " が無い", seen[i]);
		}
	}

	/** Where the last word of the first line of the page starts, from the line's start. */
	private static double lastWordOfFirstLine(final String page) {
		final Matcher m = TEXT.matcher(page);
		assertTrue(m.find());
		final String y = m.group(2);
		final double x = Double.parseDouble(m.group(1));
		double last = x;
		while (m.find() && m.group(2).equals(y)) {
			last = Double.parseDouble(m.group(1));
		}
		return last - x;
	}

	private static double[] position(final List<String> pages, final String text) {
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				if (m.group(3).equals(text)) {
					return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
				}
			}
		}
		fail(text + " が無い");
		return null;
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/percent-float-in-stf/" + name);
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
		}, "percent-float-in-stf-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		Arrays.sort(pages);
		final List<String> result = new ArrayList<>();
		for (final File page : pages) {
			result.add(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return result;
	}
}
