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
 * An element in a neutral flex wrapper (a flex container as a flex item, say) leaves its line-axis min and max sizes to
 * the wrapper, as it does its size (2026-10-10; the widths are Chrome's). Resolved again against the wrapper, a
 * percentage counted twice: quarto-book's logo box, max-width: calc(100% - 115px), came out 31.96pt where Chrome
 * makes it 118.2pt, and the logo was cut. Nor does the element's own max-width cap the content its intrinsic min-width
 * takes, and min-width wins over max-width, as everywhere. The wrapper's automatic minimum follows the element's
 * overflow. The row is 360pt wide.
 */
public class FlexWrappedElementMinMaxTest extends TestCase {
	private static final Pattern OUTLINE = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=[\\d.]+\\] outline\\[");

	private static final Pattern TAIL = Pattern.compile("x=([\\d.]+) y=[\\d.]+ AbsoluteRectFrame\\[w=20.00 h=10.00\\]");

	public void testPercentageMaxWidth() throws Exception {
		// 50% of the row: Chrome 180pt (it was 90, 50% of the 180pt wrapper)
		assertEquals(180, item("max-width: 50%", "<i></i>"), 0.1);
	}

	public void testCalcMaxWidth() throws Exception {
		// 360pt less 115px: Chrome 273.8pt (it was 187.5)
		assertEquals(273.75, item("max-width: calc(100% - 115px)", "<i></i>"), 0.1);
	}

	public void testPercentageMinWidth() throws Exception {
		assertEquals(180, item("min-width: 50%", "A"), 0.1);
	}

	public void testBorderBoxMinAndMax() throws Exception {
		// The wrapper takes them in the element's box-sizing: Chrome 75pt both
		assertEquals(75, item("min-width: 100px; padding: 0 10px; box-sizing: border-box", "A"), 0.1);
		assertEquals(75, item("max-width: 100px; padding: 0 10px; box-sizing: border-box", "<i></i>"), 0.1);
	}

	public void testMinWidthWinsOverMaxWidth() throws Exception {
		// The next item follows at 90pt, as in Chrome (it was 30: the item's main size let max-width win)
		assertEquals(90, layout("min-width: 90pt; max-width: 30pt", "<i></i>")[1], 0.1);
	}

	public void testIntrinsicMinWidthWinsOverMaxWidth() throws Exception {
		// min-width: max-content is the content's, not capped by the element's own max-width: Chrome 90pt, and the next
		// item after it (the element came out 45pt, the next item at 30pt over it)
		final double[] layout = layout("min-width: max-content; max-width: 30pt", "<b></b><b></b>");
		assertEquals(90, layout[0], 0.1);
		assertEquals(90, layout[1], 0.1);
	}

	public void testContentBoxMinWidthTakesPaddingAndBorder() throws Exception {
		// The wrapper's min-width counts the element's padding and border: Chrome 90pt, and the next item after it (the
		// next item came at 75pt, over the element)
		final double[] layout = layout("flex: 0 1 0; min-width: 75pt; padding: 0 7.5pt; height: 8pt", "");
		assertEquals(90, layout[0], 0.1);
		assertEquals(90, layout[1], 0.1);
	}

	public void testOverflowClipKeepsAutomaticMinimum() throws Exception {
		// overflow: clip does not make a scroll container: Chrome keeps the item at its content's 600pt, where hidden
		// lets it shrink to the 340pt the row leaves
		assertEquals(600, layout("overflow: clip", "<i></i><i></i>")[1], 0.1);
		assertEquals(340, layout("overflow: hidden", "<i></i><i></i>")[1], 0.1);
	}

	public void testOverflowHiddenKeepsPaddingAndBorder() throws Exception {
		// With no automatic minimum, the element still takes its padding: Chrome puts the next item at 30pt (it was 0,
		// over the element)
		assertEquals(30, layout("flex: 0 1 0; overflow: hidden; padding: 0 15pt; height: 8pt", "")[1], 0.1);
	}

	public void testIntrinsicMaxWidth() throws Exception {
		// max-width: min-content of a wrapping row: Chrome 45pt, one box per line (it was 90)
		assertEquals(45, layout("flex-wrap: wrap; max-width: min-content", "<b></b><b></b>")[1], 0.1);
	}

	public void testInheritedClipComputesToHidden() throws Exception {
		// clip computes to hidden beside a hidden axis, and so inherits: Chrome lets the item shrink to 60pt
		final Matcher m = TAIL.matcher(convert("<div style=\"display: flex; width: 80pt; overflow-x: clip; "
				+ "overflow-y: hidden\"><div style=\"overflow-x: inherit; overflow-y: visible; white-space: nowrap\"><i></i>"
				+ "</div><div style=\"flex: none; width: 20pt; height: 10pt; background: #000\"></div></div>"));
		assertTrue(m.find());
		assertEquals(60, Double.parseDouble(m.group(1)), 0.1);
	}

	public void testColumnItemKeepsKeywordWidth() throws Exception {
		// width: max-content in a column: Chrome 45pt, not the column's 200pt
		final Matcher m = OUTLINE.matcher(convert("<div style=\"display: flex; flex-direction: column; width: 200pt; "
				+ "height: 100pt\"><div style=\"display: flex; width: max-content; outline: 1px solid blue\"><b></b></div>"
				+ "</div>"));
		assertTrue(m.find());
		assertEquals(45, Double.parseDouble(m.group(1)), 0.1);
	}

	/** The outlined width of a display: flex item with the given style, in a 360pt row ending with a 20pt box. */
	private static double item(final String style, final String content) throws Exception {
		return layout(style, content)[0];
	}

	/** {the outlined width, the x of the 20pt box after it} for a display: flex item in a 360pt row. */
	private static double[] layout(final String style, final String content) throws Exception {
		final String page = convert("<div style=\"display: flex; width: 360pt\"><div style=\"display: flex; "
				+ "outline: 1px solid blue; " + style + "\">" + content + "</div><div style=\"flex: none; width: 20pt; "
				+ "height: 10pt; background: #000\"></div></div>");
		final List<Double> widths = new ArrayList<>();
		final Matcher m = OUTLINE.matcher(page);
		while (m.find()) {
			widths.add(Double.parseDouble(m.group(1)));
		}
		assertEquals(widths.toString(), 1, widths.size());
		final Matcher tail = TAIL.matcher(page);
		assertTrue(page, tail.find());
		return new double[] { widths.get(0), Double.parseDouble(tail.group(1)) };
	}

	private static String convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("flex-wrapped-element-min-max").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 400pt 400pt; margin: 10pt } body { margin: 0; font: 8pt/1.2 serif }"
				+ " i { display: inline-block; width: 300pt; height: 8pt; background: #ccc }"
				+ " b { display: inline-block; width: 45pt; height: 8pt; background: #ccc }"
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
