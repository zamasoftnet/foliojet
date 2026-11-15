package jp.cssj.test.unit._0400_column_count;

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
 * Page floats in a multicol (2026-11-15, publishing report TECH-20261115-001). A {@code float: top} figure with
 * {@code column-span: all} and {@code width: 100%} is as wide as the multicol, not a column; and a multicol does not run
 * beside a page float, it starts below the figure's band. Before, the spanning figure came out a column wide, and the
 * next page put both columns in the half beside it, the column gap off the middle.
 */
public class PageFloatInMulticolTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=([-\\d.]+) y=([-\\d.]+) Text\\[\"(.*?)\"");

	private static final double PAGE = 300, MARGIN = 20;

	private static String words(final String prefix, final int count) {
		final StringBuilder p = new StringBuilder("<p>");
		for (int i = 1; i <= count; ++i) {
			p.append(prefix).append(i).append(' ');
		}
		return p.append("</p>").toString();
	}

	private static List<String> pages(final String figure) throws Exception {
		final String doc = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page{size:" + PAGE + "pt " + PAGE + "pt;margin:" + MARGIN + "pt} body{font:8pt/12pt serif}"
				+ " p{margin:0} .c{column-count:2;column-gap:12pt}"
				+ " .fig{float:top;margin:0 0 8pt 0} .box{height:60pt;border:1pt solid black}" + figure
				+ "</style></head><body><div class=\"c\">" + words("A", 40)
				+ "<div class=\"fig\"><div class=\"box\">FIG</div></div>" + words("B", 260) + "</div></body></html>";
		final File dir = Files.createTempDirectory("page-float-multicol").toFile();
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

	/** The page with the figure: no word above the figure's bottom, and words in both columns. */
	private static void assertColumnsBelowTheFigure(final List<String> pages) {
		for (final String page : pages) {
			final Matcher fig = Pattern.compile("x=([-\\d.]+) y=([-\\d.]+) Text\\[\"FIG\"").matcher(page);
			if (!fig.find()) {
				continue;
			}
			final double figTop = Double.parseDouble(fig.group(2));
			double highest = Double.POSITIVE_INFINITY, rightmost = Double.NEGATIVE_INFINITY;
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				if (!m.group(3).equals("FIG")) {
					highest = Math.min(highest, Double.parseDouble(m.group(2)));
					rightmost = Math.max(rightmost, Double.parseDouble(m.group(1)));
				}
			}
			assertTrue("a word at " + highest + " beside the figure at " + figTop, highest > figTop + 50);
			assertTrue("the second column is used: " + rightmost, rightmost > (PAGE - 2 * MARGIN) / 2);
			return;
		}
		fail("no page with the figure");
	}

	/** The figure spans the columns and is as wide as the multicol. */
	public void testSpanningFigure() throws Exception {
		final List<String> pages = pages(" .fig{column-span:all;width:100%}");
		assertColumnsBelowTheFigure(pages);
		// The box of the figure: as wide as the multicol (the page less the margins and the body's 8px each side)
		final Matcher box = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=6[0-9]\\.").matcher(String.join("", pages));
		assertTrue(box.find());
		assertEquals(PAGE - 2 * MARGIN - 12, Double.parseDouble(box.group(1)), 1);
	}

	/** A figure a column wide: the multicol starts below its band rather than beside it. */
	public void testColumnWideFigure() throws Exception {
		assertColumnsBelowTheFigure(pages(""));
	}
}
