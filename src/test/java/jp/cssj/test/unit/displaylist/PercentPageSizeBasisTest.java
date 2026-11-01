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
 * took its specified size only after its content, so a percentage in it resolved against 0; so did a percentage limit
 * in an absolutely positioned box, and a percentage max-height in a parent of auto height. The expected values are
 * Chrome's.
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
