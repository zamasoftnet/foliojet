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
 * <b>{@code text-shadow}のぼかし半径</b>の描画を画素で固定します(2026-08-29)。
 *
 * <p>
 * fixture {@code 0150-text-shadow/blur.html}(40pt "I"、各段50pt): #sharp は
 * {@code 6pt 0 0 black}、#blur は {@code 6pt 0 8pt black}、#soft は
 * {@code 0 0 6pt rgba(255,0,0,.5)}。ぼかした影は、ぼかし無しの影の右端より
 * さらに右まで白でない画素を持ち(σ=blur/2の12段近似で約0.87×blur≒7pt)、
 * 字形の本体は同じ位置に黒で残る。半透明の赤いぼかしは字形の外に薄い赤を
 * 作り、真っ赤(段1つ分のアルファでは無い)にはならない。
 * </p>
 */
public class TextShadowBlurTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final int SCALE = 2;

	public void testBlur() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/0150-text-shadow/blur.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 72 * SCALE);
			new File("build/test-images").mkdirs();
			javax.imageio.ImageIO.write(img, "png", new File("build/test-images/text-shadow-blur.png"));

			// 段: 0 sharp(y=10..60pt)、1 blur(60..110)、2 soft(110..160)
			final int sharpRight = rightmostNonWhite(img, 0);
			final int blurRight = rightmostNonWhite(img, 1);
			assertTrue("ぼかした影がぼかし無しの影より外へ広がっていません (sharp=" + sharpRight + ", blur="
					+ blurRight + ")", blurRight >= sharpRight + 4 * SCALE);
			// ぼかしの外縁は薄い(黒ではない)
			assertFalse("ぼかしの外縁が真っ黒です", isDark(img.getRGB(blurRight, rowOf(1))));
			// 字形本体は両段とも同じ左端から始まり黒い
			final int sharpLeft = leftmostNonWhite(img, 0);
			final int blurLeft = leftmostNonWhite(img, 1);
			assertTrue("ぼかしで字形の位置が変わっています", Math.abs(sharpLeft - blurLeft) <= 6 * SCALE);
			// "I"の縦画(幅約3.7pt)の内側、左端から1.5pt
			assertTrue("字形本体が黒くありません", isDark(img.getRGB(sharpLeft + 3, rowOf(0)))
					&& isDark(img.getRGB(sharpLeft + 3, rowOf(1))));

			// 半透明の赤いぼかし: 字形の右外(6ptまで)に赤みのある薄い画素があり、真っ赤ではない
			final int softLeft = leftmostNonWhite(img, 2);
			int tinted = 0, saturated = 0;
			for (int x = softLeft; x < softLeft + 30 * SCALE; ++x) {
				final int rgb = img.getRGB(x, rowOf(2));
				final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (r > g + 20 && r > b + 20) {
					++tinted;
					// 明るい純赤(半透明なら白と混ざってピンクになる。字形の縁の
					// 暗い赤は黒との混色なので除く)
					if (r > 200 && g < 40 && b < 40) {
						++saturated;
					}
				}
			}
			assertTrue("半透明の赤いぼかしがありません", tinted > 0);
			assertEquals("半透明の影が不透明の赤で描かれています", 0, saturated);
		}
	}

	/** 段の中央の行(ベースラインより少し上、Iの縦画がある高さ)。 */
	private static int rowOf(final int index) {
		return (10 + 50 * index + 25) * SCALE;
	}

	private static int rightmostNonWhite(final BufferedImage img, final int index) {
		final int y = rowOf(index);
		for (int x = img.getWidth() - 1; x >= 0; --x) {
			if (!isWhite(img.getRGB(x, y))) {
				return x;
			}
		}
		fail("段" + index + "に描画がありません");
		return -1;
	}

	private static int leftmostNonWhite(final BufferedImage img, final int index) {
		final int y = rowOf(index);
		for (int x = 0; x < img.getWidth(); ++x) {
			if (!isWhite(img.getRGB(x, y))) {
				return x;
			}
		}
		fail("段" + index + "に描画がありません");
		return -1;
	}

	private static boolean isWhite(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r >= 250 && g >= 250 && b >= 250;
	}

	private static boolean isDark(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r + g + b < 3 * 60;
	}
}
