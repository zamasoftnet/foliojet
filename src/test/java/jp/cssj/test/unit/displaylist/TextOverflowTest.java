package jp.cssj.test.unit.displaylist;

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
 * <b>{@code text-overflow: ellipsis}</b>を固定します(css-overflow-3、
 * 2026-08-29)。
 *
 * <p>
 * 幅100ptのnowrapブロックに"MMMM…"(20pt)を入れた3段: ellipsis
 * (overflow:hidden)・clip既定(overflow:hidden)・overflow:visible+ellipsis。
 * 画素で検査する: 1段目は箱の末尾(省略記号の領域)でx-height中央の行に
 * インクが無く(Mの縦画が消えている)、ベースライン直上にはインクがある
 * (省略記号の点)。2段目は同じ領域のx-height中央にMの縦画がある。
 * 1・2段目とも箱の外は白、3段目は箱の外にも文字がある(ellipsis不適用)。
 * </p>
 */
public class TextOverflowTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testEllipsis() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/0040-overflow/text-overflow-ellipsis.html"), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 144); // 2px/pt
			// 目視確認用(失敗時の切り分け)
			new File("build/test-images").mkdirs();
			javax.imageio.ImageIO.write(img, "png", new File("build/test-images/text-overflow-ellipsis.png"));
			// margin10pt。各段: 高さ24pt+間隔6pt → 1段目y=10..34、2段目40..64、3段目70..94
			// 箱はx=10..110pt。省略記号(1em=20pt)はx=90..110ptに置かれる
			final int box0 = 10 * 2, box1 = 110 * 2;
			// 1段目: 箱の外(x>110pt)は白
			assertFalse("ellipsis: 箱の外に文字が描かれています", hasInk(img, box1 + 4, 300 * 2, 10 * 2, 34 * 2));
			// 2段目(clip): 箱の外は白
			assertFalse("clip: 箱の外に文字が描かれています", hasInk(img, box1 + 4, 300 * 2, 40 * 2, 64 * 2));
			// 3段目(overflow:visible): 箱の外にも文字がある
			assertTrue("visible: 箱の外に文字が描かれていません", hasInk(img, box1 + 4, 200 * 2, 70 * 2, 94 * 2));
			// 省略記号の領域(x=92..108pt)。x-height中央(行上端から約12pt)に
			// 1段目はインク無し、2段目はMの縦画がある
			final int ex0 = 92 * 2, ex1 = 108 * 2;
			assertFalse("ellipsis: 省略記号の位置にMが残っています", hasInk(img, ex0, ex1, 18 * 2, 24 * 2));
			assertTrue("clip: 同じ位置にMがありません", hasInk(img, ex0, ex1, 48 * 2, 54 * 2));
			// 1段目の同じ領域、ベースライン直上(行上端から約20pt)には点がある
			assertTrue("ellipsis: 省略記号が描かれていません", hasInk(img, ex0, ex1, 26 * 2, 30 * 2));
			// 1段目の箱の前半には本文が残っている
			assertTrue("ellipsis: 本文が消えています", hasInk(img, box0 + 4, 60 * 2, 18 * 2, 24 * 2));
		}
	}

	private static boolean hasInk(final BufferedImage img, final int x0, final int x1, final int y0, final int y1) {
		for (int y = y0; y < y1; ++y) {
			for (int x = x0; x < x1; ++x) {
				final int rgb = img.getRGB(x, y);
				final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (r + g + b < 3 * 128) {
					return true;
				}
			}
		}
		return false;
	}
}
