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
 * <b>放射・円錐グラデーションと{@code filter}</b>を画素で固定します
 * (css-images-3/4、filter-effects-1、2026-08-29)。
 *
 * <p>
 * 表示リストのgoldenは塗りの要約しか持たないので、PDFBoxで描画した
 * 画素を見る: 放射の中心は最初の色・角は最後の色、円錐は象限で色が
 * 違う、半透明グラデーションの下の背景色が透ける、グレースケールの
 * 赤い画像はr≈g≈b、drop-shadowの箱の右下外に灰の影、opacity()・
 * brightness()の色値、親のフィルタが子の背景に届く。
 * </p>
 */
public class GradientFilterTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
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

	public void testGradients() throws Exception {
		final java.awt.image.BufferedImage img = render("files/unittest/3080-MODERN-CSS/gradients.html");
		// #radial: 10,10 100pt角 circle closest-side red→blue
		assertTrue("放射の中心が最初の色(赤)ではありません: " + hex(img.getRGB(60, 60)), isRed(img.getRGB(60, 60)));
		assertTrue("放射の角が最後の色(青)ではありません: " + hex(img.getRGB(13, 13)), isBlue(img.getRGB(13, 13)));
		// #conic: 120,120 100pt角、中心(170,170)。北東=赤 南東=lime 南西=青 北西=黄
		assertTrue("円錐の北東: " + hex(img.getRGB(195, 145)), isRed(img.getRGB(195, 145)));
		assertTrue("円錐の南東: " + hex(img.getRGB(195, 195)), isGreen(img.getRGB(195, 195)));
		assertTrue("円錐の南西: " + hex(img.getRGB(145, 195)), isBlue(img.getRGB(145, 195)));
		assertTrue("円錐の北西: " + hex(img.getRGB(145, 145)), isYellow(img.getRGB(145, 145)));
		// #over-color: 10,230 100x50、透明→青のグラデーションの下に黄
		assertTrue("半透明グラデーションの左端で背景色(黄)が透けていません: " + hex(img.getRGB(13, 255)),
				isYellow(img.getRGB(13, 255)));
		assertTrue("グラデーションの右端が青ではありません: " + hex(img.getRGB(107, 255)), isBlue(img.getRGB(107, 255)));
		// #repeating: 230,10 60x100、5pt周期の黒白縞。y=12は黒・y=17は白
		assertTrue("繰り返しの黒帯: " + hex(img.getRGB(260, 12)), isDark(img.getRGB(260, 12)));
		assertTrue("繰り返しの白帯: " + hex(img.getRGB(260, 17)), isLight(img.getRGB(260, 17)));
		assertTrue("繰り返しの2周期目の黒帯: " + hex(img.getRGB(260, 22)), isDark(img.getRGB(260, 22)));
	}

	public void testFilter() throws Exception {
		final java.awt.image.BufferedImage img = render("files/unittest/3080-MODERN-CSS/filter.html");
		// #gray: red.png(実際は(241,203,203)の淡い赤)のgrayscale(1) → 輝度211の灰
		final int gray = img.getRGB(40, 40);
		assertTrue("grayscaleの画像が無彩色ではありません: " + hex(gray), isGray(gray) && r(gray) < 235 && r(gray) > 180);
		// #shadow: 120,10 100x60 緑、drop-shadow(5pt 5pt 3pt) → 右下の外側(224,40)は灰
		final int shadow = img.getRGB(224, 40);
		assertTrue("drop-shadowの影がありません: " + hex(shadow), isGray(shadow) && r(shadow) < 230);
		assertTrue("箱の内側は緑のまま: " + hex(img.getRGB(170, 40)), isGreen(img.getRGB(170, 40)));
		// #opacity: 赤のopacity(.5) → 白の上で(255,128,128)前後
		final int half = img.getRGB(60, 125);
		assertTrue("opacity(.5)の赤: " + hex(half), r(half) > 240 && g(half) > 100 && g(half) < 160 && b(half) > 100
				&& b(half) < 160);
		// #bright: rgb(100,100,100)のbrightness(2) → 200前後
		final int bright = img.getRGB(170, 125);
		assertTrue("brightness(2): " + hex(bright), isGray(bright) && r(bright) > 185 && r(bright) < 215);
		// #nest: 親のgrayscale(1)が子の赤い背景・青い境界に届く
		assertTrue("親のフィルタが子の背景に届いていません: " + hex(img.getRGB(60, 195)), isGray(img.getRGB(60, 195)));
		assertTrue("親のフィルタが子の境界に届いていません: " + hex(img.getRGB(12, 195)), isGray(img.getRGB(12, 195)));
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

	private static boolean isRed(final int rgb) {
		return r(rgb) > 180 && g(rgb) < 90 && b(rgb) < 90;
	}

	private static boolean isGreen(final int rgb) {
		return g(rgb) > 180 && r(rgb) < 90 && b(rgb) < 90;
	}

	private static boolean isBlue(final int rgb) {
		return b(rgb) > 180 && r(rgb) < 90 && g(rgb) < 90;
	}

	private static boolean isYellow(final int rgb) {
		return r(rgb) > 180 && g(rgb) > 180 && b(rgb) < 90;
	}

	private static boolean isDark(final int rgb) {
		return r(rgb) < 80 && g(rgb) < 80 && b(rgb) < 80;
	}

	private static boolean isLight(final int rgb) {
		return r(rgb) > 200 && g(rgb) > 200 && b(rgb) > 200;
	}

	private static boolean isGray(final int rgb) {
		return Math.abs(r(rgb) - g(rgb)) < 12 && Math.abs(g(rgb) - b(rgb)) < 12;
	}
}
