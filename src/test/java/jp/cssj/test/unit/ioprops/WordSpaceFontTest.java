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
 * 単語間の空白の幅は、空白を描く書体の幅であることを固定します(2026-10-04)。
 *
 * <p>
 * 空白の幅は書体の候補の先頭の書体から取っていた。本番の書体設定は serif などの並びの先頭が絵文字の書体
 * (空白を持たず、空白の幅に半角を答える)なので、既定の書体の欧文の語間がどれも半角(0.5em)に広がっていた
 * (Times の空白は 0.25em)。
 * </p>
 */
public class WordSpaceFontTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

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

	/** 濃い画素のある右端の列(行の高さが違っても、語間の合計が出る)。 */
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
		// 絵文字の書体が先頭にあっても(行の高さは変わり得るが)語間は Times の空白のまま
		final int withEmoji = rightmostInk(png("'emoji','Times New Roman'"));
		final int plain = rightmostInk(png("'Times New Roman'"));
		assertTrue(plain > 0);
		// 縦の位置の違いで 1 画素の丸めは出る。語間が半角なら空白 3 つで約 20 画素ずれる
		assertTrue("right end of the text: " + plain + " vs " + withEmoji, Math.abs(plain - withEmoji) <= 1);
	}
}
