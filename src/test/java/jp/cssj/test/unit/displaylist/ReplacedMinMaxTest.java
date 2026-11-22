package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A replaced element that is over its maximum on one axis and under its minimum on the other takes the maximum on
 * the one and the minimum on the other (CSS 2.1 section 10.4, the last two rows of the table). The row "wider than
 * max-width and shorter than min-height" gave the height the minimum <em>width</em>, 0 by default: the image was laid
 * out 150pt by 0pt and not painted.
 *
 * <p>
 * Each document is BEFORE, a black inline SVG image and AFTER on a 300pt square page without margins, so that the
 * coordinates of the display list are those of the page.
 * </p>
 */
public class ReplacedMinMaxTest extends TestCase {
	private static final Pattern FRAME = Pattern
			.compile("x=([-\\d.]+) y=([-\\d.]+) AbsoluteRectFrame\\[w=([-\\d.]+) h=([-\\d.]+)\\]");

	private static final Pattern AFTER = Pattern.compile("x=([-\\d.]+) y=([-\\d.]+) Text\\[\"AFTER\"");

	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page { size: 300pt 300pt; margin: 0 } body { margin: 0; font: 10pt/12pt serif } p { margin: 0 }"
			+ "</style></head><body>";

	private record Result(String dump, byte[] pdf) {
	}

	/** 200pt by 20pt with max-width 150pt and min-height 30pt: 150pt by 30pt, painted. */
	public void testWiderThanMaxAndShorterThanMin() throws Exception {
		assertImage(image(200, 20, "max-width: 150pt; min-height: 30pt"), 150, 30);
	}

	/** The transpose, 20pt by 200pt with min-width 30pt and max-height 150pt: 30pt by 150pt, painted. */
	public void testNarrowerThanMinAndTallerThanMax() throws Exception {
		assertImage(image(20, 200, "min-width: 30pt; max-height: 150pt"), 30, 150);
	}

	private static String image(final int width, final int height, final String limits) {
		return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width + "\" height=\"" + height
				+ "\" viewBox=\"0 0 " + width + " " + height + "\" style=\"width: " + width + "pt; height: " + height
				+ "pt; " + limits + "\"><rect width=\"" + width + "\" height=\"" + height
				+ "\" fill=\"black\"/></svg>";
	}

	private static void assertImage(final String image, final double width, final double height) throws Exception {
		final Result result = convert("<p>BEFORE</p>" + image + "<p>AFTER</p>");
		final Matcher frame = FRAME.matcher(result.dump);
		assertTrue("no frame of the image in\n" + result.dump, frame.find());
		final double x = Double.parseDouble(frame.group(1)), y = Double.parseDouble(frame.group(2));
		assertEquals("width of the image", width, Double.parseDouble(frame.group(3)), 0.01);
		assertEquals("height of the image", height, Double.parseDouble(frame.group(4)), 0.01);

		final Matcher after = AFTER.matcher(result.dump);
		assertTrue("AFTER is not laid out", after.find());
		assertTrue("AFTER at y=" + after.group(2) + " is not below the image, which ends at " + (y + height),
				Double.parseDouble(after.group(2)) >= y + height - 0.01);

		// One pixel per point: the middle of the image is black, the page beside it is not.
		try (PDDocument doc = Loader.loadPDF(result.pdf)) {
			final BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 72);
			assertTrue("the image is not painted", isDark(page.getRGB((int) (x + width / 2), (int) (y + height / 2))));
			assertFalse("the page beside the image is painted",
					isDark(page.getRGB((int) (x + width + 20), (int) (y + height / 2))));
		}
	}

	private static boolean isDark(final int rgb) {
		return ((rgb >> 16) & 0xFF) < 80 && ((rgb >> 8) & 0xFF) < 80 && (rgb & 0xFF) < 80;
	}

	/** Converts the document and returns the display list dump of its first page and the PDF. */
	private static Result convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("replaced-min-max").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), HEAD + body + "</body></html>", StandardCharsets.UTF_8);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File page = new File(dir, "page-0001.txt");
		assertTrue("no display list of the first page", page.exists());
		return new Result(Files.readString(page.toPath(), StandardCharsets.UTF_8), out.toByteArray());
	}
}
