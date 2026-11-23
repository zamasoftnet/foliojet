package jp.cssj.test.unit.displaylist;

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
 * Balanced columns hold what no column break cuts (2026-11-23, triage §267). The capacity search took an image, or a
 * block with {@code break-inside: avoid}, at the start of a column as cut at the proposed position: one image in three
 * columns was drawn as three slices and a card was cut over the columns. An image floated in the text put all the text
 * before it in the first column (since 2026-10-09), a float at the end ran over what follows the multicol, and so did a
 * last column longer than the estimate for columns already built. The positions are Chrome's, within a point.
 *
 * <p>
 * Pages of 300x400pt without margins, 6pt text in three columns 88.7pt wide, 105.7pt apart. "AFTER" follows the multicol.
 * </p>
 */
public class MulticolUnbreakableContentTest extends TestCase {
	private static final String GIF = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAAAAAAALAAAAAABAAEAAAIBRAA7";

	public void testImageAlone() throws Exception {
		final String page = columns(block(70, 101));
		assertImage(page, 0, 0, 70, 101);
		assertEquals(101.0, y(page, "AFTER"), 0.5);
	}

	public void testImageBetweenTexts() throws Exception {
		final String page = columns(
				p(100, 140) + "<div>" + block(70, 101) + "</div>" + p(200, 220));
		assertImage(page, 105.67, 0, 70, 101);
		assertTrue("the second text is in the third column", x(page, "T200") > 200);
	}

	public void testImageInPadding() throws Exception {
		final String page = columns(
				"<div style='padding: 10pt; border: 1pt solid'>" + block(40, 101) + "<p>T50</p></div>" + p(100, 112));
		assertImage(page, 11, 11, 40, 101);
		assertTrue("what follows the image is in the second column", x(page, "T50") > 100 && x(page, "T50") < 200);
	}

	public void testImageInFlexAndGridItem() throws Exception {
		for (final String display : new String[] { "display: flex", "display: grid; grid-template-columns: 1fr 1fr" }) {
			final String page = columns(
					"<div style='" + display + "'><div>" + block(40, 101) + "</div><div>T50</div></div>" + p(100, 112));
			assertImage(page, 0, 0, 40, 101);
			assertTrue(display, x(page, "T100") > 100);
		}
	}

	public void testImageInFloat() throws Exception {
		for (final String image : new String[] { block(70, 101), img(70, 101, "") }) {
			final String page = columns("<div style='float: right'>" + image + "</div><p>T50</p><p>" + block(60, 56) + "</p>");
			assertImage(page, 18.67, 0, 70, 101);
			assertImage(page, 105.67, 0, 60, 56);
			assertTrue("after the image", y(page, "AFTER") >= 101);
		}
	}

	public void testImageInVerticalColumns() throws Exception {
		final String page = convert("400pt 300pt", "writing-mode: vertical-rl",
				multicol("<div>" + block(101, 70) + "</div><p>T50</p>") + "<p>AFTER</p>");
		assertImage(page, 299, 0, 101, 70);
		assertEquals("in the second column", 105.67, y(page, "T50"), 0.5);
	}

	public void testAvoidBlocks() throws Exception {
		final String page = columns(card(100, 160) + card(200, 212));
		assertTrue("the first card is in the first column", x(page, "T159") < 100);
		assertEquals("the second card", 105.67, x(page, "T200"), 0.5);
		assertEquals(0.0, y(page, "T200"), 0.5);
		assertEquals("after the first card", 72.0, y(page, "AFTER"), 1.0);
	}

	public void testAvoidTable() throws Exception {
		final StringBuilder rows = new StringBuilder();
		for (int i = 0; i < 6; ++i) {
			rows.append("<tr><td>T5").append(i).append("</td></tr>");
		}
		final String page = columns("<table style='break-inside: avoid'>" + rows + "</table>" + p(100, 112));
		assertTrue("one column", x(page, "T50") < 100 && x(page, "T55") < 100);
		assertTrue("the text is in the second column", x(page, "T100") > 100);
	}

	/** An image floated in the text, where the text before it alone would fill a column. */
	public void testFloatInTheText() throws Exception {
		final String page = columns(p(100, 250) + img(40, 30, "float: right; ") + p(300, 360));
		assertImage(page, 260, 0, 40, 30);
		assertTrue("three columns", x(page, "T200") > 100 && x(page, "T300") > 200);
		assertEquals("after the columns", 93.1, y(page, "AFTER"), 1.0);
	}

	/** An image floated beside the last line: the columns hold it. */
	public void testFloatAtTheEnd() throws Exception {
		final String page = columns(p(100, 252) + img(40, 60, "float: right; ") + p(252, 260));
		final Matcher image = image(page, 40, 60);
		assertTrue("in the third column: " + page, Double.parseDouble(image.group(1)) > 211);
		assertTrue("above what follows", Double.parseDouble(image.group(2)) + 60 <= y(page, "AFTER") + 0.5);
		assertTrue("three columns", y(page, "AFTER") < 100);
	}

	/**
	 * Rows of floats past the end of the flows stay one column, as floats alone do: no flow breaks the columns there, and
	 * taken to be spread over the columns they were drawn over what follows the multicol, or past the end of the page.
	 */
	public void testRowsOfFloatsPastTheFlows() throws Exception {
		final StringBuilder strip = new StringBuilder(p(100, 130));
		final StringBuilder items = new StringBuilder();
		for (int i = 0; i < 7; ++i) {
			strip.append(i < 5 ? img(40, 50, "float: left; ") : "");
			items.append("<div>").append(img(40, 50, "float: left; clear: both; ")).append("<p>T").append(50 + i).append("</p></div>");
		}
		for (final String content : new String[] { strip.toString(), items.toString() }) {
			for (int lead = 0; lead < 400; lead += 40) {
				final List<String> pages = convertPages("300pt 400pt", "writing-mode: horizontal-tb",
						"<div style='height: " + lead + "pt'></div>" + multicol(content) + "<p>AFTER</p>");
				final Pattern image = Pattern.compile("x=[-0-9.]+ y=([-0-9.]+) AbsoluteRectFrame.w=40.00 h=50.00");
				for (final String page : pages) {
					final double end = page.contains("\"AFTER\"") ? y(page, "AFTER") : 400;
					for (final Matcher m = image.matcher(page); m.find();) {
						assertTrue(lead + ": " + m.group() + " before " + end, Double.parseDouble(m.group(1)) + 50 <= end + 0.5);
					}
				}
			}
		}
	}

	/** Content of more than one column: the card moves to the third, longer than the estimate, and the box holds it. */
	public void testLastColumnLongerThanTheEstimate() throws Exception {
		final String page = columns(p(100, 420) + card(500, 740) + p(740, 760));
		assertTrue("the third column", x(page, "T740") > 211);
		assertTrue("above what follows", y(page, "T759") < y(page, "AFTER"));
	}

	/** A heading stays with the image after it, if the columns can be long enough for both. */
	public void testHeadingKeptWithTheImage() throws Exception {
		final String page = columns("<div style='break-after: avoid'>T50</div><div>" + block(70, 101) + "</div>" + p(100, 130));
		assertImage(page, 0, 7.2, 70, 101);
		assertTrue("the text is in the second column", x(page, "T100") > 100);
		final String second = columns(p(100, 180) + "<div style='break-after: avoid'>T50</div><div>" + block(70, 101) + "</div>");
		assertEquals("the heading starts the second column", 105.67, x(second, "T50"), 0.5);
		assertImage(second, 105.67, 7.2, 70, 101);
	}

	/** Cards kept together that are longer than the page are not: each starts a column. */
	public void testKeptCardsLongerThanThePage() throws Exception {
		final String page = convertPages("300pt 300pt", "writing-mode: horizontal-tb", "<div style='position: absolute; width: 300pt'>"
				+ multicol("<div style='break-inside: avoid; break-after: avoid; height: 160pt'>T50</div>"
						+ "<div style='break-inside: avoid; height: 160pt'>T51</div>")
				+ "</div>").get(0);
		assertTrue("in the first column", x(page, "T50") < 100);
		assertEquals("at the head of the second", 105.67, x(page, "T51"), 0.5);
	}

	/** A card longer than the maximum of the box is cut over the columns. */
	public void testAvoidBlockLongerThanMaxHeight() throws Exception {
		final String page = convert("300pt 400pt", "writing-mode: horizontal-tb", "<div style='max-height: 50pt; column-count: 3; column-gap: 17pt'>"
				+ card(100, 160) + "</div><p>AFTER</p>");
		assertTrue("the third column: " + page, x(page, "T159") > 211);
		assertTrue("above what follows", y(page, "T159") < 50);
	}

	/** An avoid block longer than the page is cut over the columns, in a cell and in an inline block as well. */
	public void testAvoidBlockLongerThanThePage() throws Exception {
		final String content = multicol(card(100, 1100));
		for (final String[] host : new String[][] { { "<table style='width: 100%; border-spacing: 0'><tr><td style='padding: 0'>",
				"</td></tr></table>" }, { "<div style='display: inline-block; width: 300pt'>", "</div>" } }) {
			final List<String> pages = convertPages("300pt 400pt", "writing-mode: horizontal-tb", host[0] + content + host[1]);
			final String first = pages.get(0);
			assertTrue(host[0] + " three columns", x(first, "T100") < 100 && Pattern.compile("x=2[0-9][0-9][.0-9]* y=[-0-9.]+ Text\\[\"T")
					.matcher(first).find());
		}
	}

	private static String columns(final String content) throws Exception {
		return convert("300pt 400pt", "writing-mode: horizontal-tb", multicol(content) + "<p>AFTER</p>");
	}

	private static String multicol(final String content) {
		return "<div style='column-count: 3; column-gap: 17pt'>" + content + "</div>";
	}

	private static String card(final int from, final int to) {
		return "<div style='break-inside: avoid'>" + p(from, to) + "</div>";
	}

	private static String p(final int from, final int to) {
		final StringBuilder words = new StringBuilder("<p>");
		for (int i = from; i < to; ++i) {
			words.append('T').append(i).append(' ');
		}
		return words.append("</p>").toString();
	}

	private static String block(final int width, final int height) {
		return img(width, height, "display: block; ");
	}

	private static String img(final int width, final int height, final String style) {
		return "<img src='" + GIF + "' alt='' style='" + style + "width: " + width + "pt; height: " + height + "pt'>";
	}

	private static void assertImage(final String page, final double x, final double y, final int width, final int height) {
		final Matcher m = image(page, width, height);
		assertEquals(page, x, Double.parseDouble(m.group(1)), 0.5);
		assertEquals(page, y, Double.parseDouble(m.group(2)), 0.5);
	}

	/** The only drawing of the image, not clipped. */
	private static Matcher image(final String page, final int width, final int height) {
		final Matcher m = Pattern.compile(
				"x=([-0-9.]+) y=([-0-9.]+) (?:artifact )?AbsoluteRectFrame\\[w=" + width + "\\.00 h=" + height + "\\.00\\](.*)").matcher(page);
		assertTrue("the image: " + page, m.find());
		assertFalse("not sliced: " + page, m.group(3).contains("clip"));
		assertFalse("drawn once: " + page, m.find());
		m.reset();
		m.find();
		return m;
	}

	private static double x(final String page, final String token) {
		return Double.parseDouble(text(page, token).group(1));
	}

	private static double y(final String page, final String token) {
		return Double.parseDouble(text(page, token).group(2));
	}

	private static Matcher text(final String page, final String token) {
		final Matcher m = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"" + token + "\"").matcher(page);
		assertTrue(token + ": " + page, m.find());
		return m;
	}

	private static String convert(final String size, final String mode, final String body) throws Exception {
		final List<String> pages = convertPages(size, mode, body);
		assertEquals("pages", 1, pages.size());
		return pages.get(0);
	}

	private static List<String> convertPages(final String size, final String mode, final String body) throws Exception {
		final File dir = Files.createTempDirectory("multicol-unbreakable").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: " + size
				+ "; margin: 0 } body { margin: 0; font: 6pt/1.2 serif; " + mode + " } p { margin: 0 }</style></head><body>"
				+ body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] dumps = dir.listFiles((d, name) -> name.startsWith("page-") && name.endsWith(".txt"));
		Arrays.sort(dumps);
		final List<String> pages = new ArrayList<>();
		for (final File dump : dumps) {
			pages.add(Files.readString(dump.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
