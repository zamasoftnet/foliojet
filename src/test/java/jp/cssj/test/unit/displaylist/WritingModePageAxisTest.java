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
 * Page-axis sizes that came out differently in vertical and in horizontal writing (2026-10-28, the sweep's
 * writing-mode mirror check, seeds 45250557 to 45251095).
 *
 * <ul>
 * <li>A box in vertical writing kept a page-axis size its content no longer had: {@code FlowBlockBox.setPageAxis}
 * only grows there, a page break grew the open boxes to the cut line before it knew it would not break, and a child
 * that came out smaller (three columns balanced from 170pt to 65.5pt) left the box at the earlier size. What followed
 * moved on by the difference.</li>
 * <li>A box in horizontal writing in an orthogonal flow did not hold its floats: the end of its last line set it
 * back to the line, while one in vertical writing held them.</li>
 * </ul>
 */
public class WritingModePageAxisTest extends TestCase {
	/** Three columns of a bordered column flex, 170pt before they are balanced (the page is 168pt): mirror seed 45250557. */
	private static final String COLUMNS_H = "<div style=\"column-count: 3; background: #ddd\"><div style=\"display: flex;"
			+ " flex-direction: column; border-style: solid\"><div style=\"flex: 1 1 42pt; padding-top: 6pt;"
			+ " padding-block-end: 3pt\">T53 T54 T55 T56 T57 T58</div><div style=\"flex: 1 1 57pt; padding-block-end: 3pt;"
			+ " border-style: solid; border-bottom-width: 4pt\">T59 T60 T61 T62</div><div style=\"margin-block-end: 11pt;"
			+ " padding-block-start: 6pt\">T64 T65 T66 T67 T68</div><div style=\"margin-bottom: 10pt\">T69 T70</div></div></div>";

	/** {@link #COLUMNS_H} for vertical-rl: top for right, bottom for left. */
	private static final String COLUMNS_V = COLUMNS_H.replace("padding-top", "padding-right")
			.replace("border-bottom-width", "border-block-end-width").replace("margin-bottom", "margin-left");

	public WritingModePageAxisTest(final String name) {
		super(name);
	}

	/** The background of balanced columns is as thick in vertical writing as in horizontal writing (65.5pt). */
	public void testBoxAroundBalancedColumnsShrinks() throws Exception {
		final double[] h = pos("376pt 183pt; margin: 0 10pt 15pt 10pt", "horizontal-tb", COLUMNS_H, null);
		final double[] v = pos("183pt 376pt; margin: 10pt 0 10pt 15pt", "vertical-rl", COLUMNS_V, null);
		assertTrue("the columns are balanced: " + h[1], h[1] < 100);
		assertEquals(h[1], v[0], 0.01);
	}

	/**
	 * A box of the other writing mode holds its float as overflow: hidden does, in both directions: its frame is 100pt
	 * along its block axis, where the float ends (the line of text alone is 11pt).
	 */
	public void testOrthogonalBoxHoldsItsFloats() throws Exception {
		final double[] h = pos("300pt 300pt; margin: 0", "horizontal-tb", "<div style=\"writing-mode: vertical-lr;"
				+ " height: 120pt; background: #ddd\"><div style=\"float: left; height: 50pt; width: 100pt\">F1</div>T1</div>", null);
		assertEquals(100, h[0], 0.01);
		final double[] v = pos("300pt 300pt; margin: 0", "vertical-lr", "<div style=\"writing-mode: horizontal-tb;"
				+ " width: 120pt; background: #ddd\"><div style=\"float: left; width: 50pt; height: 100pt\">F1</div>T1</div>", null);
		assertEquals(100, v[1], 0.01);
	}

	/** A box of {@code width: max-content} and {@code overflow: hidden} holds its float: it clipped it to the line. */
	public void testIntrinsicSizeBoxWithOverflowHoldsItsFloats() throws Exception {
		final double[] h = pos("300pt 300pt; margin: 0", "horizontal-tb", "<div style=\"width: max-content;"
				+ " overflow: hidden; background: #ddd\"><div style=\"float: left; width: 50pt; height: 100pt\">F1</div>"
				+ "T1</div>", null);
		assertEquals(100, h[1], 0.01);
	}

	/**
	 * Collapsed borders in vertical-lr give each cell the half of the border on its own side (mirror seed 45250368): the
	 * cells took the vertical-rl sides, so the content of the second row began 1pt early, the half of the 2pt border
	 * before it.
	 */
	public void testCollapsedBorderHalvesInVerticalLr() throws Exception {
		final String table = "<table style=\"border-collapse: collapse\"><tr><td style=\"border: solid 2pt\"></td></tr>"
				+ "<tr><th>T99</th></tr></table>";
		final double[] h = pos("186pt 276pt; margin: 5pt 5pt 15pt 0", "horizontal-tb", table, "T99");
		final double[] v = pos("276pt 186pt; margin: 0 15pt 5pt 5pt", "vertical-lr", table, "T99");
		assertEquals(h[1], v[0], 0.01);
		assertEquals(h[0], v[1], 0.01);
	}

	/** The position of {@code token}, or with {@code null} the size of the first frame. */
	private static double[] pos(final String page, final String writingMode, final String body, final String token)
			throws Exception {
		final File dir = Files.createTempDirectory("writing-mode-page-axis").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: " + page
				+ " } html { writing-mode: " + writingMode + " } body { margin: 0; font: 9pt/11pt serif }"
				+ " p { margin: 0 }</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String text = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final Matcher m = (token == null ? Pattern.compile("AbsoluteRectFrame\\[w=(-?[\\d.]+) h=(-?[\\d.]+)")
				: Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"" + token + "\"")).matcher(text);
		assertTrue(text, m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}
}
