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
 * <b>{@code @page { bleed }}で塗り足しが実際に印刷される</b>ことを固定します
 * (2026-08-29)。
 *
 * <p>
 * 利用者報告(日本自由党川崎)より。以前は{@code bleed}を書いても内容が
 * <b>仕上り線で切り落とされ</b>、塗り足しの帯が白いまま出ていた——
 * 断ち代(cuttingMargin)が{@code output.marks}=noneのとき0のままだったため。
 * CSSで塗り足しを宣言したなら、その幅だけ仕上り線の外へ描く。
 * </p>
 */
public class BleedTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/**
	 * トンボなし: 用紙は仕上り100pt＋塗り足し5pt×2＝110pt角で、
	 * 用紙の隅まで塗られる。
	 */
	public void testBleedIsPrinted() throws Exception {
		final java.awt.image.BufferedImage img = render("files/unittest/0475-bleed/bleed-full.html");
		assertEquals("paper = page + bleed on both sides", 110, img.getWidth());
		assertEquals(110, img.getHeight());
		assertTrue("塗り足しが用紙の左上隅まで届いていません", isRed(img.getRGB(1, 1)));
		assertTrue("塗り足しが用紙の右下隅まで届いていません",
				isRed(img.getRGB(img.getWidth() - 2, img.getHeight() - 2)));
		assertTrue("仕上り面が塗られていません", isRed(img.getRGB(55, 55)));
	}

	/**
	 * トンボあり: 裁ち口はトンボのぶんまで広がる(塗り足しと同じ幅まで
	 * 詰めるとトンボが用紙の外へ出て消えるため)。塗り足しは仕上り線の
	 * 外側5ptまで届き、その外側の白い帯にトンボが引かれる。
	 */
	public void testMarksKeepTheirBand() throws Exception {
		final java.awt.image.BufferedImage img = render("files/unittest/0475-bleed/bleed-marks.html");
		final int trim = (img.getWidth() - 100) / 2;
		assertTrue("トンボのための裁ち口が塗り足しより広くありません: " + trim, trim > 5);
		// 仕上り線の内側と、その外側5pt(=塗り足し)までは塗られている
		assertTrue(isRed(img.getRGB(trim + 50, trim + 50)));
		assertTrue("塗り足しが仕上り線の外まで届いていません", isRed(img.getRGB(trim - 3, trim + 50)));
		// 塗り足しのさらに外は白い帯(そこにトンボが引かれる)
		assertFalse("塗り足しの外まで塗られています", isRed(img.getRGB(1, trim + 50)));
		assertTrue("トンボが引かれていません", hasInk(img, 0, 0, trim, trim));
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

	/** 白でない画素が1つでもあるか(トンボの線を数える)。 */
	private static boolean hasInk(final java.awt.image.BufferedImage img, final int x0, final int y0, final int w,
			final int h) {
		for (int y = y0; y < y0 + h; ++y) {
			for (int x = x0; x < x0 + w; ++x) {
				final int rgb = img.getRGB(x, y);
				if (((rgb >> 16) & 0xFF) < 200 && ((rgb >> 8) & 0xFF) < 200 && (rgb & 0xFF) < 200) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isRed(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r > 150 && g < 80 && b < 80;
	}
}
