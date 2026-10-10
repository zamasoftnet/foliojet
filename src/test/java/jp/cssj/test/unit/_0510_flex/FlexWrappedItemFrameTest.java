package jp.cssj.test.unit._0510_flex;

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
 * The item the flex algorithm sizes for a neutral wrapper is the element it holds (2026-10-11; the values are Chrome's):
 * its margins outside it, a replaced element's padding and border outside its flex basis and inside its shrink. And
 * the auto margins of a column's items along the main axis take its free space. Positions are of the 10pt black box
 * after the item, the page area 10pt from the page edge.
 */
public class FlexWrappedItemFrameTest extends TestCase {
	private static final Pattern BLACK = Pattern.compile("x=([\\d.]+) y=([\\d.]+) AbsoluteRectFrame\\[w=7.50 h=7.50\\]");

	private static final String IMAGE = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' "
			+ "height='80'%3E%3Crect width='160' height='80' fill='red'/%3E%3C/svg%3E";

	public void testWrappedElementKeepsItsMargins() throws Exception {
		// An 18pt flex container with margins 6pt and 3pt after a 45pt box: Chrome puts the next box at 72pt (it was 63,
		// the element squeezed to 9pt after its margin)
		assertEquals(72, black("<div style=\"display: flex; width: 200px\"><div style=\"flex: none; width: 60px; "
				+ "height: 10px; background: #ccc\"></div><div style=\"display: flex; width: 24px; height: 24px; "
				+ "margin: 0 4px 0 8px; border: 1px solid; box-sizing: border-box\">/</div>" + BOX + "</div>")[0], 0.1);
	}

	public void testReplacedBasisTakesPaddingOutside() throws Exception {
		// flex: 0 0 45pt of a content-box image with padding: 0 7.5pt is its content box: Chrome puts the next box at
		// 60pt (it was 45)
		assertEquals(60, black("<div style=\"display: flex; width: 150pt; align-items: flex-start\"><img src=\"" + IMAGE
				+ "\" style=\"display: block; flex: 0 0 45pt; min-width: 0; padding: 0 7.5pt\">" + BOX + "</div>")[0],
				0.1);
	}

	public void testReplacedShrinkScalesByContentBox() throws Exception {
		// Two items of 45pt content shrink alike in a 75pt row, the image's padding outside: Chrome puts the second at 45pt
		// (it was 42.86, the padding weighing in the shrink)
		assertEquals(45, black("<div style=\"display: flex; width: 75pt; align-items: flex-start\"><img src=\"" + IMAGE
				+ "\" style=\"display: block; width: 45pt; min-width: 0; padding: 0 7.5pt\"><div style=\"width: 45pt; "
				+ "min-width: 0\">" + BOX + "</div></div>")[0], 0.1);
	}

	public void testColumnMainAxisAutoMargin() throws Exception {
		// margin-top: auto in a 75pt-high column takes its free space: Chrome puts the image and the box at the bottom,
		// the box at 67.5pt (it was 15, under the image at the top)
		assertEquals(67.5, black("<div style=\"display: flex; flex-direction: column; width: 150pt; height: 75pt\">"
				+ "<img src=\"" + IMAGE + "\" style=\"display: block; width: 30pt; margin-top: auto\">" + BOX + "</div>")[1],
				0.1);
	}

	public void testVerticalRlAutoMarginsStartAtTheRight() throws Exception {
		// vertical-rl starts its page axis at the right, margin-left: auto being the end side: Chrome keeps the box at the
		// right of a column and puts it there in a row too (it was at the left of a row)
		for (final String direction : new String[] { "column", "row" }) {
			assertEquals(direction, 67.5, black("<div style=\"display: flex; flex-direction: " + direction
					+ "; writing-mode: vertical-rl; width: 75pt; height: 75pt\"><div style=\"flex: none; width: 10px; "
					+ "height: 10px; margin-left: auto; background: #000\"></div></div>")[0], 0.1);
		}
	}

	public void testWrappedPercentMarginAgainstContainer() throws Exception {
		// margin-left: 10% of an 18pt flex container in a 150pt row is 15pt, the element keeping its width: Chrome puts
		// the next box at 33pt (it was 18, the margin taken against the wrapper)
		assertEquals(33, black("<div style=\"display: flex; width: 150pt; align-items: flex-start\"><div style=\"display: "
				+ "flex; flex: none; width: 18pt; height: 15pt; margin-left: 10%; box-sizing: border-box\"></div>" + BOX
				+ "</div>")[0], 0.1);
	}

	public void testReplacedBasisContribution() throws Exception {
		// A content-box image with flex: 0 0 45pt and padding: 0 7.5pt contributes 60pt, as it is laid out: Chrome puts
		// the box after its max-content container at 60pt (it was 45)
		assertEquals(60, black("<div style=\"display: flex\"><div style=\"display: flex; width: max-content\"><img src=\""
				+ IMAGE + "\" style=\"display: block; flex: 0 0 45pt; min-width: 0; height: 7.5pt; padding: 0 7.5pt\">"
				+ "</div>" + BOX + "</div>")[0], 0.1);
	}

	public void testPercentMarginOutsideMeasuredContent() throws Exception {
		// margin-left: 10% of a flex container sized by its 30pt content in a 150pt row: Chrome puts the next box at 45pt
		// (it was 30, the element squeezed by its margin)
		assertEquals(45, black("<div style=\"display: flex; width: 150pt; align-items: flex-start\"><div style=\"display: "
				+ "flex; flex: none; margin-left: 10%\"><div style=\"flex: none; width: 30pt; height: 7.5pt\"></div></div>"
				+ BOX + "</div>")[0], 0.1);
	}

	public void testPercentMarginWithIntrinsicMinWidth() throws Exception {
		// As testWrappedPercentMarginAgainstContainer with min-width: max-content, which lays the element out by
		// shrink-to-fit: Chrome puts the next box at 33pt
		assertEquals(33, black("<div style=\"display: flex; width: 150pt; align-items: flex-start\"><div style=\"display: "
				+ "flex; flex: none; width: 18pt; height: 15pt; min-width: max-content; margin-left: 10%; box-sizing: "
				+ "border-box\"><div style=\"flex: none; width: 7.5pt; height: 7.5pt\"></div></div>" + BOX + "</div>")[0],
				0.1);
	}

	public void testColumnCrossPercentMargin() throws Exception {
		// margin-left: 10% of a flex container sized by its content in a 150pt column: it keeps its 37.5pt content after
		// its 15pt margin, Chrome puts its last box at 45pt (it was 30, the element squeezed by its margin)
		assertEquals(45, black("<div style=\"display: flex; flex-direction: column; width: 150pt; height: 75pt; "
				+ "align-items: flex-start\"><div style=\"display: flex; flex: none; margin-left: 10%; justify-content: "
				+ "flex-end\"><div style=\"flex: none; width: 30pt; height: 7.5pt\"></div>" + BOX + "</div></div>")[0],
				0.1);
	}

	private static final String BOX = "<div style=\"flex: none; width: 10px; height: 10px; background: #000\"></div>";

	/** {x, y} of the 10px black box. */
	private static double[] black(final String body) throws Exception {
		final Matcher m = BLACK.matcher(convert(body));
		assertTrue(m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}

	private static String convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("flex-wrapped-item-frame").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 7.5pt } body { margin: 0; font: 8pt/1.2 serif }"
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
