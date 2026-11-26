package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * SVG text under a filter, which Batik draws to a raster (2026-11-26; the vertical newspaper pages of a local film
 * project, under an ink filter). The page is checked as PDFBox draws it: the filtered text is an image, and there is no
 * other text.
 */
public class SvgVerticalUnderFilterTest extends TestCase {
	/** The page of an SVG 200pt square, a unit a point, with the body in a group under the filter. */
	private static BufferedImage render(final String filter, final String groupAttributes, final String body,
			final float dpi) throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset="UTF-8"/>
				<style>@page{size:200pt 200pt;margin:0} body{margin:0}</style></head><body>
				<svg xmlns="http://www.w3.org/2000/svg" width="200pt" height="200pt" viewBox="0 0 200 200"
				 style="display:block"><filter id="f" filterUnits="userSpaceOnUse" x="0" y="0" width="200" height="200">
				%s</filter><g filter="url(#f)" font-family="serif" %s>%s</g></svg>
				</body></html>
				""".formatted(filter, groupAttributes, body);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///svg-text-under-filter.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			// the filters here are drawn to a raster (Batik draws an offset's source as it is, in vectors)
			assertEquals("no text but the filter's image", "", new PDFTextStripper().getText(doc).strip());
			return new PDFRenderer(doc).renderImageWithDPI(0, dpi);
		}
	}

	/** The ink's bounds (x0, y0, x1, y1) in the columns from x0 to x1. */
	private static int[] ink(final BufferedImage image, final int fromX, final int toX) {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
		for (int y = 0; y < image.getHeight(); ++y) {
			for (int x = fromX; x < toX; ++x) {
				if (grey(image.getRGB(x, y)) < 128) {
					x0 = Math.min(x0, x);
					y0 = Math.min(y0, y);
					x1 = Math.max(x1, x);
					y1 = Math.max(y1, y);
				}
			}
		}
		return new int[] { x0, y0, x1, y1 };
	}

	private static int grey(final int rgb) {
		return (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
	}

	/**
	 * 一 (a horizontal stroke) stays upright in its column, centred on x; ー, which a vertical font turns, is turned a
	 * quarter and stands as a vertical stroke. A CID-keyed font is drawn to a raster with its AWT substitute (pdfg2d
	 * PDFFontUtils.drawAwtFont), where a vertical line was turned sideways like a horizontal font's, so the glyphs lay on
	 * their side.
	 */
	public void testUprightGlyphsUnderFilter() throws Exception {
		final BufferedImage image = render("<feGaussianBlur stdDeviation=\"0.2\"/>",
				"font-size=\"60\" style=\"writing-mode:vertical-rl;text-orientation:upright\"",
				"<text x=\"150\" y=\"20\">一</text><text x=\"50\" y=\"20\">ー</text>", 72);
		final int[] one = ink(image, 100, 200);
		assertTrue("一 " + java.util.Arrays.toString(one), one[2] > one[0]);
		final int w = one[2] - one[0], h = one[3] - one[1];
		assertTrue("一 lies across its column: " + w + " x " + h, w > 3 * Math.max(h, 1));
		assertEquals("一 is centred on its column", 150, (one[0] + one[2]) / 2.0, 6);
		assertTrue("一 is in the first em below y 20: " + one[1] + "-" + one[3], one[1] > 20 && one[3] < 80);
		final int[] bar = ink(image, 0, 100);
		assertTrue("ー " + java.util.Arrays.toString(bar), bar[2] > bar[0]);
		assertTrue("ー stands along its column: " + java.util.Arrays.toString(bar),
				bar[3] - bar[1] > 3 * (bar[2] - bar[0]));
		assertEquals("ー is centred on its column", 50, (bar[0] + bar[2]) / 2.0, 6);
	}

	/**
	 * A fullwidth full stop (．, without a Unicode vertical form) moves to the upper right of its em box, where a vertical
	 * font has it (codex 2026-11-26: only 、。， were moved).
	 */
	public void testFullStopToTheUpperRight() throws Exception {
		final BufferedImage image = render("<feGaussianBlur stdDeviation=\"0.2\"/>",
				"font-size=\"60\" style=\"writing-mode:vertical-rl;text-orientation:upright\"",
				"<text x=\"100\" y=\"20\">．</text>", 72);
		final int[] dot = ink(image, 0, 200);
		assertTrue("．" + java.util.Arrays.toString(dot), dot[2] >= dot[0]);
		assertTrue("right of the column's centre: " + java.util.Arrays.toString(dot), (dot[0] + dot[2]) / 2.0 > 100);
		assertTrue("in the upper half of the em: " + java.util.Arrays.toString(dot), (dot[1] + dot[3]) / 2.0 < 50);
	}

	/** 一一一 down a column: the strokes one em (60) apart, as a vertical font advances. */
	public void testUprightAdvanceUnderFilter() throws Exception {
		final BufferedImage image = render("<feGaussianBlur stdDeviation=\"0.2\"/>",
				"font-size=\"60\" style=\"writing-mode:vertical-rl;text-orientation:upright\"",
				"<text x=\"100\" y=\"10\">一一一</text>", 72);
		final java.util.List<Double> centres = new java.util.ArrayList<>();
		int from = -1;
		for (int y = 0; y <= image.getHeight(); ++y) {
			boolean ink = false;
			for (int x = 70; y < image.getHeight() && x < 130 && !ink; ++x) {
				ink = grey(image.getRGB(x, y)) < 128;
			}
			if (ink && from < 0) {
				from = y;
			} else if (!ink && from >= 0) {
				centres.add((from + y - 1) / 2.0);
				from = -1;
			}
		}
		assertEquals("three strokes " + centres, 3, centres.size());
		assertEquals("one em apart " + centres, 60, centres.get(1) - centres.get(0), 3);
		assertEquals("one em apart " + centres, 60, centres.get(2) - centres.get(1), 3);
	}

	/**
	 * Bold under a filter is made as the PDF makes it (the outline widened by a stroke), in the text's own colour
	 * (codex 2026-11-26: the stroke took the GC's stroke paint, black, not the fill's red).
	 */
	public void testBoldUnderFilter() throws Exception {
		final BufferedImage image = render("<feGaussianBlur stdDeviation=\"0.2\"/>",
				"font-size=\"40\" fill=\"red\"",
				"<text x=\"10\" y=\"60\">三王</text><text x=\"110\" y=\"60\" font-weight=\"bold\">三王</text>", 72);
		int regular = 0, bold = 0, black = 0;
		for (int y = 0; y < image.getHeight(); ++y) {
			for (int x = 0; x < image.getWidth(); ++x) {
				final int rgb = image.getRGB(x, y), r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (r < 128 && g < 128 && b < 128) {
					++black;
				} else if (r > 160 && g < 128 && b < 128) {
					if (x < 100) {
						++regular;
					} else {
						++bold;
					}
				}
			}
		}
		assertEquals("no black ink", 0, black);
		assertTrue("ink " + regular, regular > 100);
		assertTrue("bold " + bold + " against regular " + regular, bold > regular * 1.25);
	}

	/**
	 * Small text under a filter is antialiased, as SVG's shape-rendering auto is in a browser (pdfg2d SVGImage sets the
	 * hint). Batik draws the filter's source with the hints of the Graphics2D, and without them a 5pt Mincho's thin
	 * horizontals, under a device pixel at 300 dpi, broke up. Counted on the page drawn at the filter resolution (300
	 * dpi, the filter's pixels one to one): the edge pixels between ink and paper are grey, not only black and white.
	 */
	public void testSmallTextUnderFilterIsAntialiased() throws Exception {
		final BufferedImage image = render(
				"<feColorMatrix type=\"matrix\" values=\"1 0 0 0 0 0 1 0 0 0 0 0 1 0 0 0 0 0 1 0\"/>",
				"font-size=\"5\"", "<text x=\"10\" y=\"20\">救出しない許可をとった生徒約千人の新聞記事</text>", 300);
		int dark = 0, mid = 0;
		for (int y = 0; y < image.getHeight(); ++y) {
			for (int x = 0; x < image.getWidth(); ++x) {
				final int g = grey(image.getRGB(x, y));
				if (g < 64) {
					++dark;
				} else if (g < 192) {
					++mid;
				}
			}
		}
		assertTrue("ink " + dark, dark > 500);
		assertTrue("grey edge pixels " + mid + " for " + dark + " dark ones", mid > dark / 2);
	}
}
