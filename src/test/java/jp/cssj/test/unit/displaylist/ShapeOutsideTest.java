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
 * <b>{@code shape-outside: circle(50%)}の回り込み</b>を画素で固定します
 * (css-shapes-1、2026-08-29)。
 *
 * <p>
 * 100pt角の左フロート(背景なし)に半径50ptの円。描画結果を72dpi
 * (1pt=1px)で検査する: (1)マージンボックスの右上隅(円の外)に本文の
 * 画素がある=行が矩形ではなく円を避けている、(2)円の内側に本文の画素が
 * ない、(3)円の下端付近の行(y=96〜108)がフロート幅100ptの内側から
 * 始まる=下端まで飛ばずに円の周りを下りている。
 * </p>
 */
public class ShapeOutsideTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testCircleWrap() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/0120-float/shape-outside-circle.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final java.awt.image.BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			assertTrue("本文がどこにも描かれていません", countDark(img, 100, 0, 300, 120) > 50);
			// (1) 右上隅: 1行目は x≈82.5 から始まるので [84,100)×[1,11) に本文がある
			assertTrue("円の外(フロート右上隅)に本文が回り込んでいません", countDark(img, 84, 1, 100, 11) > 0);
			// (2) 円の内側(半径48で見る——アンチエイリアスの縁を除く)に本文がない
			int inside = 0;
			for (int y = 0; y < 100; ++y) {
				for (int x = 0; x < 100; ++x) {
					final double dx = x + 0.5 - 50, dy = y + 0.5 - 50;
					if (dx * dx + dy * dy < 48 * 48 && isDark(img.getRGB(x, y))) {
						++inside;
					}
				}
			}
			assertEquals("円の内側に本文の画素があります", 0, inside);
			// (3) y=96の行は x≈69.6 から始まる: [70,100)×[97,107) に本文がある
			assertTrue("円の下端付近の行がフロートの下まで飛んでいます(円に沿って下りていない)",
					countDark(img, 70, 97, 100, 107) > 0);
			// (4) 円の下(y≥108)の行は左端から始まる
			assertTrue("円の下の行が左端へ戻っていません", countDark(img, 0, 110, 20, 120) > 0);
		}
	}

	private static int countDark(final java.awt.image.BufferedImage img, final int x0, final int y0, final int x1,
			final int y1) {
		int n = 0;
		for (int y = y0; y < y1; ++y) {
			for (int x = x0; x < x1; ++x) {
				if (isDark(img.getRGB(x, y))) {
					++n;
				}
			}
		}
		return n;
	}

	private static boolean isDark(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r < 128 && g < 128 && b < 128;
	}
}
