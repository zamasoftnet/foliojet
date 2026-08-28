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
 * <b>box-shadow と outline の描画</b>を画素で固定します(2026-08-29)。
 *
 * <p>
 * files/unittest/3080-MODERN-CSS/box-shadow-outline.html を72dpiで描き、
 * 紙200pt角・margin0で1px=1ptの座標を直接見る。
 * </p>
 * <ul>
 * <li>背景の無い箱の硬い影: 箱の外の影の位置は紙より暗く、箱の中は白のまま
 * (影は境界箱の外だけに描く)</li>
 * <li>ぼかし影: 箱のすぐ下は紙より暗い</li>
 * <li>アウトライン: 境界の外のoffsetぶん空けた位置が緑で、隙間は白</li>
 * <li>内側の影: パディング箱の縁が赤で、中央は白</li>
 * </ul>
 */
public class BoxDecorationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testShadowAndOutlinePixels() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/3080-MODERN-CSS/box-shadow-outline.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final java.awt.image.BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72);

			// #hollow: 箱(20,20)-(80,60)、影は(30,30)-(90,70)から箱を抜いたもの
			assertTrue("硬い影が箱の外に描かれていません", isDarkerThanPaper(img.getRGB(85, 65)));
			assertTrue("硬い影が箱の右に描かれていません", isDarkerThanPaper(img.getRGB(85, 40)));
			assertTrue("背景の無い箱の中に影が透けています", isWhite(img.getRGB(50, 40)));
			assertTrue("影の外が汚れています", isWhite(img.getRGB(25, 25)));
			assertTrue("影の外(右下の外側)が汚れています", isWhite(img.getRGB(95, 75)));

			// #card: 箱(100,20)-(160,60)、0 2pt 8pt のぼかし影
			assertTrue("ぼかし影が箱の下に描かれていません", isDarkerThanPaper(img.getRGB(130, 63)));
			assertTrue("白背景の箱の中が影で汚れています", isWhite(img.getRGB(130, 40)));
			assertTrue("ぼかしの外まで塗られています", isWhite(img.getRGB(130, 78)));

			// #outlined: 境界箱(20,110)-(80,150)、outline 3pt offset 2pt → 左辺のx=15..18
			assertTrue("アウトラインが境界の外に描かれていません", isGreen(img.getRGB(16, 130)));
			assertTrue("アウトラインが上辺の外に描かれていません", isGreen(img.getRGB(50, 106)));
			assertTrue("outline-offsetの隙間が塗られています", isWhite(img.getRGB(19, 130)));
			assertTrue("アウトラインの外が塗られています", isWhite(img.getRGB(12, 130)));
			assertTrue("箱の中がアウトラインで汚れています", isWhite(img.getRGB(50, 130)));

			// #inset: 箱(100,110)-(160,150)、inset spread 6pt の赤い縁
			assertTrue("内側の影が縁に描かれていません", isRed(img.getRGB(103, 130)));
			assertTrue("内側の影が箱の外へ漏れています", isWhite(img.getRGB(98, 130)));
			assertTrue("内側の影が中央まで塗られています", isWhite(img.getRGB(130, 130)));
		}
	}

	private static boolean isWhite(final int rgb) {
		return ((rgb >> 16) & 0xFF) >= 250 && ((rgb >> 8) & 0xFF) >= 250 && (rgb & 0xFF) >= 250;
	}

	private static boolean isDarkerThanPaper(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r + g + b < 3 * 245;
	}

	private static boolean isGreen(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return g > 120 && r < 80 && b < 80;
	}

	private static boolean isRed(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r > 150 && g < 80 && b < 80;
	}
}
