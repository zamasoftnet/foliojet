package jp.cssj.test.unit._0510_flex;

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
 * A row flex item's contributions to its container's line (2026-10-10, CSS Flexbox §9.9.1; the widths are Chrome's).
 * A button laid out as a flex container sits in a wrapper item, which now takes its {@code min-width: max-content} too:
 * the wrapper shrank and the next button overlapped it. Min-width and max-width clamp an item's contributions, and a
 * definite flex base size caps them when the item cannot grow and floors them when it cannot shrink: the flex basis
 * and the width only floored them, and min-width and max-width did not count.
 */
public class FlexMinWidthContributionTest extends TestCase {
	private static final Pattern FRAME = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern OUTLINE = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=[\\d.]+\\] outline\\[");

	public void testFlexButtonsDoNotOverlap() throws Exception {
		final List<double[]> frames = frames(convert("<div style=\"width: 150px\"><div style=\"display: flex; gap: 1rem\">"
				+ "<button style=\"display: flex; min-width: max-content\">A1</button>"
				+ "<button style=\"display: flex; min-width: max-content\">Medium (default)</button></div></div>"));
		assertEquals(describe(frames), 2, frames.size());
		// The second starts after the first and the 12pt gap, as in Chrome: 40.2pt (it was 34.4, inside the first)
		assertEquals(frames.get(0)[0] + frames.get(0)[2] + 12, frames.get(1)[0], 0.1);
	}

	public void testNowrapRowCountsItsMinWidths() throws Exception {
		final List<double[]> frames = frames(convert("<div style=\"display: flex\"><div style=\"width: 300px; flex: none\">N</div>"
				+ "<div class=\"m\" style=\"width: 100%; background: #efe\"><div style=\"display: flex; gap: 16px\">"
				+ "<div class=\"b\">Small size</div><div class=\"b\">Medium default size</div><div class=\"b\">Large size</div>"
				+ "</div></div><div style=\"min-width: 280px\">T</div></div>"));
		// The main column holds the row: 349.6px (262.2pt) in Chrome; it was 214.6px with the row out of it
		assertEquals(describe(frames), 262.2, frames.get(0)[2], 1);
	}

	/** min-width: fit-content asks the min-content of the min-content contribution, not the max-content. */
	public void testFitContentMinWidthAsksMinContent() throws Exception {
		// Chrome 60px; as the max-content it was 120px, wider than the words need
		assertEquals(45, squeezed("min-width: fit-content"), 0.1);
	}

	public void testFlexBaseSizeClampsContributions() throws Exception {
		// An item that cannot grow asks at most its basis: Chrome 20px (it was 60px)
		assertEquals(15, squeezed("flex: 0 0 20px; overflow: hidden"), 0.1);
		// ...but no less than its automatic minimum while its overflow is visible: Chrome 60px
		assertEquals(45, squeezed("flex: 0 0 20px"), 0.1);
		// A basis that can shrink is no floor: Chrome 60px (it was 100px)
		assertEquals(45, squeezed("flex: 1 1 100px"), 0.1);
		// Nor is a width beyond the content's: Chrome 30px, the automatic minimum within the width (it was 60px)
		assertEquals(22.5, squeezed("width: 30px"), 0.1);
	}

	public void testMaxWidthCapsContributions() throws Exception {
		// Chrome 40px (it was 60px)
		assertEquals(30, squeezed("max-width: 40px"), 0.1);
	}

	public void testMaxContentContribution() throws Exception {
		// A float sizes the row by its max-content: 130px in Chrome, the content and the 10px tail (it was 310px)
		assertEquals(97.5, outline(convert(STYLE + "<div class=\"m\" style=\"float: left\"><div style=\"display: flex; "
				+ "align-items: flex-start\"><div style=\"flex: 1 1 300px\"><i></i> <i></i></div><div style=\"flex: none; "
				+ "width: 10px; height: 10px; background: #000\"></div></div></div>")), 0.1);
	}

	/**
	 * An element in a neutral wrapper (a flex container in a flex row) keeps its padding and margin in its
	 * contributions: the wrapper takes its width but not its frame, which stays with it inside the wrapper.
	 */
	public void testWrappedElementKeepsItsFrame() throws Exception {
		final String item = "display: flex; flex-wrap: wrap; flex: 1 1 0; width: 60px; padding: 0 10px; margin: 0 5px; ";
		// 60px wide, 10px padding and 5px margin on each side: 90px in Chrome (it was 60 with the frame left out)
		assertEquals(67.5, squeezed(item + "box-sizing: content-box"), 0.1);
		// The padding in the 60px: 70px in Chrome
		assertEquals(52.5, squeezed(item + "box-sizing: border-box"), 0.1);
	}

	/** A wrapping row's min-content takes its items' contributions without the flex basis clamp, as Chrome does. */
	public void testWrappingRowTakesNoBasisClamp() throws Exception {
		// Chrome 60px both (they were 20 and 200)
		assertEquals(45, squeezed("flex-wrap: wrap", "flex: 0 1 20px; min-width: 0"), 0.1);
		assertEquals(45, squeezed("flex-wrap: wrap", "flex: 0 0 200px"), 0.1);
	}

	public void testWidthKeyword() throws Exception {
		// Chrome 120px (it was 60, the keyword read as auto)
		assertEquals(90, squeezed("width: max-content; flex: none"), 0.1);
	}

	private static final String STYLE = "<style>.m { outline: 1px solid red; font-size: 0 } i { display: inline-block; "
			+ "width: 60px; height: 10px; background: silver }</style>";

	private static double squeezed(final String itemStyle) throws Exception {
		return squeezed("", itemStyle);
	}

	/**
	 * The width of a flex item squeezed to its automatic minimum, the min-content of the row it holds: a row (with the
	 * given style) of one item with the given style holding two 60px boxes.
	 */
	private static double squeezed(final String rowStyle, final String itemStyle) throws Exception {
		return outline(convert(STYLE + "<div style=\"display: flex; width: 170px\"><div class=\"m\" style=\"width: 100%\">"
				+ "<div style=\"display: flex; align-items: flex-start; " + rowStyle + "\"><div style=\"" + itemStyle
				+ "\"><i></i> <i></i></div></div></div><div style=\"flex: none; width: 150px; height: 5px; "
				+ "background: #000\"></div></div>"));
	}

	private static double outline(final String page) {
		final Matcher m = OUTLINE.matcher(page);
		assertTrue(page, m.find());
		return Double.parseDouble(m.group(1));
	}

	private static String describe(final List<double[]> frames) {
		final StringBuilder sb = new StringBuilder();
		for (final double[] f : frames) {
			sb.append(String.format(" [x=%.2f w=%.2f]", f[0], f[2]));
		}
		return sb.toString();
	}

	private static List<double[]> frames(final String page) {
		final List<double[]> frames = new ArrayList<>();
		final Matcher m = FRAME.matcher(page);
		while (m.find()) {
			final double w = Double.parseDouble(m.group(3));
			if (w < 500) {
				frames.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), w,
						Double.parseDouble(m.group(4)) });
			}
		}
		return frames;
	}

	/** The display list of the first page. */
	private static String convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("flex-min-width-contribution").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: A4; margin: 36pt } body { margin: 0; font: 16px/1.5 serif } * { box-sizing: border-box }"
				+ " button { padding: 0 8px; border: 1px solid #000; font: inherit; background: #eee; white-space: nowrap }"
				+ " .b { padding: 0 8px; border: 1px solid #000; background: #eee; min-width: max-content }"
				+ "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
	}
}
