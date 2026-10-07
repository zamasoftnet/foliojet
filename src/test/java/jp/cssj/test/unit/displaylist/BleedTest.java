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
 * Verifies that <b>bleed declared with {@code @page { bleed }} is actually printed</b>
 * (2026-08-29).
 *
 * <p>
 * Based on a user report (Japan Liberal Party, Kawasaki). Previously, even with {@code bleed},
 * content was <b>clipped at the trim line</b>, leaving the bleed band white,
 * because cuttingMargin remained 0 when {@code output.marks}=none.
 * When CSS declares bleed, draw beyond the trim line by that width.
 * </p>
 */
public class BleedTest extends TestCase {
	/**
	 * Without crop marks: the sheet is a 110 pt square, from trim size 100 pt + bleed 5 pt×2,
	 * and is painted all the way to its corners.
	 */
	public void testBleedIsPrinted() throws Exception {
		final java.awt.image.BufferedImage img = render("files/unittest/0475-bleed/bleed-full.html");
		assertEquals("paper = page + bleed on both sides", 110, img.getWidth());
		assertEquals(110, img.getHeight());
		assertTrue("塗り足しが用紙の左上隅まで届いていません", isRed(img.getRGB(1, 1)));
		assertTrue("塗り足しが用紙の右下隅まで届いていません",
				isRed(img.getRGB(img.getWidth() - 2, img.getHeight() - 2)));
		assertTrue("仕上り面が塗られていません", isRed(img.getRGB(55, 55)));
	}

	/**
	 * With crop marks: the cutting margin expands to accommodate crop marks
	 * (reducing it to the bleed width would put the marks off the sheet, making them disappear).
	 * Bleed reaches 5 pt beyond the trim line, and crop marks are drawn in the white band beyond it.
	 */
	public void testMarksKeepTheirBand() throws Exception {
		final java.awt.image.BufferedImage img = render("files/unittest/0475-bleed/bleed-marks.html");
		final int trim = (img.getWidth() - 100) / 2;
		assertTrue("トンボのための裁ち口が塗り足しより広くありません: " + trim, trim > 5);
		// The area inside the trim line and 5 pt beyond it (=bleed) is painted.
		assertTrue(isRed(img.getRGB(trim + 50, trim + 50)));
		assertTrue("塗り足しが仕上り線の外まで届いていません", isRed(img.getRGB(trim - 3, trim + 50)));
		// Beyond the bleed is a white band (where crop marks are drawn).
		assertFalse("塗り足しの外まで塗られています", isRed(img.getRGB(1, trim + 50)));
		assertTrue("トンボが引かれていません", hasInk(img, 0, 0, trim, trim));
	}

	private static java.awt.image.BufferedImage render(final String file) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File(file), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			return new PDFRenderer(doc).renderImageWithDPI(0, 72);
		}
	}

	/** Whether there is at least one non-white pixel (counts crop-mark lines). */
	private static boolean hasInk(final java.awt.image.BufferedImage img, final int x0, final int y0, final int w,
			final int h) {
		for (int y = y0; y < y0 + h; ++y) {
			for (int x = x0; x < x0 + w; ++x) {
				final int rgb = img.getRGB(x, y);
				if (((rgb >> 16) & 0xFF) < 200 && ((rgb >> 8) & 0xFF) < 200 && (rgb & 0xFF) < 200) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isRed(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r > 150 && g < 80 && b < 80;
	}
}
