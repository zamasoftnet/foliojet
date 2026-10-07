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
 * Pin down rendering of the <b>{@code text-shadow} blur radius</b> with pixels (2026-08-29).
 *
 * <p>
 * Fixture {@code 0150-text-shadow/blur.html} (40 pt "I", each block 50 pt): #sharp is
 * {@code 6pt 0 0 black}, #blur is {@code 6pt 0 8pt black}, and #soft is
 * {@code 0 0 6pt rgba(255,0,0,.5)}. The blurred shadow has non-white pixels farther right
 * than the sharp shadow's right edge (a 12-step approximation with σ=blur/2 gives
 * about 0.87×blur ≈ 7 pt), while the glyph itself stays black at the same position.
 * The translucent red blur creates pale red outside the glyph, never pure red
 * (alpha is not that of a single step).
 * </p>
 */
public class TextShadowBlurTest extends TestCase {
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

			// Blocks: 0 sharp (y=10..60 pt), 1 blur (60..110), 2 soft (110..160).
			final int sharpRight = rightmostNonWhite(img, 0);
			final int blurRight = rightmostNonWhite(img, 1);
			assertTrue("ぼかした影がぼかし無しの影より外へ広がっていません (sharp=" + sharpRight + ", blur="
					+ blurRight + ")", blurRight >= sharpRight + 4 * SCALE);
			// The blur's outer edge is pale (not black).
			assertFalse("ぼかしの外縁が真っ黒です", isDark(img.getRGB(blurRight, rowOf(1))));
			// The glyph itself is black and starts at the same left edge in both blocks.
			final int sharpLeft = leftmostNonWhite(img, 0);
			final int blurLeft = leftmostNonWhite(img, 1);
			assertTrue("ぼかしで字形の位置が変わっています", Math.abs(sharpLeft - blurLeft) <= 6 * SCALE);
			// Inside the "I" stem (about 3.7 pt wide), 1.5 pt from the left edge.
			assertTrue("字形本体が黒くありません", isDark(img.getRGB(sharpLeft + 3, rowOf(0)))
					&& isDark(img.getRGB(sharpLeft + 3, rowOf(1))));

			// Translucent red blur: pale reddish pixels exist outside the glyph's right edge (up to 6 pt), but no pure red.
			final int softLeft = leftmostNonWhite(img, 2);
			int tinted = 0, saturated = 0;
			for (int x = softLeft; x < softLeft + 30 * SCALE; ++x) {
				final int rgb = img.getRGB(x, rowOf(2));
				final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (r > g + 20 && r > b + 20) {
					++tinted;
					// Bright pure red (translucency mixes with white to produce pink; exclude dark red
					// at the glyph edge because it mixes with black).
					if (r > 200 && g < 40 && b < 40) {
						++saturated;
					}
				}
			}
			assertTrue("半透明の赤いぼかしがありません", tinted > 0);
			assertEquals("半透明の影が不透明の赤で描かれています", 0, saturated);
		}
	}

	/** Row at the block's middle (slightly above the baseline, at the height of the I stem). */
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
