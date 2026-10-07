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
 * Verify that word-space width comes from the font that draws the space (2026-10-04).
 *
 * <p>
 * Space width previously came from the first font candidate. Production font settings put an emoji font
 * first in serif and other lists; it has no space and reports a half-width advance.
 * This expanded all word spaces in default Latin text to 0.5em (a Times space is 0.25em).
 * </p>
 */
public class WordSpaceFontTest extends TestCase {
	private static BufferedImage png(final String family) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:300pt 60pt;margin:10pt} body{margin:0;font-size:20pt;font-family:" + family + "}"
				+ "</style></head><body><p>aa bb cc dd</p></body></html>";
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "image/png");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///word-space.html"), "text/html", null);
		} finally {
			session.close();
		}
		return ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
	}

	/** Rightmost column with dark pixels (captures total word spacing even if line heights differ). */
	private static int rightmostInk(final BufferedImage image) {
		for (int x = image.getWidth() - 1; x >= 0; --x) {
			for (int y = 0; y < image.getHeight(); ++y) {
				final int rgb = image.getRGB(x, y);
				if (((rgb >> 16) & 0xff) + ((rgb >> 8) & 0xff) + (rgb & 0xff) < 384) {
					return x;
				}
			}
		}
		return -1;
	}

	public void testEmojiFirstDoesNotWidenWordSpaces() throws Exception {
		// Even with an emoji font first (line height may change), word spacing remains the Times space width.
		final int withEmoji = rightmostInk(png("'emoji','Times New Roman'"));
		final int plain = rightmostInk(png("'Times New Roman'"));
		assertTrue(plain > 0);
		// Vertical positions can cause 1-pixel rounding. Half-width spacing would shift three spaces by about 20 pixels.
		assertTrue("right end of the text: " + plain + " vs " + withEmoji, Math.abs(plain - withEmoji) <= 1);
	}
}
