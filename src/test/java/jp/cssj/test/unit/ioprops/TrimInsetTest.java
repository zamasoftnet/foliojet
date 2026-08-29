package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
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
 * <b>{@code output.trim-inset}で仕上り位置を後から指定できる</b>ことを
 * 固定します(2026-08-29、利用者報告B-3)。
 *
 * <p>
 * 塗り足し込みで作られた既存データ——印刷面110pt角のうち外周5ptが
 * 塗り足しで、仕上りサイズは中央の100pt角——を、<b>CSSを書き換えずに</b>
 * 正しい仕上りサイズで出せること。
 * </p>
 */
public class TrimInsetTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String FILE = "files/unittest/0475-bleed/trim-inset.html";

	/** 指定しなければ従来どおり——印刷面110ptがそのまま出る。 */
	public void testWithoutTrimInset() throws Exception {
		final BufferedImage img = render(null, null);
		assertEquals(110, img.getWidth());
		assertEquals(110, img.getHeight());
		assertTrue("外周が塗り足しのまま出ていません", isBlue(img.getRGB(1, 1)));
	}

	/** トンボなし: 用紙は仕上りサイズちょうどになり、塗り足しは断ち落とされる。 */
	public void testTrimmedToFinishedSize() throws Exception {
		final BufferedImage img = render("5pt", null);
		assertEquals("仕上りサイズになっていません", 100, img.getWidth());
		assertEquals(100, img.getHeight());
		assertTrue("仕上り面の左上隅が塗り足しのままです", isRed(img.getRGB(1, 1)));
		assertTrue("仕上り面の右下隅が塗り足しのままです", isRed(img.getRGB(98, 98)));
		assertFalse("塗り足しが残っています", isBlue(img.getRGB(50, 0)));
	}

	/**
	 * トンボあり: トンボは<b>仕上り線</b>に引かれ、塗り足しはその外へ
	 * 5ptだけはみ出す。CSSの{@code bleed}を書いたときと同じ絵になる。
	 */
	public void testMarksAreDrawnOnTheFinishLine() throws Exception {
		final BufferedImage img = render("5pt", "crop");
		final int trim = (img.getWidth() - 100) / 2;
		assertTrue("トンボのための裁ち口がありません: " + trim, trim > 5);
		assertEquals("用紙が正方形になっていません", img.getWidth(), img.getHeight());
		// 仕上り面
		assertTrue(isRed(img.getRGB(trim + 1, trim + 1)));
		assertTrue(isRed(img.getRGB(trim + 98, trim + 98)));
		// 仕上り線の外5ptは塗り足し(青)
		assertTrue("塗り足しが仕上り線の外に出ていません", isBlue(img.getRGB(trim - 3, trim + 50)));
		// そのさらに外は白い帯で、そこにトンボが引かれる
		assertFalse("塗り足しが5ptより外まで出ています", isBlue(img.getRGB(trim - 8, trim + 50)));
		// トンボは塗り足しのすぐ外の白い帯に引かれる(実測: 仕上り線の
		// 5pt外から外側へ)。左上のコーナートンボが来る範囲を見る
		assertTrue("トンボが引かれていません", hasInk(img, 0, 0, trim - 4, trim - 4));
	}

	private static BufferedImage render(final String trimInset, final String marks) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			if (trimInset != null) {
				session.property("output.trim-inset", trimInset);
			}
			if (marks != null) {
				session.property("output.marks", marks);
			}
			CTISessionHelper.transcodeFile(session, new File(FILE), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			return new PDFRenderer(doc).renderImageWithDPI(0, 72);
		}
	}

	private static boolean hasInk(final BufferedImage img, final int x0, final int y0, final int w, final int h) {
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

	private static boolean isBlue(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return b > 150 && r < 80 && g < 80;
	}
}
