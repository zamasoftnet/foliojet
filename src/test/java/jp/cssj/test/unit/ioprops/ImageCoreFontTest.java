package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify that default Latin text in image output uses a font with glyphs (2026-10-04).
 *
 * <p>
 * The default fonts (Times New Roman, Times, serif) resolve to the core font Times-Roman.
 * Images are drawn with Java2D, which does not know this name and uses its default sans font.
 * Positioning those glyphs with AFM advances corrupted both glyphs and spacing (production preview).
 * Core fonts without glyphs move to the end of the candidate list, so the next serif candidate
 * (Latin glyphs in Noto Serif JP in this test configuration) is used.
 * </p>
 */
public class ImageCoreFontTest extends TestCase {
	private static BufferedImage png(final String family) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:300pt 60pt;margin:10pt} body{margin:0;font-size:20pt" + family + "}</style></head>"
				+ "<body><p>Hamburgefonts target</p></body></html>";
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "image/png");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///core-font.html"), "text/html", null);
		} finally {
			session.close();
		}
		return ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
	}

	public void testDefaultLatinIsDrawnWithAnOutlineFont() throws Exception {
		final BufferedImage byDefault = png("");
		final BufferedImage noto = png(";font-family:'Noto Serif JP'");
		assertEquals(noto.getWidth(), byDefault.getWidth());
		int differ = 0;
		for (int y = 0; y < noto.getHeight(); ++y) {
			for (int x = 0; x < noto.getWidth(); ++x) {
				if (noto.getRGB(x, y) != byDefault.getRGB(x, y)) {
					++differ;
				}
			}
		}
		assertEquals("pixels that differ from Noto Serif JP", 0, differ);
	}
}
