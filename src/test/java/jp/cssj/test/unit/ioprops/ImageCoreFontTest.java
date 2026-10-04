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
 * 画像出力の既定の欧文は、字形を持つ書体で描くことを固定します(2026-10-04)。
 *
 * <p>
 * 既定の書体(Times New Roman, Times, serif)は中核書体 Times-Roman に当たる。画像では Java2D で描くが、
 * Java2D はこの名前を知らず sans の既定で描き、それを AFM の送り幅で並べるので字形も字間も崩れていた
 * (本番の preview)。字形を持たない中核書体は候補の最後に回すので、serif の次の候補(この試験の設定では
 * Noto Serif JP の欧文)で描かれる。
 * </p>
 */
public class ImageCoreFontTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

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
