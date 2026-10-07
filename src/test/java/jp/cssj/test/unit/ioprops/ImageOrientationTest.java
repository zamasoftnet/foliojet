package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Tests for <b>photo EXIF orientation</b> ({@code image-orientation}) (introduced on 2026-08-30).
 *
 * <p>
 * The test image is 40×80 with {@code orientation=6} (90 degrees clockwise). Respecting orientation
 * makes it <b>80×40</b>; {@code image-orientation: none} leaves it at <b>40×80</b>.
 *
 * <p>
 * <b>PDF output ignored EXIF orientation.</b> {@code PDFUserAgent} read directly through
 * {@code PDFWriter}, bypassing the EXIF-aware {@code RasterImageLoader}, so
 * <b>landscape photos taken on phones remained sideways</b>
 * (image and SVG output rotated them correctly). The fix peeks at the resource's beginning to read
 * orientation, then passes the same stream, rewound, to PDFWriter to avoid fetching HTTP resources twice.
 *
 * <p>
 * Judge using <b>the actual rendered image</b>. Reading PDF {@code cm} operators is misleading:
 * rotation is in an outer {@code cm}, while the {@code cm} immediately before {@code Do} retains
 * the raw pixel dimensions. Looking only at the innermost one falsely suggests no effect
 * (this was actually misread once).
 */
public class ImageOrientationTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File DOCUMENT = new File("files/unittest/ioprops/image-orientation.html");

	/**
	 * A 40×80 image (top quarter red) is placed in landscape orientation when EXIF is respected,
	 * and in portrait orientation with {@code image-orientation: none}.
	 */
	public void testExifOrientationIsHonouredInPdf() throws Exception {
		final java.awt.image.BufferedImage page = this.render();
		final java.awt.Rectangle[] marks = redMarks(page);
		assertEquals("赤い帯が2つ写ること", 2, marks.length);
		final java.awt.Rectangle rotated = marks[0];
		final java.awt.Rectangle asIs = marks[1];
		assertTrue("EXIFを尊重すると赤い帯は縦長になる: " + rotated, rotated.height > rotated.width);
		assertTrue("image-orientation:none では赤い帯は横長のまま: " + asIs, asIs.width > asIs.height);
	}

	/**
	 * Return clusters of red pixels from top to bottom. The two images are vertically arranged,
	 * so gaps between rows suffice to separate them.
	 */
	private static java.awt.Rectangle[] redMarks(final java.awt.image.BufferedImage page) {
		final List<java.awt.Rectangle> marks = new ArrayList<>();
		java.awt.Rectangle current = null;
		for (int y = 0; y < page.getHeight(); ++y) {
			int minX = Integer.MAX_VALUE, maxX = -1;
			for (int x = 0; x < page.getWidth(); ++x) {
				final int rgb = page.getRGB(x, y);
				final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (r > 150 && g < 100 && b < 100) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
				}
			}
			if (maxX < 0) {
				current = null;
				continue;
			}
			if (current == null) {
				current = new java.awt.Rectangle(minX, y, maxX - minX + 1, 1);
				marks.add(current);
			} else {
				current.add(new java.awt.Rectangle(minX, y, maxX - minX + 1, 1));
			}
		}
		return marks.toArray(new java.awt.Rectangle[marks.size()]);
	}

	private java.awt.image.BufferedImage render() throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				CTISessionHelper.transcodeFile(session, DOCUMENT, "text/html", null);
			} finally {
				session.close();
			}
		}
		try (PDDocument pdf = Loader.loadPDF(out)) {
			return new PDFRenderer(pdf).renderImageWithDPI(0, 144);
		}
	}
}
