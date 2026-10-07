package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Image output with a transparent background (introduced on 2026-09-09).
 *
 * <p>
 * <b>Successful conversion proves nothing.</b> An image is still produced if an intended transparent
 * background is painted white. Here, <b>read the output image back and inspect pixel alpha</b>.
 * </p>
 *
 * <p>
 * If requested for a format without alpha (JPEG, etc.), render on white and report {@code 2824}.
 * Silently ignoring the request would leave users wondering why their transparent image is white.
 * </p>
 */
public class TransparentImageOutputTest extends TestCase {

	/** Nothing is drawn at the top left. Place an opaque red box in the center. */
	private static final String HTML = "<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
			+ "@page { size: 100px 100px; margin: 0 }"
			+ "body { margin: 0 }"
			+ "div { position: absolute; left: 40px; top: 40px; width: 20px; height: 20px;"
			+ " background: #ff0000 }"
			+ "</style></head><body><div></div></body></html>";

	private final List<String> messages = Collections.synchronizedList(new ArrayList<String>());

	private BufferedImage convert(final String mimeType, final Boolean transparent) throws Exception {
		this.messages.clear();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(new MessageHandler() {
				@Override
				public void message(final short code, final String[] args, final String mes) {
					messages.add(Integer.toHexString(code & 0xFFFF).toUpperCase());
				}
			});
			session.property("output.type", mimeType);
			session.property("output.image.resolution", "72");
			session.property("output.resolution", "72");
			if (transparent != null) {
				session.property("output.image.transparent", transparent.toString());
			}
			try (InputStream in = new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8))) {
				CTISessionHelper.transcodeStream(session, in,
						new java.io.File("files/unittest/transparent-probe.html").toURI(), "text/html", "UTF-8");
			}
		} finally {
			session.close();
		}
		final byte[] bytes = out.toByteArray();
		assertTrue(mimeType + ": 画像が出ていない", bytes.length > 0);
		final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
		assertNotNull(mimeType + ": 読み戻せない", image);
		return image;
	}

	private static int alphaAt(final BufferedImage image, final int x, final int y) {
		return image.getRGB(x, y) >>> 24;
	}

	private static int rgbAt(final BufferedImage image, final int x, final int y) {
		return image.getRGB(x, y) & 0xFFFFFF;
	}

	/** PNG has a transparent background. */
	public void testPngIsTransparent() throws Exception {
		final BufferedImage image = this.convert("image/png", Boolean.TRUE);
		assertEquals("何も描いていないところが透明でない", 0, alphaAt(image, 2, 2));
		assertEquals("描いたところが不透明でない", 255, alphaAt(image, 50, 50));
		assertEquals("描いたところの色が違う", 0xFF0000, rgbAt(image, 50, 50));
		assertFalse("2824 が出ている(PNGはアルファを持てるはず): " + this.messages,
				this.messages.contains("2824"));
	}

	/** GIF has a transparent background. */
	public void testGifIsTransparent() throws Exception {
		final BufferedImage image = this.convert("image/gif", Boolean.TRUE);
		assertEquals("何も描いていないところが透明でない", 0, alphaAt(image, 2, 2));
		assertEquals("描いたところが不透明でない", 255, alphaAt(image, 50, 50));
		assertFalse("2824 が出ている(GIFはアルファを持てるはず): " + this.messages,
				this.messages.contains("2824"));
	}

	/** Without the setting, paint white as before (the default is unchanged). */
	public void testPngStaysOpaqueByDefault() throws Exception {
		final BufferedImage image = this.convert("image/png", null);
		assertEquals("既定なのに透明になっている", 255, alphaAt(image, 2, 2));
		assertEquals("既定の背景が白でない", 0xFFFFFF, rgbAt(image, 2, 2));
	}

	/** Explicit {@code false} also paints white. */
	public void testPngStaysOpaqueWhenAskedNotTo() throws Exception {
		final BufferedImage image = this.convert("image/png", Boolean.FALSE);
		assertEquals("false なのに透明になっている", 255, alphaAt(image, 2, 2));
	}

	/**
	 * For formats without alpha, render on white and report {@code 2824}.
	 *
	 * <p>
	 * <b>Do not silently ignore it.</b> Leaving users wondering why a supposedly transparent image
	 * is white is the most costly outcome.
	 * </p>
	 */
	public void testJpegWarnsAndStaysOpaque() throws Exception {
		final BufferedImage image = this.convert("image/jpeg", Boolean.TRUE);
		assertEquals("JPEGなのに透明になっている", 255, alphaAt(image, 2, 2));
		assertTrue("アルファを持てない形式なのに 2824 が出ていない: " + this.messages,
				this.messages.contains("2824"));
	}
}
