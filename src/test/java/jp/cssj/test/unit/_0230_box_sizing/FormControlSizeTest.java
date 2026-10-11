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

	/**
	 * field-sizing: content sizes the control to its content (2026-10-11, NEXT-SESSION §5-6; repro/fs/fs2.html): a
	 * textarea with "ab" is 24.22 x 21.14pt, one with two lines and rows="20" 18.87 x 34.78pt in Chrome; an input with
	 * "hello world" is narrower than its natural 132.75pt (Chrome 53.8pt).
	 */
	public void testFieldSizingContent() throws Exception {
		final String textarea = "style=\"field-sizing: content; font-family: monospace; font-size: 13px; line-height: 1.4;"
				+ " padding: 4px 8px; border: 1px solid; box-sizing: border-box; white-space: pre\"";
		final List<double[]> f = frames("<div><textarea " + textarea + ">ab</textarea></div><div><textarea rows=\"20\" "
				+ textarea + ">a\nb</textarea></div><div><input style=\"field-sizing: content\" value=\"hello world\" />"
				+ "</div>");
		assertSize("a textarea with ab", f.get(0), 24.22, 21.14);
		assertSize("a textarea of two lines, rows 20", f.get(1), 18.87, 34.78);
		assertTrue("an input with hello world: " + f.get(2)[0], f.get(2)[0] > 40 && f.get(2)[0] < 60);
	}

	/**
	 * What field-sizing: content leaves (repro/fs/fs3.html in Chrome): a range keeps its width, 96.75pt; an empty
	 * textarea a line, 15.75pt high (5.25pt wide in Chrome with its 1.5pt padding and 0.75pt caret, Copper's padding is
	 * 1pt and has no caret).
	 */
	public void testFieldSizingLeaves() throws Exception {
		final List<double[]> f = frames("<div><input type=\"range\" style=\"field-sizing: content\" /></div>"
				+ "<div><textarea style=\"field-sizing: content\"></textarea></div>");
		assertEquals("a range under field-sizing: content", 96.75, f.get(0)[0], EPSILON);
		assertEquals("an empty textarea under field-sizing: content: height", 15.75, f.get(1)[1], EPSILON);
		assertTrue("an empty textarea under field-sizing: content: width " + f.get(1)[0], f.get(1)[0] < 6);
	}

	/**
	 * inline-size and block-size auto give a control its natural size, as width and height auto do, in vertical writing
	 * too (repro/fs/fs3.html, fs4.html in Chrome): rows 3 146.25 x 45pt, cols 10 78.75 x 30pt, and 45 x 146.25pt,
	 * 30 x 78.75pt vertical (they were 0pt along the logical axis).
	 */
	public void testLogicalAuto() throws Exception {
		final String z = "font: 16px/20px monospace; padding: 0; border: 0";
		final String v = "; writing-mode: vertical-rl";
		final List<double[]> f = frames("<div><textarea rows=\"3\" style=\"block-size: auto; " + z + "\"></textarea></div>"
				+ "<div><textarea cols=\"10\" style=\"inline-size: auto; " + z + "\"></textarea></div>"
				+ "<div><textarea rows=\"3\" style=\"block-size: auto; " + z + v + "\"></textarea></div>"
				+ "<div><textarea cols=\"10\" style=\"inline-size: auto; " + z + v + "\"></textarea></div>");
		assertSize("rows 3, block-size: auto", f.get(0), 146.25, 45);
		assertSize("cols 10, inline-size: auto", f.get(1), 78.75, 30);
		assertSize("rows 3, block-size: auto, vertical", f.get(2), 45, 146.25);
		assertSize("cols 10, inline-size: auto, vertical", f.get(3), 30, 78.75);
	}

	/**
	 * A button is as wide as its content in the flow whatever its display, and stretches in a flex column, a grid and
	 * absolutely positioned (2026-10-11, repro/btn/bb.html in Chrome: block 56.48pt, its content's, and 225pt, the
	 * container's, in the three). A child's width: inherit gets auto, not fit-content (codex).
	 */
	public void testBlockButton() throws Exception {
		final String box = "<div style=\"width: 300px; position: relative; height: 30px";
		final List<double[]> f = frames(box + "\"><button>Language</button></div>"
				+ box + "\"><button style=\"display: block\">Language</button></div>"
				+ box + "; display: flex; flex-direction: column\"><button>Language</button></div>"
				+ box + "; display: grid\"><button>Language</button></div>"
				+ box + "\"><button style=\"position: absolute; left: 0; right: 0\">Language</button></div>"
				+ box + "\"><button style=\"min-width: 100px; padding: 0; border: 0\"><span style=\"display: block;"
				+ " width: inherit; height: 10px; background: red\"></span></button></div>");
		assertEquals("display: block, as inline-block", f.get(0)[0], f.get(1)[0], 0.01);
		assertTrue("display: block, narrower than the line: " + f.get(1)[0], f.get(1)[0] < 100);
		assertEquals("in a flex column", 225, f.get(2)[0], EPSILON);
		assertEquals("in a grid", 225, f.get(3)[0], EPSILON);
		assertEquals("absolutely positioned between left and right", 225, f.get(4)[0], EPSILON);
		assertEquals("a child's width: inherit", 75, f.get(f.size() - 1)[0], EPSILON);
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
