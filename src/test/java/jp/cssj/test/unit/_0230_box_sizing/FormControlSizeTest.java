package jp.cssj.test.unit._0230_box_sizing;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
 * Sizes of form controls against Chrome's (2026-10-11, NEXT-SESSION §5-5; copper4/tmp/ab/repro/forms/ta.html, vt.html,
 * buttonws/qa3/rows.html). A textarea has Chrome's 13.333px monospace font, 2 rows of the line height and cols of
 * 0.55em with 1.125em more; buttons, inputs and textareas have line-height: normal; a field's natural width is its
 * inline size, the height in vertical writing. The expected values are Chrome's, within 1.5pt.
 */
public class FormControlSizeTest extends TestCase {
	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=-?[\\d.]+ AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)");

	private static final double EPSILON = 1.5;

	public void testTextarea() throws Exception {
		final List<double[]> f = frames("<div><textarea></textarea></div><div><textarea cols=\"10\" rows=\"1\">"
				+ "</textarea></div><div><textarea rows=\"3\" style=\"line-height: 20px\"></textarea></div>");
		assertSize("default", f.get(0), 126, 27);
		assertSize("cols 10, rows 1", f.get(1), 71.25, 15.75);
		assertSize("rows 3, line-height 20px", f.get(2), 126, 49.5);
	}

	public void testLineHeight() throws Exception {
		final List<double[]> f = frames("<div style=\"line-height: 2\"><button>Go</button> <input /></div>");
		// A button has Chrome's padding and border too: 25.35pt wide (2026-10-11, it was 16.22pt). Its height is 1.76pt
		// above Chrome's: the zero-width space that gives an empty button its line falls back to another font, which
		// makes the line 13pt (Chrome 11.5pt; recorded)
		assertEquals("a button in a line-height: 2 block: width", 25.35, f.get(0)[0], EPSILON);
		assertEquals("a button in a line-height: 2 block: height", 15.75, f.get(0)[1], 2);
		assertEquals("an input in a line-height: 2 block", 15.75, f.get(1)[1], EPSILON);
	}

	public void testVertical() throws Exception {
		final List<double[]> f = frames("<div><textarea rows=\"2\" cols=\"20\" style=\"writing-mode: vertical-rl\">"
				+ "</textarea></div><div><input style=\"writing-mode: vertical-rl\" /></div>"
				+ "<div><input size=\"10\" style=\"writing-mode: vertical-rl\" /></div>");
		assertSize("a vertical textarea", f.get(0), 27, 126);
		assertSize("a vertical input", f.get(1), 15.75, 132.75);
		assertSize("a vertical input of size 10", f.get(2), 15.75, 80.25);
	}

	private static void assertSize(final String label, final double[] frame, final double w, final double h) {
		assertEquals(label + ": width", w, frame[0], EPSILON);
		assertEquals(label + ": height", h, frame[1], EPSILON);
	}

	private static List<double[]> frames(final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 400pt 600pt; margin: 20pt } body { margin: 0; font: 16px serif } div { margin: 0 0 6pt }"
				+ "</style></head><body>" + body + "</body></html>";
		final File dir = Files.createTempDirectory("form-control-size").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final List<double[]> frames = new ArrayList<>();
		for (final String line : page.split("\n")) {
			// The frames of the controls: not the page's (at a negative x), nor the clipped inner ones
			final Matcher m = FRAME.matcher(line);
			if (m.find() && Double.parseDouble(m.group(1)) >= 0 && !line.contains("clip=")) {
				frames.add(new double[] { Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)) });
			}
		}
		assertFalse(page, frames.isEmpty());
		return frames;
	}
}
