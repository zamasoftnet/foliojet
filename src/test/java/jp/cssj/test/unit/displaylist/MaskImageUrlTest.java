package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** {@code mask-image:url(SVG)} が背景色の四角ではなくアイコン形状になること。 */
public class MaskImageUrlTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testSvgUrlMaskUsesBackgroundColorAndTransparentOutside() throws Exception {
		final java.awt.image.BufferedImage image = render("mask-image-url.html");
		assertTrue("SVGマスクの中心が背景色で描かれていません", isRed(image.getRGB(30, 30)));
		assertFalse("URLマスクを無視した背景色の四角が残っています", isRed(image.getRGB(12, 12)));
	}

	/** MDN型の絶対配置::afterでもcurrentColorのURLマスクを描くこと。 */
	public void testExternalLinkPseudoMaskUsesCurrentColor() throws Exception {
		final java.awt.image.BufferedImage image = render("external-link-pseudo-mask.html");
		int blue = 0;
		for (int y = 8; y < 34; ++y) {
			for (int x = 48; x < 78; ++x) {
				final int rgb = image.getRGB(x, y);
				final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (b > 100 && b > r * 2 && b > g) {
					++blue;
				}
			}
		}
		assertTrue("外部リンク疑似要素のcurrentColorマスクが描かれていません: " + blue, blue > 12);
	}

	private static java.awt.image.BufferedImage render(final String file) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3080-MODERN-CSS/" + file), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			return new PDFRenderer(doc).renderImageWithDPI(0, 72);
		}
	}

	private static boolean isRed(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r > 150 && g < 80 && b < 80;
	}
}
