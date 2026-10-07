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
 * Verifies <b>basic {@code clip-path} shapes</b> (css-shapes-1, 2026-08-22).
 *
 * <p>
 * Clips a 100 pt square red box with circle(30pt at 50pt 50pt) and checks the rendered pixels:
 * the circle's center is red; outside the circle (the box's four corners) is white.
 * </p>
 */
public class ClipPathTest extends TestCase {
	public void testCircleClip() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3080-MODERN-CSS/clip-path-circle.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final java.awt.image.BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			// 200 pt square sheet, margin 10 pt. Box top-left is (10,10); circle(30pt at 50,50).
			// Center (60,60) is red; box corners (15,15)/(105,105) are white (clipped).
			assertTrue("円の中心が塗られていません", isRed(img.getRGB(60, 60)));
			assertTrue("円の内側(中心+20pt)が塗られていません", isRed(img.getRGB(60, 80)));
			assertFalse("円の外(ボックス左上)が切り抜かれていません", isRed(img.getRGB(15, 15)));
			assertFalse("円の外(ボックス右下)が切り抜かれていません", isRed(img.getRGB(105, 105)));
			assertFalse("円の外(ボックス右上)が切り抜かれていません", isRed(img.getRGB(105, 15)));
		}
	}

	/**
	 * <b>{@code clip-path} on replaced elements ({@code <img>})</b> (2026-08-29).
	 *
	 * <p>
	 * The path reported by a user as "works on div, but not on {@code <img>}".
	 * Replaced elements do not go through {@code AbstractContainerBox}, so clip-path, stored only in
	 * {@code BlockParams}, was silently discarded. Clips a 100 pt square red image with the same circle
	 * and checks pixels to verify that the center is red and the four corners are white.
	 * </p>
	 */
	public void testCircleClipOnImage() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3080-MODERN-CSS/clip-path-image.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final java.awt.image.BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			assertTrue("円の中心に画像が出ていません", isRed(img.getRGB(60, 60)));
			assertTrue("円の内側(中心+20pt)に画像が出ていません", isRed(img.getRGB(60, 80)));
			assertFalse("円の外(画像の左上)が切り抜かれていません", isRed(img.getRGB(15, 15)));
			assertFalse("円の外(画像の右下)が切り抜かれていません", isRed(img.getRGB(105, 105)));
			assertFalse("円の外(画像の右上)が切り抜かれていません", isRed(img.getRGB(105, 15)));
		}
	}

	private static boolean isRed(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r > 150 && g < 80 && b < 80;
	}
}
