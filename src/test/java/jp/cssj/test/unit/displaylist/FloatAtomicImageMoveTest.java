package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** At a page boundary inside a float, a fixed-height wrapper must not truncate an atomic image. */
public class FloatAtomicImageMoveTest extends TestCase {
	public void testAtomicImageMovesWholeWithItsExplicitHeightWrappers() throws Exception {
		assertFullImageOnSecondPage("atomic-explicit-height-page-break.html", 0);
	}

	/** A float and fixed-height image wrapper inside a BFC with preceding content, as in Yahoo! News. */
	public void testAtomicImageMovesWholeThroughInlineWrappers() throws Exception {
		assertFullImageOnSecondPage("atomic-inline-wrapper-page-break.html", 145);
	}

	private static void assertFullImageOnSecondPage(final String name, final int imageBottomY) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/0120-float/" + name), "text/html", null);
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			assertTrue("改ページ後のページがありません", doc.getNumberOfPages() >= 2);
			final java.awt.image.BufferedImage page2 = new PDFRenderer(doc).renderImageWithDPI(1, 72);
			// Checking only the center misses the regression actually seen on Yahoo! News:
			// only a thin top slice was drawn on the next page and the rest was clipped.
			// Require both the top and a point near the bottom to verify that the full image height moved.
			if (imageBottomY == 0) {
				// Existing regression with a simple block wrapper.
				assertTrue("atomic画像が固定高ラッパーの残量へ切り詰められています",
						isRed(page2.getRGB(35, 35)));
			} else {
				final int span = redVerticalSpan(page2);
				assertTrue("atomic画像が次ページにありません", span > 0);
				assertTrue("改ページしたBFCがatomic画像をclipしています: " + span,
						span >= imageBottomY);
			}
		}
	}

	private static int redVerticalSpan(final java.awt.image.BufferedImage image) {
		int minY = image.getHeight(), maxY = -1;
		for (int y = 0; y < image.getHeight(); ++y) {
			for (int x = 0; x < image.getWidth(); ++x) {
				if (isRed(image.getRGB(x, y))) {
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}
		return maxY < minY ? 0 : maxY - minY + 1;
	}

	private static boolean isRed(final int rgb) {
		final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return r > 150 && g < 80 && b < 80;
	}
}
