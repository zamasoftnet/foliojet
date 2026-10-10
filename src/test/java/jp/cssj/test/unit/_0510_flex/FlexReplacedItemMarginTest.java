package jp.cssj.test.unit._0510_flex;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A replaced flex item takes its margins, and its border and padding for content-box, beside it (2026-10-09). Its
 * wrapper item took over only the element's width, so the frame went beyond the wrapper: the text after an svg icon
 * with margin-right sat on the margin, and a button sized to its content (a grid item, as on smolcss.dev) came out
 * narrower by the margin. Chrome lays out every row of a group alike (tmp/ab/repro/buttonws/flexsvg3.html: 51.86pt
 * wide with the text 21.75pt in, and 26.74pt in for the 200pt rows; chip.html for the negative margin). The wrapper is
 * now the element's border box with its margins, sized by the flex algorithm, and the element fills it (2026-10-10,
 * codex's review cases in {@link #testReviewCases}).
 */
public class FlexReplacedItemMarginTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"Open\"");

	private static final Pattern FRAME = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+)");

	private static final String SVG = "viewBox=\"0 0 32 32\"><rect width=\"32\" height=\"32\"/></svg>";

	private static final String IMG = "src=\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' "
			+ "height='32'%3E%3Crect width='32' height='32'/%3E%3C/svg%3E\">";

	/** Items 15pt wide with 6pt beside them, in a button sized to its content. */
	private static final String[] ROWS = { "<span class=\"i\" style=\"display: block; margin-right: 6pt\"></span>Open",
			"<svg class=\"i\" style=\"margin-right: 6pt\" " + SVG + "Open",
			"<img class=\"i\" style=\"margin-right: 6pt\" " + IMG + "Open",
			"<svg class=\"i\" style=\"margin-left: 6pt\" " + SVG + "Open",
			"<svg class=\"i\" style=\"padding-right: 3pt; margin-right: 3pt\" " + SVG + "Open",
			"<svg class=\"i\" style=\"box-sizing: border-box; width: 18pt; padding-right: 3pt; margin-right: 3pt\" " + SVG
					+ "Open" };

	public void testInAFlexRow() throws Exception {
		this.sameAsFirst("<div class=\"o\" style=\"display: flex; align-items: flex-start\"><div class=\"b\">", "</div></div>");
	}

	public void testInAGrid() throws Exception {
		this.sameAsFirst("<div class=\"o\" style=\"display: grid; justify-content: start\"><div class=\"b\">", "</div></div>");
	}

	/** A percentage width resolves against the container, and the margin comes beside it. */
	public void testPercentageWidth() throws Exception {
		final String box = "<div class=\"b\" style=\"width: 200pt\">";
		final double[] block = textAndFrame(convert(box
				+ "<span style=\"display: block; width: 10%; height: 15pt; margin-right: 6pt\"></span>Open</div>"));
		final double[] svg = textAndFrame(
				convert(box + "<svg style=\"width: 10%; height: 15pt; margin-right: 6pt\" " + SVG + "Open</div>"));
		assertEquals("text", block[0], svg[0], 0.01);
		assertEquals("text 1pt + 20pt + 6pt in", 27, svg[0] - svg[1], 0.01);
	}

	/**
	 * A negative margin takes room off, in the measure too (Material UI's chip: margin-left 2px, margin-right -4px). The
	 * size alone counted, the chip came out wider than its content and its centered row moved by half the difference.
	 * Chrome: 111.66pt wide, the text 23.25pt in.
	 */
	public void testNegativeMargin() throws Exception {
		final String open = "<div style=\"display: flex; align-items: flex-start\"><a class=\"c\" style=\"display: flex; "
				+ "align-items: center; justify-content: center; padding: 0 6pt; border: 0.75pt solid black; gap: 4.5pt\">";
		final String item = "width: 13.5pt; height: 13.5pt; margin-left: 1.5pt; margin-right: -3pt; background: red\"";
		final double[] block = textAndFrame(
				convert(open + "<span style=\"display: block; " + item + "></span><span>Open</span></a></div>"));
		final double[] svg = textAndFrame(convert(open + "<svg style=\"" + item + " " + SVG + "<span>Open</span></a></div>"));
		assertEquals("text 0.75pt + 6pt + 12pt + 4.5pt in", 23.25, block[0] - block[1], 0.01);
		assertEquals("text", block[0], svg[0], 0.01);
		assertEquals("width", block[2], svg[2], 0.01);
	}

	/**
	 * codex's review cases (2026-10-10): an image of 120 x 60pt natural size in a 200pt row (column for H), its border box
	 * and the next item measured in Chrome (tmp/ab/repro/buttonws/qa/, chrome.py). Each is checked on its own, so an
	 * image shrunk by mistake does not pass with the next item in the right place.
	 */
	public void testReviewCases() throws Exception {
		Boxes b = qa("#i{width:20pt;margin-left:10%}");
		b.image("A, a percentage margin", 20, 20, 10);
		b.next("A", 40, 0);
		b = qa("#i{width:20pt;padding-left:10%}");
		b.image("A2, a percentage padding", 0, 40, 10);
		b.content("A2", 20, 20);
		b.next("A2", 40, 0);
		b = qa("#i{width:50%;flex:0 0 60pt;min-width:0;margin-right:20pt}");
		b.image("B, a flex-basis", 0, 60, 30);
		b.next("B", 80, 0);
		b = qa("#f{width:100pt} #i{width:50%;flex:0 1 auto;min-width:0;margin-right:20pt} "
				+ "#n{width:50pt;flex:0 1 auto;min-width:0}");
		b.image("C, shrunk in proportion to its size without the margin", 0, 40, 20);
		b.next("C", 60, 0);
		b = qa("#i{width:50%;min-width:80%;margin-right:10pt}");
		b.image("D, a percentage min-width", 0, 160, 80);
		b.next("D", 170, 0);
		b = qa("#i{box-sizing:border-box;width:20pt;min-width:40pt;padding:5pt;margin-right:10pt}");
		b.image("E, a border-box min-width", 0, 40, 25);
		b.content("E", 5, 30);
		b.next("E", 50, 0);
		b = qa("#i{width:auto;max-width:50%;margin-right:20pt}");
		b.image("F, an auto width under a percentage max-width", 0, 100, 50);
		b.next("F", 120, 0);
		// #n taller than the image, as Copper paints a replaced element over a later block's background
		b = qa("#i{width:auto;max-width:50%;margin-right:-20pt} #n{height:60pt}");
		b.image("F2, the same with a negative margin", 0, 100, 50);
		assertEquals("F2: the next item's x (its top lies under the image)", 80, b.next[0], 0.5);
		b = qa("#i{width:10%;min-width:0;margin-right:-30pt}");
		b.image("G, a margin larger than the image the other way", 0, 20, 10);
		b.next("G", -10, 0);
		b = qa("#f{flex-direction:column;height:100pt} "
				+ "#i{width:20pt;height:80pt;min-height:80pt;flex:0 1 auto;margin-bottom:10pt} #n{height:50pt}");
		b.image("H, a min-height in a column", 0, 20, 80);
		b.next("H", 0, 90);
		b = qa("#f{width:440pt} #i{width:500pt;flex:1 1 0;max-width:35%;margin-right:18pt} #n{flex:0 0 50pt}");
		b.image("M, a percentage max-width is of the row (Mozilla's blog)", 0, 154, 77);
		b.next("M", 172, 0);
	}

	/**
	 * The measure of a replaced element outside a flex counts its used size with its frame (codex J): width 100pt under
	 * max-width 50pt with margin-left 20pt makes an absolutely positioned box or a table cell 70pt wide, as in Chrome.
	 * A 1pt border stands for the outline, which a table cell does not draw.
	 */
	/**
	 * The automatic minimum of a replaced item is its content size suggestion (2026-10-10, CSS Flexbox §4.5): the height
	 * through the ratio when the height is absolute, else the natural width. In a 100pt row Chrome shrinks a 200 x 20pt
	 * image to 90pt beside a 10pt item, keeps a 200pt image with height auto at its natural 120pt, and gives a width:
	 * auto image 40pt from its height (tmp/ab/repro/buttonws/qa2/, M1-M3). It stayed 200pt wide.
	 */
	public void testAutomaticMinimum() throws Exception {
		Boxes b = qa("#f{width:100pt} #i{width:200pt;height:20pt;flex:0 1 auto}");
		b.image("M1, an absolute height", 0, 90, 20);
		b.next("M1", 90, 0);
		b = qa("#f{width:100pt} #i{width:200pt;height:auto;flex:0 1 auto}");
		b.image("M2, the natural width", 0, 120, 60);
		b.next("M2", 120, 0);
		b = qa("#f{width:100pt} #i{width:auto;height:20pt;flex:0 1 auto}");
		b.image("M3, an auto width", 0, 40, 20);
		b.next("M3", 40, 0);
	}

	/**
	 * A percentage height goes through the ratio as the container resolves it, and an SVG with only a viewBox has no
	 * natural width for the automatic minimum (2026-10-10, 3c's A/B: frontiers-art's logo, viewBox 2811 wide and
	 * height: 100%, was drawn 2108pt wide; katex's, height: 80%, 67.88 for Chrome's 60.35).
	 */
	public void testAutomaticMinimumWithPercentages() throws Exception {
		Boxes b = qaHtml(ratioOnly(String.format(QA, "#f{height:20pt} #i{width:auto;height:100%;flex:0 1 auto}")));
		b.image("R1, a viewBox only, height: 100%", 0, 80, 20);
		b.next("R1", 80, 0);
		b = qa("#f{height:40pt} #i{width:auto;height:50%;flex:0 1 auto}");
		b.image("R2, a natural size, height: 50%", 0, 40, 20);
		b.next("R2", 40, 0);
		b = qaHtml(ratioOnly(String.format(QA,
				"#f{height:40pt} #i{width:auto;height:50%;flex:0 1 auto;margin-right:10pt}")));
		b.image("R3, a viewBox only with a margin", 0, 80, 20);
		b.next("R3", 90, 0);
	}

	/**
	 * The suggestion keeps to the limits of the other axis through the ratio and to its own max, and a specified
	 * aspect-ratio of a border-box image is of its border box (2026-10-10, codex's review, qa2/X1-X4 in Chrome).
	 */
	public void testAutomaticMinimumWithLimits() throws Exception {
		Boxes b = qa("#f{width:100pt} #i{width:auto;flex:0 1 auto;max-height:20pt}");
		b.image("X1, max-height", 0, 40, 20);
		b.next("X1", 40, 0);
		b = qa("#f{width:100pt} #i{width:auto;flex:0 1 auto;height:20pt;min-height:80pt}");
		b.image("X2, min-height", 0, 160, 80);
		b.next("X2", 160, 0);
		b = qa("#f{width:100pt} #i{width:auto;flex:0 1 auto;height:60pt;aspect-ratio:3;box-sizing:border-box;"
				+ "padding:10pt}");
		b.image("X3, aspect-ratio of a border box", 0, 180, 60);
		b.next("X3", 180, 0);
		b = qa("#f{width:100pt} #i{width:200pt;flex:0 1 auto;height:20pt;max-width:150pt;min-height:30pt}");
		b.image("X4, shrinks to the line", 0, 90, 30);
		b.next("X4", 90, 0);
		b = qa("#f{width:100pt;height:40pt} #i{width:auto;flex:0 1 auto;max-height:50%}");
		b.image("Z1, a percentage max-height", 0, 40, 20);
		b.next("Z1", 40, 0);
	}

	/** The QA image as an SVG with only a viewBox of 160 x 40. */
	private static String ratioOnly(final String html) {
		final String sized = "width='160' height='80'%3E%3Crect width='160' height='80'";
		assertTrue(html.contains(sized));
		return html.replace(sized, "viewBox='0 0 160 40'%3E%3Crect width='160' height='40'");
	}

	/**
	 * The sizes the flex algorithm gives a replaced item reach the element (2026-10-10, tmp/ab/repro/buttonws/qa2/ and
	 * qa/K, Chrome): a stretched row makes an image of height auto as high as the line (S1, 40 x 100), flex: 1 in a
	 * column gives it the free height (S2, 40 x 90), and a stretched column makes an image of width auto as wide as the
	 * column, its height following the ratio (K, 200 x 100; with height 30pt, 200 x 30), within its own max-width and
	 * margins, not with an auto margin (K3-K7).
	 */
	public void testStretchAndColumnMain() throws Exception {
		Boxes b = qa("#f{align-items:stretch;height:100pt} #i{width:40pt;height:auto}");
		b.image("S1, a stretched row", 0, 40, 100);
		b.next("S1", 40, 0);
		b = qa("#f{flex-direction:column;height:100pt} #i{width:40pt;height:auto;flex:1 1 0;min-height:0}");
		b.image("S2, flex: 1 in a column", 0, 40, 90);
		b.next("S2", 0, 90);
		b = qa("#f{flex-direction:column;align-items:stretch} #i{width:auto}");
		b.image("K, a stretched column", 0, 200, 100);
		b.next("K", 0, 100);
		b = qa("#f{flex-direction:column;align-items:stretch} #i{width:auto;height:30pt}");
		b.image("K2, the same with a height", 0, 200, 30);
		b.next("K2", 0, 30);
		// Its own limits, margins and box (a column with an auto height lays its items out in normal flow)
		b = qa("#f{flex-direction:column;align-items:stretch} #i{width:auto;max-width:150pt}");
		b.image("K3, max-width", 0, 150, 75);
		b.next("K3", 0, 75);
		b = qa("#f{flex-direction:column;align-items:stretch} #i{width:auto;margin:0 20pt}");
		b.image("K4, margins", 20, 160, 80);
		b.next("K4", 0, 80);
		b = qa("#f{flex-direction:column;align-items:stretch} #i{width:auto;margin-left:auto}");
		b.image("K5, an auto margin is not stretched", 80, 120, 60);
		b.next("K5", 0, 60);
		b = qa("#f{flex-direction:column;align-items:stretch} #i{width:auto;padding:0 10pt;border:3pt solid #00ff00}");
		b.image("K6, padding and border", 0, 200, 93);
		b.content("K6", 13, 174);
		b.next("K6", 0, 93);
		b = qa("#f{flex-direction:column;align-items:stretch} #i{width:auto;max-width:50%}");
		b.image("K7, max-width: 50%", 0, 100, 50);
		b.next("K7", 0, 50);
		// The stretched height keeps to percentage limits of the container's height (codex's review)
		b = qa("#f{align-items:stretch;height:100pt} #i{width:40pt;height:auto;max-height:50%}");
		b.image("Y4, a stretched row with max-height: 50%", 0, 40, 50);
		b.next("Y4", 40, 0);
		b = qa("#f{align-items:stretch;height:100pt} #i{width:40pt;height:auto;min-height:120%}");
		b.image("Y6, a stretched row with min-height: 120%", 0, 40, 120);
		b.next("Y6", 40, 0);
		b = qa("body{height:100pt} #f{align-items:stretch;height:100%} #i{width:100pt;height:auto;max-height:50%}");
		b.image("Z2, max-height: 50% in a container of height: 100%", 0, 100, 50);
		b.next("Z2", 100, 0);
	}

	/**
	 * Percentages of a replaced item's height in a column are of the container, as the wrapper takes them over
	 * (2026-10-10, qa2/C1, C2: Chrome 90 and 30 in a 100pt column), and a border-box container's percentage padding
	 * comes off the basis of the image's percentage height (C3: 60 x 30 at y 20).
	 */
	public void testColumnPercentages() throws Exception {
		Boxes b = qa("#f{flex-direction:column;height:100pt} #i{width:40pt;height:80pt;min-height:90%;flex:0 1 auto}");
		b.image("C1, min-height: 90%", 0, 40, 90);
		b.next("C1", 0, 90);
		b = qa("#f{flex-direction:column;height:100pt} #i{width:40pt;height:80pt;max-height:30%}");
		b.image("C2, max-height: 30%", 0, 40, 30);
		b.next("C2", 0, 30);
		b = qa("body{width:200pt} #f{box-sizing:border-box;height:100pt;padding:10% 0} #i{width:auto;height:50%}");
		b.image("C3, the padding of a border-box container", 0, 60, 30);
		b.next("C3", 60, 20);
		// A percentage padding taken over in the height is of the container's width (codex's review)
		b = qa("#f{flex-direction:column;height:100pt} #i{width:40pt;height:40pt;min-height:0;padding-top:10%}");
		b.image("Y5, padding-top: 10% in a column", 0, 40, 60);
		b.next("Y5", 0, 60);
	}

	public void testMeasureUsesTheUsedSize() throws Exception {
		for (final String f : new String[] { "#f{display:block;position:absolute;width:auto}",
				"#f{display:table-cell;width:auto}", "#f{display:inline-block;width:auto}" }) {
			final Boxes b = qa(f + " #f{outline:none;border:1pt solid black} "
					+ "#i{width:100pt;max-width:50pt;margin-left:20pt} #n{display:none}");
			b.image(f, 21, 50, 25);
			assertNotNull(f + ": no border", b.outline);
			assertEquals(f + ": the box's width", 70, b.outline[2] - 2, 0.5);
		}
	}

	/**
	 * The width and height attributes of an inline svg are hints below the author's CSS (2026-10-10): with width="16"
	 * and CSS 30px, or 1em at 18px, Chrome draws the svg 22.5pt or 13.5pt wide (svgpx.html, svgem.html). They won over
	 * the CSS, which made Material UI's chip icon 1.5pt too small once its margins counted.
	 */
	public void testSvgSizeAttributesAreHints() throws Exception {
		final Pattern frame = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");
		for (final String[] c : new String[][] { { "width: 30px; height: 30px", "22.5" },
				{ "width: 1em; height: 1em; font-size: 18px", "13.5" },
				// A declared width: auto counts: the width follows the height through the ratio (2026-10-10)
				{ "width: auto; height: 30px", "22.5" } }) {
			for (final String display : new String[] { "block", "flex" }) {
				final String page = convert("<div style=\"display: " + display + "\"><svg width=\"16\" height=\"16\" style=\""
						+ c[0] + "; background: red\" " + SVG + "</div>");
				final Matcher f = frame.matcher(page);
				assertTrue(page, f.find());
				assertEquals(c[0] + " in " + display + ": width\n" + page, Double.parseDouble(c[1]),
						Double.parseDouble(f.group(1)), 0.01);
				assertEquals(c[0] + " in " + display + ": height", Double.parseDouble(c[1]), Double.parseDouble(f.group(2)),
						0.01);
			}
		}
		// A declared height: auto keeps the attribute's height when the attributes give no ratio (2026-10-11,
		// Bootstrap's placeholder, without a viewBox: 250px high in Chrome)
		final String page = convert("<div style=\"width: 300pt\"><svg width=\"100%\" height=\"250\" style=\"max-width: 100%;"
				+ " height: auto; background: red\"><rect width=\"32\" height=\"32\"/></svg></div>");
		final Matcher f = frame.matcher(page);
		assertTrue(page, f.find());
		assertEquals("width=\"100%\" height=\"250\": width\n" + page, 300, Double.parseDouble(f.group(1)), 0.01);
		assertEquals("width=\"100%\" height=\"250\": height", 187.5, Double.parseDouble(f.group(2)), 0.01);
		// An inherited width is the parent's, here from its inline-size (Chrome 22.5 x 60; codex, 2026-10-11)
		final String inherited = convert("<div style=\"inline-size: 30px\"><svg width=\"160\" height=\"80\""
				+ " style=\"width: inherit; background: red\"><rect width=\"32\" height=\"32\"/></svg></div>");
		final Matcher g = frame.matcher(inherited);
		assertTrue(inherited, g.find());
		assertEquals("width: inherit\n" + inherited, 22.5, Double.parseDouble(g.group(1)), 0.01);
		assertEquals("width: inherit: height", 60, Double.parseDouble(g.group(2)), 0.01);
	}

	private static final String QA ="<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page{size:600pt 400pt;margin:20pt} body{margin:0} #f{display:flex;width:200pt;align-items:flex-start;"
			+ "outline:1pt solid black;background:#eeeeee} #i{display:block;flex:none;width:40pt;height:auto;"
			+ "background:#00ff00} #n{flex:none;width:10pt;height:10pt;background:blue} %s</style></head><body>"
			+ "<div id=\"f\"><img id=\"i\" src=\"data:image/svg+xml,%%3Csvg xmlns='http://www.w3.org/2000/svg' "
			+ "width='160' height='80'%%3E%%3Crect width='160' height='80' fill='red'/%%3E%%3C/svg%%3E\">"
			+ "<div id=\"n\"></div></div></body></html>";

	/** Boxes in pt from the top left of #f (the page margin), read from the PDF rendered 4 pixels to the point. */
	private static final class Boxes {
		double[] border, content, next, outline;

		void image(final String label, final double x, final double w, final double h) {
			assertEquals(label + ": the image's x", x, this.border[0], 0.5);
			assertEquals(label + ": the image's width", w, this.border[2], 0.5);
			assertEquals(label + ": the image's height", h, this.border[3], 0.5);
		}

		void content(final String label, final double x, final double w) {
			assertEquals(label + ": the content's x", x, this.content[0], 0.5);
			assertEquals(label + ": the content's width", w, this.content[2], 0.5);
		}

		void next(final String label, final double x, final double y) {
			assertNotNull(label + ": no next item", this.next);
			assertEquals(label + ": the next item's x", x, this.next[0], 0.5);
			assertEquals(label + ": the next item's y", y, this.next[1], 0.5);
		}
	}

	private static Boxes qa(final String css) throws Exception {
		return qaHtml(String.format(QA, css));
	}

	private static Boxes qaHtml(final String html) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final File dir = Files.createTempDirectory("flex-replaced-qa").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null)) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
			final BufferedImage img = new PDFRenderer(pdf).renderImageWithDPI(0, 288);
			final Boxes b = new Boxes();
			final double[] red = bbox(img, 255, 0, 0), green = bbox(img, 0, 255, 0);
			b.content = red;
			b.border = green == null ? red : red == null ? green : union(red, green);
			b.next = bbox(img, 0, 0, 255);
			b.outline = bbox(img, 0, 0, 0);
			assertNotNull(html + ": no image", b.border);
			return b;
		}
	}

	private static double[] union(final double[] a, final double[] b) {
		final double x = Math.min(a[0], b[0]), y = Math.min(a[1], b[1]);
		return new double[] { x, y, Math.max(a[0] + a[2], b[0] + b[2]) - x, Math.max(a[1] + a[3], b[1] + b[3]) - y };
	}

	/** {x, y, width, height} in pt of the pixels of a color, from the page margin; null if none. */
	private static double[] bbox(final BufferedImage img, final int r, final int g, final int b) {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
		for (int y = 0; y < img.getHeight(); ++y) {
			for (int x = 0; x < img.getWidth(); ++x) {
				final int rgb = img.getRGB(x, y);
				if (Math.abs(((rgb >> 16) & 0xFF) - r) < 40 && Math.abs(((rgb >> 8) & 0xFF) - g) < 40
						&& Math.abs((rgb & 0xFF) - b) < 40) {
					x0 = Math.min(x0, x);
					y0 = Math.min(y0, y);
					x1 = Math.max(x1, x);
					y1 = Math.max(y1, y);
				}
			}
		}
		if (x1 < 0) {
			return null;
		}
		return new double[] { x0 / 4.0 - 20, y0 / 4.0 - 20, (x1 - x0 + 1) / 4.0, (y1 - y0 + 1) / 4.0 };
	}

	private void sameAsFirst(final String open, final String close) throws Exception {
		final double[] first = textAndFrame(convert(open + ROWS[0] + close));
		assertEquals("text 1pt + 15pt + 6pt in", 22, first[0] - first[1], 0.01);
		for (int i = 1; i < ROWS.length; ++i) {
			final double[] row = textAndFrame(convert(open + ROWS[i] + close));
			assertEquals("row " + i + " text", first[0], row[0], 0.01);
			assertEquals("row " + i + " width", first[2], row[2], 0.01);
		}
	}

	/** {text x, button x, button width}: the button is the first frame drawn. */
	private static double[] textAndFrame(final String page) {
		final Matcher t = TEXT.matcher(page);
		assertTrue(page, t.find());
		final Matcher f = FRAME.matcher(page);
		assertTrue(page, f.find());
		return new double[] { Double.parseDouble(t.group(1)), Double.parseDouble(f.group(1)),
				Double.parseDouble(f.group(3)) };
	}

	private static String convert(final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 400pt 400pt; margin: 20pt } body { margin: 0; font: 12pt/1.5 sans-serif }"
				+ " .b { display: flex; align-items: center; border: 1pt solid black; background: #ddd }"
				+ " .i { width: 15pt; height: 15pt; background: red }</style></head><body>" + body + "</body></html>";
		final File dir = Files.createTempDirectory("flex-replaced-margin").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
	}
}
