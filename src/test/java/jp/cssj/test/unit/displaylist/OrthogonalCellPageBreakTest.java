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
 * Page breaks in tables with cells of the other writing mode (2026-10-27). A row holding such a cell was never cut
 * nor moved, so a table whose rows had vertical headers never broke and ran off the paper. When an enclosing table's
 * row forced the cut, the cell was cut along its own page axis: it became as wide as the cut line and its
 * continuation kept the whole height, so the row was continued on every page up to the page limit (sweep seed
 * 43722269, mirror). The cell is not cut now: it stays whole in the first fragment and the others are cut.
 */
public class OrthogonalCellPageBreakTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"([^\"]*)\"");

	private static final Pattern BOX = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) BackgroundBorder\\[w=([\\d.]+) h=([\\d.]+)\\]");

	/** The page area is 280pt square. */
	private static final double AREA = 280;

	/** Rows with vertical headers break between rows (they all went on one page, down to y=885). */
	public void testRowsWithVerticalHeaders() throws Exception {
		final StringBuilder rows = new StringBuilder();
		for (int i = 0; i < 30; ++i) {
			rows.append("<tr><th style=\"writing-mode: vertical-lr\">H").append(i).append("</th><td>R").append(i)
					.append("a<br>R").append(i).append("b</td></tr>");
		}
		final List<List<String[]>> pages = layout("", "<table>" + rows + "</table>");
		assertTrue(pages.size() >= 3);
		assertAllOnce(pages, "R", 30, 'a');
		for (final List<String[]> page : pages) {
			for (final String[] t : page) {
				assertTrue(t[2] + " y=" + t[1], Double.parseDouble(t[1]) < AREA);
			}
		}
	}

	/** The same in a vertical table with horizontal cells (it overflowed to the left). */
	public void testVerticalTableWithHorizontalCells() throws Exception {
		final StringBuilder rows = new StringBuilder();
		for (int i = 0; i < 30; ++i) {
			rows.append("<tr><th style=\"writing-mode: horizontal-tb\">H").append(i).append("</th><td>R").append(i)
					.append("a<br>R").append(i).append("b</td></tr>");
		}
		final List<List<String[]>> pages = layout("html { writing-mode: vertical-rl }", "<table>" + rows + "</table>");
		assertTrue(pages.size() >= 3);
		assertAllOnce(pages, "R", 30, 'a');
		for (final List<String[]> page : pages) {
			for (final String[] t : page) {
				assertTrue(t[2] + " x=" + t[0], Double.parseDouble(t[0]) >= 0);
			}
		}
	}

	/** A row taller than the page is cut beside a vertical cell, which stays whole on the first page. */
	public void testRowTallerThanPage() throws Exception {
		final StringBuilder cell = new StringBuilder();
		for (int i = 0; i < 40; ++i) {
			cell.append("<p>L").append(i).append("a</p>");
		}
		final List<List<String[]>> pages = layout("",
				"<table><tr><th style=\"writing-mode: vertical-lr\">VH</th><td>" + cell + "</td></tr></table>");
		assertTrue(pages.size() >= 2);
		assertAllOnce(pages, "L", 40, 'a');
		assertTrue(pages.get(0).stream().anyMatch(t -> t[2].equals("VH")));
		for (final List<String[]> page : pages) {
			for (final String[] t : page) {
				assertTrue(t[2] + " y=" + t[1], Double.parseDouble(t[1]) < AREA);
			}
		}
	}

	/**
	 * The seed's shape: a fixed table with a vertical header in a cell of another table, beside a cell. It took the
	 * page limit; the header's column was as wide as the cut line (390pt instead of 44.25pt) on every page.
	 */
	public void testNestedTable() throws Exception {
		final StringBuilder cell = new StringBuilder();
		for (int i = 0; i < 30; ++i) {
			cell.append("<p>L").append(i).append("a</p>");
		}
		final List<String> boxes = new ArrayList<>();
		final List<List<String[]>> pages = layout("", "<table style=\"table-layout: fixed; width: 139pt\"><tr><td>"
				+ "<table style=\"table-layout: fixed; width: 90pt\"><tr><th style=\"writing-mode: vertical-lr\">VH</th>"
				+ "<td>" + cell + "</td></tr></table></td><td>S</td></tr></table>", boxes);
		assertEquals(2, pages.size());
		assertAllOnce(pages, "L", 30, 'a');
		for (final String b : boxes) {
			final Matcher m = BOX.matcher(b);
			assertTrue(b, m.find());
			assertTrue(b, Double.parseDouble(m.group(3)) < 140);
		}
	}

	private static void assertAllOnce(final List<List<String[]>> pages, final String prefix, final int count,
			final char suffix) {
		final int[] seen = new int[count];
		for (final List<String[]> page : pages) {
			for (final String[] t : page) {
				if (t[2].startsWith(prefix) && t[2].charAt(t[2].length() - 1) == suffix) {
					++seen[Integer.parseInt(t[2].substring(prefix.length(), t[2].length() - 1))];
				}
			}
		}
		for (int i = 0; i < count; ++i) {
			assertEquals(prefix + i + suffix, 1, seen[i]);
		}
	}

	private static List<List<String[]>> layout(final String css, final String body) throws Exception {
		return layout(css, body, new ArrayList<>());
	}

	/** The texts (x, y, text) of each page; the background frames of every page go to {@code boxes}. */
	private static List<List<String[]>> layout(final String css, final String body, final List<String> boxes)
			throws Exception {
		final File dir = Files.createTempDirectory("orthogonal-cell-page-break").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 10pt } body { margin: 0; font: 9pt/12.5pt serif } "
				+ "p { margin: 0 } td, th { border: 1pt solid } " + css + "</style></head><body>" + body
				+ "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.property("output.page-limit", "20");
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<List<String[]>> pages = new ArrayList<>();
		for (int n = 1;; ++n) {
			final File page = new File(dir, String.format("page-%04d.txt", n));
			if (!page.exists()) {
				break;
			}
			final List<String[]> texts = new ArrayList<>();
			for (final String line : Files.readAllLines(page.toPath(), StandardCharsets.UTF_8)) {
				final Matcher m = TEXT.matcher(line);
				if (m.find()) {
					texts.add(new String[] { m.group(1), m.group(2), m.group(3) });
				}
				if (BOX.matcher(line).find()) {
					boxes.add(line.trim());
				}
			}
			pages.add(texts);
		}
		return pages;
	}
}
