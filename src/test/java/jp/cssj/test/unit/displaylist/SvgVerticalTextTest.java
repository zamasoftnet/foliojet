package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Vertical SVG text (2026-11-26, the vertical newspaper pages of a local film project; copperpdf4
 * docs/history/2026-11-25-realweb-test.md section 10):
 * <ul>
 * <li>{@code writing-mode: vertical-rl} (CSS Writing Modes 3) in a {@code <style>} made Batik drop the whole sheet;
 * SvgCompat writes SVG 1.1's {@code tb-rl}.</li>
 * <li>The column stood 0.6em left of x and the text began 1em above y; Chrome centres the column on x and begins at y.
 * </li>
 * <li>{@code text-orientation: upright} (as {@code glyph-orientation-vertical: 0}) left Latin letters on their side.
 * </li>
 * </ul>
 * Positions are measured on the page rendered at 72 dpi (the PDF text positions of vertical CID fonts are not where
 * the glyphs are drawn). The SVG is 200 units wide, painted at 150pt (0.75pt a unit).
 */
public class SvgVerticalTextTest extends TestCase {
	/** The document with the SVG converted to PDF. */
	private static byte[] pdf(final String svgBody) throws Exception {
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<style>@page{margin:0} body{margin:0}</style></head><body>
				<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200"
				 font-family="serif"><style>.v{writing-mode:vertical-rl} .u{text-orientation:upright}</style>%s</svg>
				</body></html>
				""".formatted(svgBody);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///svg-vertical-text.html"), "text/html", null);
		} finally {
			session.close();
		}
		return out.toByteArray();
	}

	/** The ink's box {x0, y0, x1, y1} in pt on the rendered page, or null. */
	private static int[] ink(final String svgBody) throws Exception {
		try (PDDocument doc = Loader.loadPDF(pdf(svgBody))) {
			final BufferedImage image = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
			for (int y = 0; y < image.getHeight(); ++y) {
				for (int x = 0; x < image.getWidth(); ++x) {
					final int rgb = image.getRGB(x, y);
					final int grey = ((rgb >> 16 & 0xff) + (rgb >> 8 & 0xff) + (rgb & 0xff)) / 3;
					if (grey < 128) {
						x0 = Math.min(x0, x);
						y0 = Math.min(y0, y);
						x1 = Math.max(x1, x);
						y1 = Math.max(y1, y);
					}
				}
			}
			return x1 < 0 ? null : new int[] { x0, y0, x1, y1 };
		}
	}

	/** The column centred on x = 100 (75pt), the glyphs from y = 20 (15pt) down; the em is 40 units (30pt). */
	public void testColumnCentredOnXFromY() throws Exception {
		final int[] box = ink("<text class='v' x='100' y='20' font-size='40'>国国</text>");
		assertNotNull("no ink", box);
		final double centre = (box[0] + box[2]) / 2.0;
		assertEquals("centre " + java.util.Arrays.toString(box), 75, centre, 3);
		assertTrue("top " + java.util.Arrays.toString(box), box[1] >= 14 && box[1] <= 21);
		assertTrue("two glyphs down " + java.util.Arrays.toString(box), box[3] >= 70 && box[3] <= 76);
	}

	/**
	 * text-anchor: middle centres the column on y = 100 (75pt): two 40-unit glyphs from 60 to 140 units (45 to 105pt).
	 * Batik's chunk length had half an em too many, and the column stood a quarter em (7.5pt) high.
	 */
	public void testAnchorMiddle() throws Exception {
		final int[] box = ink("<text class='v' x='100' y='100' font-size='40' text-anchor='middle'>国国</text>");
		assertNotNull("no ink", box);
		assertEquals("middle " + java.util.Arrays.toString(box), 75, (box[1] + box[3]) / 2.0, 2.5);
	}

	/** The angle rounded as Batik rounds it: 1deg is upright (0), 1.5rad (86deg) is on the side (90). */
	public void testOrientationAngles() throws Exception {
		assertFalse(fonts("<text class='v' x='100' y='10' font-size='20' glyph-orientation-vertical='1deg'>IIII</text>")
				.stream().anyMatch(f -> f.contains("Times")));
		assertTrue(fonts("<text class='v' x='100' y='10' font-size='20' glyph-orientation-vertical='1.5rad'>IIII</text>")
				.stream().anyMatch(f -> f.contains("Times")));
	}

	/** The fonts the page draws with, in its form XObjects too. */
	private static java.util.Set<String> fonts(final String svgBody) throws Exception {
		final java.util.Set<String> names = new java.util.TreeSet<String>();
		try (PDDocument doc = Loader.loadPDF(pdf(svgBody))) {
			collect(doc.getPage(0).getResources(), names);
		}
		return names;
	}

	private static void collect(final org.apache.pdfbox.pdmodel.PDResources r, final java.util.Set<String> names)
			throws Exception {
		for (final org.apache.pdfbox.cos.COSName n : r.getFontNames()) {
			names.add(r.getFont(n).getName());
		}
		for (final org.apache.pdfbox.cos.COSName n : r.getXObjectNames()) {
			if (r.getXObject(n) instanceof org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject f
					&& f.getResources() != null) {
				collect(f.getResources(), names);
			}
		}
	}

	/**
	 * Upright Latin letters: a horizontal font (Times here) cannot stand them up, so they go to the Japanese font; on
	 * their side (mixed) they are Times'. Batik places the next run after upright advances either way, so the drawing
	 * itself is what this looks at.
	 */
	public void testUprightLatin() throws Exception {
		final java.util.Set<String> upright = fonts("<text class='v u' x='100' y='10' font-size='20'>IIII</text>");
		final java.util.Set<String> mixed = fonts("<text class='v' x='100' y='10' font-size='20'>IIII</text>");
		assertTrue("mixed " + mixed, mixed.stream().anyMatch(f -> f.contains("Times")));
		assertFalse("upright " + upright, upright.stream().anyMatch(f -> f.contains("Times")));
	}
}
