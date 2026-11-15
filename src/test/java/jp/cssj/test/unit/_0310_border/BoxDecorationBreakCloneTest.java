package jp.cssj.test.unit._0310_border;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
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
 * {@code box-decoration-break: clone}, border only (2026-11-15, publishing report TECH-20261115-001): where a break
 * cuts a block box, each fragment paints the border on the cut side too, inside its own area, and nothing is laid out
 * differently from {@code slice}. A {@code column-span: all} element inside a box cuts it the same way.
 */
public class BoxDecorationBreakCloneTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=([-\\d.]+) y=([-\\d.]+) Text\\[\"(.*?)\"");

	private static final String CUT = " cut-border\\[[trbl]+\\]";

	private static String lines(final String name, final int count) {
		final StringBuilder p = new StringBuilder("<p>");
		for (int i = 1; i <= count; ++i) {
			p.append(i > 1 ? "<br />" : "").append(name).append(i);
		}
		return p.append("</p>").toString();
	}

	/** The display list of each page. */
	private static List<String> pages(final String css, final String body) throws Exception {
		final String doc = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page{size:300pt 200pt;margin:20pt} body{margin:0;font:10pt/20pt serif} p{margin:0;orphans:1;widows:1}"
				+ " .box{border:2pt solid black;border-left-width:6pt;padding:8pt;margin:10pt 0}"
				+ " .c{column-count:2;column-gap:20pt} .span{column-span:all}" + css + "</style></head><body>" + body
				+ "</body></html>";
		final File dir = Files.createTempDirectory("box-decoration-break").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), doc, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] files = dir.listFiles((d, name) -> name.startsWith("page-") && name.endsWith(".txt"));
		Arrays.sort(files);
		final List<String> pages = new ArrayList<>();
		for (final File file : files) {
			pages.add(Files.readString(file.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}

	private static List<String> cutBorders(final String page) {
		final List<String> found = new ArrayList<>();
		final Matcher m = Pattern.compile("cut-border\\[([trbl]+)\\]").matcher(page);
		while (m.find()) {
			found.add(m.group(1));
		}
		return found;
	}

	private static double[] at(final List<String> pages, final String word) {
		for (int i = 0; i < pages.size(); ++i) {
			final Matcher m = TEXT.matcher(pages.get(i));
			while (m.find()) {
				if (m.group(3).equals(word)) {
					return new double[] { i, Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
				}
			}
		}
		throw new AssertionError(word + " is not drawn");
	}

	/** A box cut by a page break closes on both pages, and everything stays where slice puts it. */
	public void testPageBreak() throws Exception {
		final String body = "<div class=\"box\">" + lines("A", 12) + "</div>";
		final List<String> slice = pages("", body);
		final List<String> clone = pages(" .box{box-decoration-break:clone}", body);
		assertEquals(2, clone.size());
		assertEquals(List.of("b"), cutBorders(clone.get(0)));
		assertEquals(List.of("t"), cutBorders(clone.get(1)));
		assertEquals(List.of(), cutBorders(slice.get(0)));
		for (int i = 0; i < clone.size(); ++i) {
			assertEquals("page " + (i + 1), slice.get(i), clone.get(i).replaceAll(CUT, ""));
		}
	}

	/** A box cut twice paints both cut sides of the middle fragment. */
	public void testMiddleFragment() throws Exception {
		final List<String> clone = pages(" .box{-webkit-box-decoration-break:clone}",
				"<div class=\"box\">" + lines("A", 20) + "</div>");
		assertEquals(3, clone.size());
		assertEquals(List.of("b"), cutBorders(clone.get(0)));
		assertEquals(List.of("tb"), cutBorders(clone.get(1)));
		assertEquals(List.of("t"), cutBorders(clone.get(2)));
	}

	/** A box cut by a column break closes in both columns, and everything stays where slice puts it. */
	public void testColumnBreak() throws Exception {
		final String body = "<div class=\"c\"><div class=\"box\">" + lines("A", 8) + "</div></div>";
		final List<String> slice = pages("", body);
		final List<String> clone = pages(" .box{box-decoration-break:clone}", body);
		assertEquals(1, clone.size());
		assertEquals(List.of("b", "t"), cutBorders(clone.get(0)));
		assertEquals(slice.get(0), clone.get(0).replaceAll(CUT, ""));
	}

	/**
	 * Fragments that a re-layout puts back together in one column paint as one box. The box fills the first column and
	 * goes on in the second; the spanner then balances the columns from the boxes, the new break falls earlier, and the
	 * rest of the first fragment lies right before the second. Each closed with its own border, a rule ran across the
	 * box. A box inside, cut by the same breaks, joins with them.
	 */
	public void testFragmentsPutBackTogether() throws Exception {
		final String tail = "<div class=\"span\">S</div><p>B1</p></div>";
		final String css = " .box{box-decoration-break:clone}";
		assertEquals(List.of("b", "t"), cutBorders(
				pages(css, "<div class=\"c\"><div class=\"box\">" + lines("A", 10) + "</div>" + tail).get(0)));
		assertEquals(List.of("b", "b", "t", "t"), cutBorders(pages(css + " .box .box{margin:0}",
				"<div class=\"c\"><div class=\"box\"><div class=\"box\">" + lines("A", 8) + "</div></div>" + tail)
				.get(0)));
	}

	/** Without a border to close, and on a box that no break cuts, clone paints nothing more. */
	public void testNothingToClose() throws Exception {
		assertEquals(List.of(), cutBorders(String.join("", pages(" .box{box-decoration-break:clone}",
				"<div class=\"box\">" + lines("A", 3) + "</div>"))));
		assertEquals(List.of(), cutBorders(String.join("", pages(" .box{box-decoration-break:clone;border:none}",
				"<div class=\"box\">" + lines("A", 12) + "</div>"))));
	}

	/**
	 * A column-span: all element inside a box cuts the box as a break does: no margin, border or padding at the cut, so
	 * two spanners in a row have nothing between them and the rest starts at the same height in both columns.
	 */
	public void testSpannerInsideABox() throws Exception {
		final String body = "<div class=\"c\"><div class=\"box\">" + lines("A", 2)
				+ "<div class=\"span\">S1</div><div class=\"span\">S2</div>" + lines("B", 4) + "</div></div>";
		final List<String> slice = pages("", body);
		final double[] s1 = at(slice, "S1"), s2 = at(slice, "S2"), b1 = at(slice, "B1"), b4 = at(slice, "B4");
		assertEquals("S2 follows S1", 20, s2[2] - s1[2], 0.5);
		assertEquals("B1 follows S2", 20, b1[2] - s2[2], 0.5);
		assertTrue("B4 is in the second column", b4[1] > b1[1] + 50);
		assertEquals("both columns start at the same height", b1[2], b4[2], 0.5);
		assertEquals(List.of(), cutBorders(slice.get(0)));

		final List<String> clone = pages(" .box{box-decoration-break:clone}", body);
		assertEquals(slice.get(0), clone.get(0).replaceAll(CUT, ""));
		// The fragments of A before the spanners, and of B after them, each closed on its cut sides; the empty piece
		// between the spanners paints nothing
		final List<String> cuts = cutBorders(clone.get(0));
		assertFalse(String.valueOf(cuts), cuts.isEmpty());
		for (final String cut : cuts) {
			assertTrue(cut, cut.equals("t") || cut.equals("b") || cut.equals("tb"));
		}
	}
}
