package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;

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
 * Pin down <b>text wrapping around {@code shape-outside: circle(50%)}</b> with pixels
 * (css-shapes-1, 2026-08-29).
 *
 * <p>
 * A circle of radius 50 pt in a 100 pt square left float (no background). Inspect the rendered result
 * at 72 dpi (1 pt = 1 px): (1) body-text pixels exist in the top-right corner of the margin box
 * (outside the circle), meaning lines avoid the circle rather than a rectangle; (2) no body-text pixels
 * exist inside the circle; (3) a line near the bottom of the circle (y=96–108) starts within the float's
 * 100 pt width, meaning lines descend around the circle rather than jumping to its bottom.
 * </p>
 */
public class ShapeOutsideTest extends TestCase {
	public void testCircleWrap() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/0120-float/shape-outside-circle.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final java.awt.image.BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			assertTrue("本文がどこにも描かれていません", countDark(img, 100, 0, 300, 120) > 50);
			// (1) Top-right corner: the first line starts at x≈82.5, so body text exists in [84,100)×[1,11).
			assertTrue("円の外(フロート右上隅)に本文が回り込んでいません", countDark(img, 84, 1, 100, 11) > 0);
			// (2) No body text inside the circle (use radius 48 to exclude the antialiased edge).
			int inside = 0;
			for (int y = 0; y < 100; ++y) {
				for (int x = 0; x < 100; ++x) {
					final double dx = x + 0.5 - 50, dy = y + 0.5 - 50;
					if (dx * dx + dy * dy < 48 * 48 && isDark(img.getRGB(x, y))) {
						++inside;
					}
				}
			}
			assertEquals("円の内側に本文の画素があります", 0, inside);
			// (3) The line at y=96 starts at x≈69.6: body text exists in [70,100)×[97,107).
			assertTrue("円の下端付近の行がフロートの下まで飛んでいます(円に沿って下りていない)",
					countDark(img, 70, 97, 100, 107) > 0);
			// (4) Lines below the circle (y≥108) start at the left edge.
			assertTrue("円の下の行が左端へ戻っていません", countDark(img, 0, 110, 20, 120) > 0);
		}
	}

	private static int countDark(final java.awt.image.BufferedImage img, final int x0, final int y0, final int x1,
			final int y1) {
		int n = 0;
		for (int y = y0; y < y1; ++y) {
			for (int x = x0; x < x1; ++x) {
				if (isDark(img.getRGB(x, y))) {
					++n;
				}
			}
		}
		return n;
	}

	private static boolean isDark(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r < 128 && g < 128 && b < 128;
	}
}
