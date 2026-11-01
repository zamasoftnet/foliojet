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
 * A flex item holds its floats (2026-10-27, the sweep's writing-mode mirror check, seed 43750614): an item with a 79pt
 * float and a line of text was 8.5pt high in horizontal writing, so the paragraph after the flex container ran over the
 * float (vertical writing held it). A flex item establishes an independent formatting context, and Chrome makes the
 * item at least as high as the float.
 */
public class FlexItemFloatContainmentTest extends TestCase {
	private static final String ITEM = "<div><div style=\"float: right; width: 68pt; height: 79pt\">T1</div><p>T22 T23</p>"
			+ "</div><div>T27</div>";

	public void testNextParagraphBelowTheFloat() throws Exception {
		assertEquals(79, pos("<div style=\"display: flex\">" + ITEM + "</div><p>T40 T41 T42 T43 T44 T45</p>", "T40")[1],
				0.01);
	}

	/** The other item aligns to the end of the line the float makes. */
	public void testAlignEnd() throws Exception {
		assertEquals(79 - 8.5, pos("<div style=\"display: flex; align-items: end\">" + ITEM + "</div>", "T27")[1], 0.01);
	}

	/** An item of only a float held it before. */
	public void testOnlyFloat() throws Exception {
		assertEquals(79, pos("<div style=\"display: flex\"><div><div style=\"float: right; width: 68pt; height: 79pt\">T1"
				+ "</div></div><div>T27</div></div><p>T40</p>", "T40")[1], 0.01);
	}

	/** Within the item's max-height, with box-sizing (codex 2026-10-27; Chrome 20 and 40). */
	public void testMaxHeight() throws Exception {
		assertEquals(20, pos("<div style=\"display: flex\"><div style=\"max-height: 20pt; overflow: hidden\">" + ITEM.substring(5)
				+ "</div><p>T40</p>", "T40")[1], 0.01);
		assertEquals(40, pos("<div style=\"display: flex\"><div style=\"max-height: 40pt; box-sizing: border-box; padding: "
				+ "5pt; border: 1pt solid\">" + ITEM.substring(5) + "</div><p>T40</p>", "T40")[1], 0.01);
	}

	/** A column flex measures the item with its float (codex 2026-10-27; Chrome 87.5). */
	public void testColumn() throws Exception {
		assertEquals(87.5, pos("<div style=\"display: flex; flex-direction: column-reverse\">" + ITEM + "</div><p>T40</p>",
				"T40")[1], 0.01);
	}

	/** A grid item too (the mirror check's seed 43750565; Chrome 79). */
	public void testGrid() throws Exception {
		assertEquals(79, pos("<div style=\"display: grid; grid-template-columns: 180pt 40pt\">" + ITEM + "</div><p>T40</p>",
				"T40")[1], 0.01);
	}

	private static final String RUN = "<div>T1</div><div>T2</div><div><div style=\"float: left\">T3</div></div>";

	private static final String AFTER = "<p>T10</p><p>T11</p><p>T12</p><p>T13</p><p>T14</p><p>T15</p>";

	/**
	 * Laid out again by balanced columns, the items stack, and the float stayed at its stacked place for the text after
	 * the container (fit seed 56147995, 2026-11-01; Chrome 0, here 11.11 and more).
	 */
	public void testTextAfterTheContainerInBalancedColumns() throws Exception {
		assertEquals(0, pos("<div style=\"column-count: 2\"><div style=\"display: flex\">" + RUN + "</div>" + AFTER + "</div>",
				"T11")[0], 0.01);
	}

	public void testTextAfterAGridInBalancedColumns() throws Exception {
		assertEquals(0, pos("<div style=\"column-count: 2\"><div style=\"display: grid; grid-template-columns: 1fr 1fr 1fr\">"
				+ RUN + "</div>" + AFTER + "</div>", "T11")[0], 0.01);
	}

	/** The next item too, beside its neighbour's float (Chrome 0; here below the float, over the text after it). */
	public void testNextItemInBalancedColumns() throws Exception {
		assertEquals(0, pos("<div style=\"column-count: 2\"><div style=\"display: flex\"><div><div style=\"float: left; "
				+ "height: 30pt\">T1</div></div><div>T2<br>T3</div></div>" + AFTER + "</div>", "T2")[1], 0.01);
	}

	/** Moved whole to the next page, the same (Chrome 0). */
	public void testTextAfterTheContainerMovedToTheNextPage() throws Exception {
		final StringBuilder fill = new StringBuilder();
		for (int i = 0; i < 50; ++i) {
			fill.append("<p>F</p>");
		}
		assertEquals(0, pos(fill + "<div style=\"display: flex; break-inside: avoid\"><div><div style=\"height: 40pt\">T1"
				+ "</div></div><div>T2</div><div><div style=\"float: left\">T3</div></div></div>" + AFTER, "T11", 2)[0], 0.01);
	}

	/**
	 * A container of the opposite block direction is a float scope of its own: it took the stacked end of the float for
	 * its size, and its lines went that far from the start of the columns (the seed: off the paper; Chrome 0).
	 */
	public void testOppositeBlockDirectionInBalancedColumns() throws Exception {
		assertEquals(0, pos("<div style=\"writing-mode: vertical-lr\"><div style=\"column-count: 2\"><div style=\"display: flex; "
				+ "writing-mode: vertical-rl\">" + RUN + "</div></div></div>", "T1")[0], 0.01);
	}

	private static double[] pos(final String body, final String token) throws Exception {
		return pos(body, token, 1);
	}

	private static double[] pos(final String body, final String token, final int pageNumber) throws Exception {
		final File dir = Files.createTempDirectory("flex-item-float-containment").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 222pt 462pt; margin: 10pt } body { margin: 0; font: 7pt/8.5pt serif } p { margin: 0 }"
				+ "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, String.format("page-%04d.txt", pageNumber)).toPath(),
				StandardCharsets.UTF_8);
		final Matcher m = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"" + token + "\"").matcher(page);
		assertTrue(page, m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}
}
