package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * The body of a page starts below a full-width {@code float: top} figure (2026-10-17, TECH-20261017-004, from the
 * publishing department's book). Top floats were exclusions for line scanning only, so what does not lay out lines
 * at the page start ignored them: a table moved or continued to the page the figure was carried to, the continuation
 * of a bordered box, and a table right after a figure that opens the page were drawn over the figure. A table placed
 * below a page-float band also got its top margin twice, and one with a top margin escaped the band check altogether.
 *
 * <p>
 * Each document has a 150 pt figure with a 12 pt caption line (FIGCAP at y = 150), so the body starts at 162 pt.
 * </p>
 */
public class TopFloatBodyStartTest extends TestCase {
	private static final Pattern ENTRY = Pattern
			.compile("x=([-\\d.]+) y=([-\\d.]+) (\\w+)\\[(?:\"([^\"]*)\"|w=([-\\d.]+) h=([-\\d.]+))");

	private static final double FIGURE_END = 162;

	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page { size: 300pt 400pt; margin: 0 } body { margin: 0; font: 10pt/12pt serif } p { margin: 0 }"
			+ " .fig { float: top; width: 100% } .art { height: 150pt; background: #9fd3ff }"
			+ " table { border-collapse: collapse; background: #ffb060; margin: 10pt auto } td, th { padding: 0 }"
			+ " .box { border: 1pt solid black; background: #ffb060; padding: 4pt }</style></head><body>";

	private static final String FIGURE = "<div class=\"fig\"><div class=\"art\"></div><p>FIGCAP</p></div>";

	private record Entry(double y, String kind, String text, double height) {
	}

	/** A table that does not fit after the figure moves to the next page, below the figure carried there. */
	public void testMovedTableBelowCarriedFigure() throws Exception {
		final List<List<Entry>> pages = layout(
				body(30) + FIGURE + "<table style=\"break-inside: avoid\">" + rows(6) + "</table>");
		final List<Entry> page = figurePage(pages, 2);
		assertEquals("table border box, one margin below the figure", FIGURE_END + 10, first(page, "BackgroundBorder").y,
				0.01);
		assertBelowFigure(page);
	}

	/** The continuation of a table split before the figure's page starts below the figure. */
	public void testSplitTableBelowCarriedFigure() throws Exception {
		final List<List<Entry>> pages = layout(body(28) + FIGURE
				+ "<table><thead><tr><th>HEAD</th><th>y</th></tr></thead><tbody>" + rows(8) + "</tbody></table>");
		assertTrue("the table starts on the first page", text(pages.get(0), "HEAD") != null);
		assertBelowFigure(figurePage(pages, 2));
	}

	/** The continuation of a bordered box starts below the figure; the figure is no longer drawn inside the box. */
	public void testBoxContinuationBelowCarriedFigure() throws Exception {
		final StringBuilder box = new StringBuilder("<div class=\"box\">");
		for (int i = 1; i <= 8; ++i) {
			box.append("<p>BOXTEXT ").append(i).append("</p>");
		}
		final List<List<Entry>> pages = layout(body(30) + FIGURE + box + "</div>");
		assertBelowFigure(figurePage(pages, 2));
	}

	/** A table right after a figure that opens the page starts below it, with its margin once. */
	public void testTableAfterFirstFigure() throws Exception {
		final List<List<Entry>> pages = layout(FIGURE + "<table>" + rows(3) + "</table>");
		final List<Entry> page = figurePage(pages, 1);
		assertEquals("table border box, one margin below the figure", FIGURE_END + 10, first(page, "BackgroundBorder").y,
				0.01);
		assertBelowFigure(page);
	}

	/**
	 * A table that does not fit beside a narrow figure moves below it, and below the full-width figure stacked under
	 * that one too (codex review: one margin below the first band reached into the second).
	 */
	public void testTableBelowStackedBands() throws Exception {
		final List<List<Entry>> pages = layout("<div class=\"fig\" style=\"width: 150pt\"><div style=\"height: 100pt\"></div></div>"
				+ "<div class=\"fig\"><div style=\"height: 15pt\"></div></div><p>LEAD</p>"
				+ "<table style=\"width: 300pt; margin: 10pt 0 0\"><tr><td>CELL</td></tr></table>");
		assertEquals("pages", 1, pages.size());
		assertEquals("table border box, one margin below both figures", 125, first(pages.get(0), "BackgroundBorder").y,
				0.01);
	}

	private static String body(final int lines) {
		final StringBuilder out = new StringBuilder();
		for (int i = 1; i <= lines; ++i) {
			out.append("<p>BODY ").append(i).append("</p>");
		}
		return out.toString();
	}

	private static String rows(final int count) {
		final StringBuilder out = new StringBuilder();
		for (int i = 1; i <= count; ++i) {
			out.append("<tr><td>CELL ").append(i).append("</td><td>x</td></tr>");
		}
		return out.toString();
	}

	/** The page holding the figure, which must be the expected one, with the figure at the top. */
	private static List<Entry> figurePage(final List<List<Entry>> pages, final int expected) {
		for (int i = 0; i < pages.size(); ++i) {
			final Entry caption = text(pages.get(i), "FIGCAP");
			if (caption != null) {
				assertEquals("page of the figure", expected, i + 1);
				assertEquals("figure caption at the top", 150, caption.y, 0.01);
				return pages.get(i);
			}
		}
		throw new AssertionError("no figure");
	}

	/** Every frame or background but the figure's own, and every text but the caption, starts below the figure. */
	private static void assertBelowFigure(final List<Entry> page) {
		int below = 0;
		for (final Entry entry : page) {
			final boolean figure = "FIGCAP".equals(entry.text)
					|| "AbsoluteRectFrame".equals(entry.kind) && entry.y == 0 && entry.height == 150;
			if (figure || entry.kind.equals("drawer")) {
				continue;
			}
			assertTrue(entry.kind + (entry.text == null ? "" : " " + entry.text) + " at y=" + entry.y
					+ " overlaps the figure", entry.y >= FIGURE_END - 0.01);
			++below;
		}
		assertTrue("content below the figure", below > 0);
	}

	private static Entry text(final List<Entry> page, final String text) {
		for (final Entry entry : page) {
			if (text.equals(entry.text)) {
				return entry;
			}
		}
		return null;
	}

	private static Entry first(final List<Entry> page, final String kind) {
		for (final Entry entry : page) {
			if (kind.equals(entry.kind)) {
				return entry;
			}
		}
		throw new AssertionError("no " + kind);
	}

	private static List<List<Entry>> layout(final String body) throws Exception {
		final File dir = Files.createTempDirectory("top-float-body-start").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), HEAD + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<List<Entry>> pages = new ArrayList<>();
		for (int i = 1;; ++i) {
			final File page = new File(dir, String.format("page-%04d.txt", i));
			if (!page.exists()) {
				break;
			}
			final List<Entry> entries = new ArrayList<>();
			final Matcher m = ENTRY.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				entries.add(new Entry(Double.parseDouble(m.group(2)), m.group(3), m.group(4),
						m.group(6) == null ? Double.NaN : Double.parseDouble(m.group(6))));
			}
			pages.add(entries);
		}
		return pages;
	}
}
