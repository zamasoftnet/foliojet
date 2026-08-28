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
 * <b>{@code clip-path: path()}</b>を固定します(css-shapes-1、2026-08-29)。
 *
 * <p>
 * 100pt角の赤いボックスを、px座標のSVGパス(右上半分の三角形、相対
 * コマンド)で切り抜く。三角形の内側は赤、外側(左下)は白。
 * </p>
 */
public class ClipPathPathTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testPathClip() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3080-MODERN-CSS/clip-path-path.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final java.awt.image.BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			// 紙200pt角・margin10pt。box左上は(10,10)。三角形は(0,0)-(100,0)-(100,100)
			// →対角線より上(x-10 > y-10)が赤、下が白
			assertTrue("三角形の内側(右上)が塗られていません", isRed(img.getRGB(95, 25)));
			assertTrue("三角形の内側(対角線近く)が塗られていません", isRed(img.getRGB(80, 60)));
			assertFalse("三角形の外(左下)が切り抜かれていません", isRed(img.getRGB(25, 95)));
			assertFalse("三角形の外(左端)が切り抜かれていません", isRed(img.getRGB(15, 60)));
			assertFalse("ボックスの外が塗られています", isRed(img.getRGB(150, 150)));
		}
	}

	private static boolean isRed(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r > 150 && g < 80 && b < 80;
	}
}
