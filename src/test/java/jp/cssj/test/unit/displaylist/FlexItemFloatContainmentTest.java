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

	private static double[] pos(final String body, final String token) throws Exception {
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
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final Matcher m = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"" + token + "\"").matcher(page);
		assertTrue(page, m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}
}
