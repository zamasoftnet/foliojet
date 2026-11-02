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
 * Percentages of a page-axis size (2026-11-02, strict seed 56646882, all of its text off the paper). A flex or grid item
 * took its specified size only after its content, so a percentage in it resolved against 0; so did a percentage in an
 * absolutely positioned box, and a percentage max-height in a parent of auto height. The expected values are Chrome's.
 */
public class PercentPageSizeBasisTest extends TestCase {
	private static final String AFTER = "<div>T2</div>";

	private static String item(final String display, final String style) {
		return "<div style=\"display: " + display + "\"><div style=\"height: 100pt\"><div style=\"" + style + "\">T1</div>"
				+ AFTER + "</div></div>";
	}

	private static String absolute(final String own, final String child) {
		return "<div style=\"position: absolute; top: 0; left: 0; width: 100pt; " + own + "\"><div style=\"" + child + "\">"
				+ "T1</div>" + AFTER + "</div>";
	}

	public void testHeightInAGridItem() throws Exception {
		assertEquals(50, pos("", item("grid", "height: 50%"), "T2")[1], 0.01);
	}

	public void testHeightInAFlexItem() throws Exception {
		assertEquals(50, pos("", item("flex", "height: 50%"), "T2")[1], 0.01);
	}

	public void testMaxHeightInAnItem() throws Exception {
		assertEquals(50, pos("", item("grid", "height: 80pt; max-height: 50%"), "T2")[1], 0.01);
	}

	/** A percentage width in vertical writing, where the width is the page axis. */
	public void testWidthInAVerticalItem() throws Exception {
		assertEquals(50, pos("writing-mode: vertical-lr", "<div style=\"display: grid\"><div style=\"width: 100pt\"><div "
				+ "style=\"width: 50%\">T1</div><div>T2</div></div></div>", "T2")[0], 0.01);
	}

	/** The seed: the box of the opposite block direction came out 0 wide, and its lines went off the left of the paper. */
	public void testOppositeBlockDirectionInAnItem() throws Exception {
		assertEquals(0, pos("writing-mode: vertical-lr", "<div style=\"display: grid\"><div style=\"width: 48pt\"><div style=\""
				+ "writing-mode: vertical-rl; max-width: 90%\">T1</div></div></div>", "T1")[0], 0.01);
	}

	/** The basis is the size the item of a column comes out, not the one it declares. */
	public void testHeightInAShrunkColumnItem() throws Exception {
		assertEquals(15, pos("", "<div style=\"display: flex; flex-direction: column; height: 30pt\"><div style=\"flex: 1 1 "
				+ "auto; height: 80pt; min-height: 0\"><div style=\"height: 50%\">T1</div>" + AFTER + "</div></div>", "T2")[1],
				0.01);
	}

	/** A main size from the content is no basis: it was measured with the percentage auto (codex 2026-11-02). */
	public void testContentBasisOfAColumnItem() throws Exception {
		final String body = "<div style=\"display: flex; flex-direction: column-reverse\"><div style=\"flex: 0 0 content; "
				+ "height: 100pt; min-height: 0\"><div style=\"height: 100%\"></div><div style=\"height: 20pt\">T1</div></div>"
				+ "</div><div>T2</div>";
		assertEquals(0, pos("", body, "T1")[1], 0.01);
		assertEquals(20, pos("", body, "T2")[1], 0.01);
	}

	/** Against the container: its height is auto, so the percentage does nothing to a definite height. */
	public void testMaxHeightInAContainerOfAutoHeight() throws Exception {
		assertEquals(66, pos("", "<div><div style=\"height: 66pt; max-height: 90%\">T1</div>" + AFTER + "</div>", "T2")[1],
				0.01);
	}

	public void testMaxHeightInAnAbsoluteBoxOfAutoHeight() throws Exception {
		assertEquals(66, pos("", absolute("", "height: 66pt; max-height: 90%"), "T2")[1], 0.01);
	}

	/** max-width: 100% of an auto width clipped all the text to nothing (codex 2026-11-02; Chrome shows it). */
	public void testMaxWidthInAnAbsoluteBoxOfAutoWidth() throws Exception {
		final String page = page("writing-mode: vertical-rl", "<div style=\"position: absolute; top: 0; right: 0; height: "
				+ "100pt\"><div style=\"max-width: 100%; overflow: hidden\">T1<br>T2</div></div>");
		final Matcher m = Pattern.compile("Text\\[\"T1\".*clip=\\[-?[\\d.]+ -?[\\d.]+ ([\\d.]+) ").matcher(page);
		assertTrue(page, m.find());
		assertTrue(page, Double.parseDouble(m.group(1)) > 0);
	}

	/** The root box keeps the page as its basis (codex 2026-11-02: the footer at the bottom of the page). */
	public void testMinHeightOfTheRootBox() throws Exception {
		final File dir = dump("<!DOCTYPE html><html style=\"height: 20pt; min-height: 100%; position: relative\"><head>"
				+ "<meta charset=\"utf-8\"><style>@page { size: 200pt 200pt; margin: 0 } body { margin: 0; font: 7pt/10pt serif }"
				+ "</style></head><body>T1<div style=\"position: absolute; bottom: 0\">T2</div></body></html>");
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		assertEquals(190, pos(page, "T2")[1], 0.01);
	}

	public void testHeightInAnAbsoluteBox() throws Exception {
		assertEquals(50, pos("", absolute("height: 100pt", "height: 50%"), "T2")[1], 0.01);
	}

	/** Between insets the height comes from the containing block, the page area here. */
	public void testHeightInAnAbsoluteBoxBetweenInsets() throws Exception {
		assertEquals(200, pos("", absolute("bottom: 42pt", "height: 50%"), "T2")[1], 0.01);
	}

	/** Of an auto height there is no basis: the percentage is auto, not 0. */
	public void testHeightInAnAbsoluteBoxOfAutoHeight() throws Exception {
		assertEquals(8.5, pos("", absolute("", "height: 50%"), "T2")[1], 0.01);
	}

	/** A float grows an auto height, which is no basis for what follows it (codex's prediction, triage §154). */
	public void testHeightAfterAFloatInAnAbsoluteBoxOfAutoHeight() throws Exception {
		assertEquals(88.5, pos("", "<div style=\"position: absolute; top: 0; left: 0; width: 100pt\"><div style=\"float: left; "
				+ "width: 20pt; height: 80pt\"></div><div style=\"clear: left; height: 50%\">T1</div>" + AFTER + "</div>", "T2")[1],
				0.01);
	}

	public void testWidthInAVerticalAbsoluteBox() throws Exception {
		assertEquals(50, pos("writing-mode: vertical-lr", absolute("height: 100pt", "width: 50%"), "T2")[0], 0.01);
	}

	public void testWidthInAVerticalAbsoluteBoxOfAutoWidth() throws Exception {
		assertEquals(8.5, pos("writing-mode: vertical-lr", "<div style=\"position: absolute; top: 0; left: 0; height: 100pt\">"
				+ "<div style=\"width: 50%\">T1</div>" + AFTER + "</div>", "T2")[0], 0.01);
	}

	public void testMinWidthInAVerticalAbsoluteBox() throws Exception {
		assertEquals(50, pos("writing-mode: vertical-lr", absolute("height: 100pt", "min-width: 50%"), "T2")[0], 0.01);
	}

	public void testMaxHeightInAnAbsoluteBox() throws Exception {
		assertEquals(50, pos("", absolute("height: 100pt", "height: 80pt; max-height: 50%"), "T2")[1], 0.01);
	}

	/** Content past the box does not move the basis of what follows it. */
	public void testHeightAfterContentPastAnAbsoluteBox() throws Exception {
		assertEquals(80, pos("", "<div style=\"position: absolute; top: 0; left: 0; width: 100pt; height: 40pt\"><div style=\""
				+ "height: 60pt\">T1</div><div style=\"height: 50%\">T2</div><div>T3</div></div>", "T3")[1], 0.01);
	}

	private static String overlay(final String parent, final String box) {
		return "<div style=\"position: relative; " + parent + "\"><div style=\"position: absolute; " + box
				+ "\"><div style=\"height: 50%\">T1</div>" + AFTER + "</div><div style=\"height: 60pt\"></div></div>";
	}

	public void testHeightInAnOverlay() throws Exception {
		assertEquals(50, pos("", overlay("height: 100pt", "inset: 0"), "T2")[1], 0.01);
	}

	/** The parent's height is known only after the overlay's source: the body of the overlay waits for it. */
	public void testHeightInAnOverlayOfAParentOfAutoHeight() throws Exception {
		assertEquals(30, pos("", overlay("", "inset: 0"), "T2")[1], 0.01);
	}

	/** A box of length width took the other path, its body at once with the parent still empty. */
	public void testHeightBetweenInsetsOfAParentOfAutoHeight() throws Exception {
		assertEquals(30, pos("", overlay("", "top: 0; bottom: 0; left: 0; width: 100pt"), "T2")[1], 0.01);
	}

	public void testHeightOfAPercentageOfAParentOfAutoHeight() throws Exception {
		assertEquals(15, pos("", overlay("", "top: 0; left: 0; width: 100pt; height: 50%"), "T2")[1], 0.01);
	}

	/** A limit is as unknown as a size while the parent is empty: the box came out 0 high and clipped its text (codex). */
	public void testPercentageLimitOfAParentOfAutoHeight() throws Exception {
		assertEquals(20, clipHeight(page("", "<div style=\"position: relative\"><div style=\"position: absolute; top: 0; left: 0; "
				+ "width: 100pt; height: 100pt; max-height: 100%\"><div style=\"height: 20pt; max-height: 100%; overflow: hidden\">"
				+ "T1</div></div><div style=\"height: 100pt\"></div></div>"), "T1"), 0.01);
	}

	/** The table is built with the parent still empty, and its wrapper takes no size from the insets (codex). */
	public void testCaptionOfAnAbsoluteTableBetweenInsets() throws Exception {
		assertEquals(20, clipHeight(page("", "<div style=\"position: relative\"><div style=\"height: 10pt\"></div><table style=\""
				+ "position: absolute; top: 0; bottom: 0; left: 0; width: 100pt\"><caption style=\"height: 20pt; max-height: 100%; "
				+ "overflow: hidden\">T1</caption><tr><td>T2</td></tr></table><div style=\"height: 100pt\"></div></div>"), "T1"), 0.01);
	}

	/** The scan for footnote calls binds the body before the page ends: against the block between, 0 high (codex). */
	public void testBodyBoundByTheFootnoteScan() throws Exception {
		final String page = page("", "<p>T0<span style=\"float: footnote\">T9</span></p><div style=\"position: relative; height: "
				+ "100pt\"><div><div style=\"position: absolute; top: 0; left: 0; width: 100pt; height: 100%\"><div style=\"height: "
				+ "50%\">T1</div>" + AFTER + "</div></div></div>");
		assertEquals(pos(page, "T1")[1] + 50, pos(page, "T2")[1], 0.01);
	}

	/** The scan comes before the outer box takes its min-height: a size of 0 said nothing (codex). */
	public void testContainingBlockOfNoSizeYet() throws Exception {
		assertEquals(20, clipHeight(page("", "<p>T0<span style=\"float: footnote\">T9</span></p><div style=\"position: absolute; "
				+ "top: 40pt; left: 0; width: 100pt; min-height: 100pt\"><div style=\"position: absolute; top: 0; left: 0; width: "
				+ "100pt; height: 100%\"><div style=\"height: 20pt; max-height: 100%; overflow: hidden\">T1</div></div></div>"),
				"T1"), 0.01);
	}

	/** The scan of a column took the multicol for the containing block: 100% of the page, and the text off the paper (codex). */
	public void testBodyBoundByTheFootnoteScanOfAColumn() throws Exception {
		final String page = page("", "<div style=\"position: relative; height: 100pt\"><div style=\"column-count: 2; column-gap: "
				+ "0; column-fill: auto\"><div style=\"position: absolute; top: 0; left: 150pt; width: 40pt; height: 100%\"><div "
				+ "style=\"height: 100%\"></div>T1</div><p style=\"margin: 0\">T2<span style=\"float: footnote\">T9</span></p><p "
				+ "style=\"margin: 0; break-before: column\">T3</p></div></div>");
		assertEquals(100, pos(page, "T1")[1], 0.01);
	}

	/** A length stays a basis in a block of no height: only a size from that block has none (codex). */
	public void testLengthWithAPercentageLimitInABlockOfNoHeight() throws Exception {
		assertEquals(20, pos("", "<div style=\"position: relative; height: 0\"><div style=\"position: absolute; top: 0; left: 0; "
				+ "width: 100pt; height: 40pt; min-height: 100%\"><div style=\"height: 50%\"></div><div>T1</div></div></div>",
				"T1")[1], 0.01);
	}

	/** Not a box still open in the column as the next one starts: 20pt wide, it clipped the line (codex). */
	public void testContainingBlockOfAColumnWithABoxOpenAcrossIt() throws Exception {
		final Matcher m = Pattern.compile("Text\\[\"T3\".*clip=\\[-?[\\d.]+ -?[\\d.]+ ([\\d.]+) ").matcher(page("", "<div style=\""
				+ "position: relative; width: 150pt; column-count: 3; column-gap: 0; column-fill: auto\"><div style=\"position: "
				+ "relative; width: 20pt\">T1<div style=\"break-before: column\">T2</div></div><div style=\"position: absolute; "
				+ "left: 0; top: 40pt; width: 100%; height: 20pt; overflow: hidden\">T3</div>T4<span style=\"float: footnote\">"
				+ "T9</span><div style=\"break-before: column\">T5</div></div>"));
		assertTrue(m.find());
		assertEquals(150, Double.parseDouble(m.group(1)), 0.01);
	}

	/** overflow: hidden at a height of 0 clipped all of the text of a column beside the body. */
	public void testClippedHeightInAnOverlay() throws Exception {
		final String page = page("", "<div style=\"position: relative\"><div style=\"position: absolute; top: 0; bottom: 0; "
				+ "right: 0; width: 50pt\"><div style=\"height: 100%; overflow: hidden\">T1</div></div><div style=\"height: 60pt"
				+ "\"></div></div>");
		assertEquals(page, 60, clipHeight(page, "T1"), 0.01);
	}

	/** The columns took 100% of a box of height 0: they came out short, went past the box, and their last lines were lost. */
	public void testColumnsInAnAbsoluteBox() throws Exception {
		final String page = page("", "<div style=\"position: absolute; top: 0; left: 0; width: 100pt; height: 34pt; column-count: "
				+ "2; column-gap: 0; column-fill: auto\"><div>T1</div><div>T2</div><div>T3</div><div>T4</div><div>T5</div><div>T6"
				+ "</div><div>T7</div></div>");
		assertEquals(0, pos(page, "T4")[0], 0.01);
		assertEquals(25.5, pos(page, "T4")[1], 0.01);
		assertEquals(50, pos(page, "T7")[0], 0.01);
	}

	/** It went unnoticed in an absolutely positioned box, where every line broke the column anyway. */
	public void testForcedColumnBreakInColumnsOfDefiniteHeight() throws Exception {
		final String page = page("", "<div style=\"width: 100pt; height: 40pt; column-count: 2; column-gap: 0\"><div>T1</div><div "
				+ "style=\"break-before: column\">T2</div></div>");
		assertEquals(50, pos(page, "T2")[0], 0.01);
		assertEquals(0, pos(page, "T2")[1], 0.01);
	}

	/** On the first child it broke an empty column away, and the rest went off the box (codex). */
	public void testForcedColumnBreakOnTheFirstChild() throws Exception {
		final String page = page("", "<div style=\"width: 100pt; height: 40pt; column-count: 2; column-gap: 0\"><div style=\""
				+ "break-before: column\">T1</div><div style=\"break-before: column\">T2</div></div>");
		assertEquals(0, pos(page, "T1")[0], 0.01);
		assertEquals(50, pos(page, "T2")[0], 0.01);
	}

	/** Past the last column the text stays in it: the next column would be off the paper. */
	public void testForcedColumnBreakPastTheLastColumn() throws Exception {
		final String page = page("", "<div style=\"width: 100pt; height: 40pt; column-count: 2; column-gap: 0\"><div>T1</div><div "
				+ "style=\"break-before: column\">T2</div><div style=\"break-before: column\">T3</div></div>");
		assertEquals(50, pos(page, "T3")[0], 0.01);
		assertEquals(8.5, pos(page, "T3")[1], 0.01);
	}

	private static double clipHeight(final String page, final String token) {
		final Matcher m = Pattern.compile("Text\\[\"" + token + "\".*clip=\\[-?[\\d.]+ -?[\\d.]+ -?[\\d.]+ ([\\d.]+)")
				.matcher(page);
		assertTrue(page, m.find());
		return Double.parseDouble(m.group(1));
	}

	private static double[] pos(final String bodyStyle, final String body, final String token) throws Exception {
		return pos(page(bodyStyle, body), token);
	}

	private static double[] pos(final String page, final String token) {
		final Matcher m = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"" + token + "\"").matcher(page);
		assertTrue(page, m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}

	private static String page(final String bodyStyle, final String body) throws Exception {
		final File dir = dump("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 222pt 462pt; margin: 10pt } body { margin: 0; font: 7pt/8.5pt serif; " + bodyStyle + " }"
				+ "</style></head><body>" + body + "</body></html>");
		return Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
	}

	private static File dump(final String html) throws Exception {
		final File dir = Files.createTempDirectory("percent-page-size-basis").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return dir;
	}
}
