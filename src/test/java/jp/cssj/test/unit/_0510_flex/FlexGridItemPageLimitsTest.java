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
 * Flex and grid items take their own min and max along the block axis (2026-11-19, found while fixing triage §238).
 * The flex and grid builders lay an item out without the block sizing that resolves them, so in a row flex container
 * or a grid, height: 116pt; max-height: 80.1pt stayed 116pt, height: 60pt; min-height: 88pt stayed 60pt, an item of
 * long text with max-height: 80.1pt grew to its content and a stretched one to the line. Chrome applies them all.
 */
public class FlexGridItemPageLimitsTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"(T\\d)\"");

	/** The distance along the block axis from {@code from} to {@code to}. */
	private static double distance(final String writingMode, final String body, final String from, final String to)
			throws Exception {
		final String doc = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page{size:300pt 300pt;margin:0pt}"
				+ "html{writing-mode:" + writingMode + "} body{margin:0;font:10pt/12pt serif} p{margin:0}</style></head>"
				+ "<body>" + body + "</body></html>";
		final File dir = Files.createTempDirectory("flex-grid-item-limits").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), doc, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final Matcher m = TEXT.matcher(Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8));
		double[] a = null, b = null;
		while (m.find()) {
			final double[] at = { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
			if (m.group(3).equals(from)) {
				a = at;
			} else if (m.group(3).equals(to)) {
				b = at;
			}
		}
		assertNotNull(body, a);
		assertNotNull(body, b);
		return writingMode.equals("horizontal-tb") ? b[1] - a[1] : a[0] - b[0];
	}

	/** The outer block size of the item holding T0, which starts it, from T1 after its container (aligned to start). */
	private static double itemSize(final String writingMode, final String container, final String item, final String text)
			throws Exception {
		return distance(writingMode, "<div style=\"" + container + "\"><div style=\"" + item + "\">T0" + text
				+ "</div></div><p>T1</p>", "T0", "T1");
	}

	private static final String ROW = "display:flex;align-items:start";

	private static final String GRID = "display:grid;align-items:start";

	public void testFlexCrossMaxAndMin() throws Exception {
		assertEquals(80.1, itemSize("horizontal-tb", ROW, "width:50pt;height:116pt;max-height:80.1pt", ""), 0.01);
		assertEquals(88.0, itemSize("horizontal-tb", ROW, "width:50pt;height:60pt;min-height:88pt", ""), 0.01);
		assertEquals(88.0, itemSize("horizontal-tb", ROW, "width:50pt;height:116pt;min-height:88pt;max-height:80.1pt", ""),
				0.01);
		final StringBuilder words = new StringBuilder();
		for (int i = 0; i < 80; ++i) {
			words.append(" W");
		}
		assertEquals(80.1, itemSize("horizontal-tb", ROW, "width:50pt;max-height:80.1pt;overflow:hidden", words.toString()),
				0.01);
		assertEquals(80.1, itemSize("vertical-rl", ROW, "height:50pt;width:116pt;max-width:80.1pt", ""), 0.01);
	}

	public void testEmptyFlexItemTakesItsMin() throws Exception {
		assertEquals(100.0, distance("horizontal-tb",
				"<p>T0</p><div style=\"" + ROW + "\"><div style=\"width:50pt;min-height:88pt\"></div></div><p>T1</p>", "T0",
				"T1"), 0.01);
	}

	public void testStretchStopsAtTheMax() throws Exception {
		assertEquals(68.1, distance("horizontal-tb",
				"<div style=\"display:flex;height:200pt\"><div style=\"position:relative;width:50pt;max-height:80.1pt\">T0"
						+ "<div style=\"position:absolute;bottom:0;left:0\">T9</div></div></div>",
				"T0", "T9"), 0.01);
	}

	public void testGridItemMaxAndMin() throws Exception {
		assertEquals(80.1, itemSize("horizontal-tb", GRID, "height:116pt;max-height:80.1pt", ""), 0.01);
		assertEquals(88.0, itemSize("horizontal-tb", GRID, "height:60pt;min-height:88pt", ""), 0.01);
		assertEquals(80.1, itemSize("vertical-rl", GRID, "width:116pt;max-width:80.1pt", ""), 0.01);
	}

	/** Within its min and max the specified size stands. */
	public void testSizeWithinLimitsStands() throws Exception {
		assertEquals(60.0, itemSize("horizontal-tb", ROW, "width:50pt;height:60pt;min-height:40pt;max-height:80.1pt", ""),
				0.01);
		assertEquals(60.0, itemSize("horizontal-tb", GRID, "height:60pt;min-height:40pt;max-height:80.1pt", ""), 0.01);
	}
}
