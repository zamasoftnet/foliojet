package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * {@code transform-origin} with a percentage on one axis and a length on the other. The y origin was resolved with
 * the unit type of the x origin, so {@code 50% 50pt} on a 100pt high box put the origin 5000pt down (50 times the
 * height) and a half turn threw the box 9900pt off the page; {@code 50pt 50%} put it half a point down.
 *
 * <p>
 * Each document is one black box turned by 180 degrees. A half turn about the origin (ox, oy) of a box at (x, y) is
 * the matrix [-1 0 0 -1 2(x + ox) 2(y + oy)], which the display list shows after the box's frame.
 * </p>
 */
public class TransformOriginMixedUnitsTest extends TestCase {
	private static final String NUMBER = "([-\\d.]+)";

	private static final Pattern FRAME = Pattern.compile("x=" + NUMBER + " y=" + NUMBER
			+ " AbsoluteRectFrame\\[w=" + NUMBER + " h=" + NUMBER + "\\][^\\n]*? tf=\\[" + NUMBER + " " + NUMBER + " "
			+ NUMBER + " " + NUMBER + " " + NUMBER + " " + NUMBER + "\\]");

	/** The reported document: the middle of a 100pt square, written as 50% across and 50pt down. */
	public void testPercentageThenLengthOnSquare() throws Exception {
		assertOrigin(100, 100, "50% 50pt", 50, 50);
	}

	/** A percentage across and a length down: 25% of 100pt, and 15pt. */
	public void testPercentageThenLength() throws Exception {
		assertOrigin(100, 60, "25% 15pt", 25, 15);
	}

	/** A length across and a percentage down: 30pt, and 50% of 60pt. */
	public void testLengthThenPercentage() throws Exception {
		assertOrigin(100, 60, "30pt 50%", 30, 30);
	}

	/** Both percentages, as before. */
	public void testPercentages() throws Exception {
		assertOrigin(100, 60, "25% 50%", 25, 30);
	}

	/** Both lengths, as before. */
	public void testLengths() throws Exception {
		assertOrigin(100, 60, "30pt 15pt", 30, 15);
	}

	private static void assertOrigin(final int width, final int height, final String origin, final double originX,
			final double originY) throws Exception {
		final String dump = convert("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 200pt 200pt; margin: 20pt } body { margin: 0 }" + " div { width: " + width
				+ "pt; height: " + height + "pt; background: black; transform-origin: " + origin
				+ "; transform: rotate(180deg) }</style></head><body><div></div></body></html>");
		final Matcher m = FRAME.matcher(dump);
		assertTrue("no transformed frame in\n" + dump, m.find());
		final double[] v = new double[10];
		for (int i = 0; i < v.length; ++i) {
			v[i] = Double.parseDouble(m.group(i + 1));
		}
		assertEquals("width of the box", width, v[2], 0.01);
		assertEquals("height of the box", height, v[3], 0.01);
		final String message = "transform-origin: " + origin + " in " + m.group();
		assertEquals(message, -1, v[4], 0.01);
		assertEquals(message, 0, v[5], 0.01);
		assertEquals(message, 0, v[6], 0.01);
		assertEquals(message, -1, v[7], 0.01);
		assertEquals(message, 2 * (v[0] + originX), v[8], 0.01);
		assertEquals(message, 2 * (v[1] + originY), v[9], 0.01);
	}

	/** Converts the document and returns the display list dump of its first page. */
	private static String convert(final String html) throws Exception {
		final File dir = Files.createTempDirectory("transform-origin-mixed").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File page = new File(dir, "page-0001.txt");
		assertTrue("no display list of the first page", page.exists());
		return Files.readString(page.toPath(), StandardCharsets.UTF_8);
	}
}
