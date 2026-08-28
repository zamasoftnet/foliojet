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
 * <b>{@code mix-blend-mode: multiply}</b>を固定します(compositing-1、
 * 2026-08-29)。
 *
 * <p>
 * 黄(255,255,0)の箱の上に、multiplyのシアン(0,255,255)の箱を重ねる。
 * 重なりは乗算で緑(0,255,0)——どちらの箱より暗い(R・Bとも落ちる)。
 * 重ならない部分はそれぞれの色のまま。PDFBoxはExtGStateの/BMを
 * 描画で解釈するので、PDFに/BMが出ていることの実証にもなる。
 * </p>
 */
public class MixBlendModeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testMultiply() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3080-MODERN-CSS/mix-blend-mode.html"), "text/html", null);
		} finally {
			session.close();
		}
		final byte[] pdf = out.toByteArray();
		final String raw = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
		assertTrue("PDFにExtGStateの/BM /Multiplyがありません", raw.contains("/BM /Multiply") || raw.contains("/BM/Multiply"));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final java.awt.image.BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			final int yellow = img.getRGB(30, 30);
			final int cyan = img.getRGB(140, 140);
			final int overlap = img.getRGB(80, 80);
			assertTrue("黄の箱: " + hex(yellow), r(yellow) > 200 && g(yellow) > 200 && b(yellow) < 60);
			assertTrue("シアンの箱: " + hex(cyan), r(cyan) < 60 && g(cyan) > 200 && b(cyan) > 200);
			// 乗算: (255,255,0)×(0,255,255)/255 = (0,255,0)
			assertTrue("重なりが乗算(緑)になっていません: " + hex(overlap),
					r(overlap) < 60 && g(overlap) > 200 && b(overlap) < 60);
			assertTrue("重なりが黄より暗くありません", r(overlap) < r(yellow) - 100);
			assertTrue("重なりがシアンより暗くありません", b(overlap) < b(cyan) - 100);
		}
	}

	private static int r(final int rgb) {
		return (rgb >> 16) & 0xFF;
	}

	private static int g(final int rgb) {
		return (rgb >> 8) & 0xFF;
	}

	private static int b(final int rgb) {
		return rgb & 0xFF;
	}

	private static String hex(final int rgb) {
		return String.format("#%06x", rgb & 0xFFFFFF);
	}
}
