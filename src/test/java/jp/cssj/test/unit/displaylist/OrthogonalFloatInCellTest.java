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
 * A float in a block of the other writing mode in a table cell (2026-10-27, fit sweep seed 43939201): the measurement
 * of the cell did not count the float, so the block and the cell were 0 wide and a floated table was put at the
 * paper's right edge with its content past it. Chrome (print) puts T0 at x=90 on the 200pt page, as Copper does
 * without the table.
 */
public class OrthogonalFloatInCellTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"T0\"");

	public void testFloatedTable() throws Exception {
		assertEquals(89, x("<div style=\"display: table; float: right\"><div style=\"writing-mode: vertical-lr\"><div "
				+ "style=\"float: right; width: 111pt\"><p>T0</p></div></div></div>"), 0.01);
	}

	/** The control without the table. */
	public void testFloatedBlock() throws Exception {
		assertEquals(89, x("<div style=\"float: right\"><div style=\"writing-mode: vertical-lr\"><div "
				+ "style=\"float: right; width: 111pt\"><p>T0</p></div></div></div>"), 0.01);
	}

	/** Content clearing the float starts past it: T0 after the 30pt float, the 42pt table at the right edge. */
	public void testClearedAfterFloat() throws Exception {
		assertEquals(200 - 12, x("<div style=\"display: table; float: right\"><div style=\"writing-mode: "
				+ "vertical-lr\"><div style=\"float: left; width: 30pt\">F</div><p style=\"clear: both\">T0</p></div></div>"),
				0.01);
	}

	/** The float's page-axis size within its max (codex 2026-10-27; it counted 300pt and put the table off the paper). */
	public void testMaxSize() throws Exception {
		assertEquals(200 - 80, x("<div style=\"display: table; float: right\"><div style=\"writing-mode: vertical-lr\">"
				+ "<div style=\"float: right; width: 300pt; max-width: 80pt\"><p>T0</p></div></div></div>"), 0.01);
	}

	/** An inline-block of a length thick is that thick, whatever floats it holds (codex 2026-10-27). */
	public void testInlineBlockOfLength() throws Exception {
		assertEquals(200 - 20, x("<div style=\"display: table; float: right\"><div style=\"writing-mode: vertical-lr\">"
				+ "<span style=\"display: inline-block; width: 20pt; overflow: hidden\">T0<div style=\"float: left; "
				+ "width: 100pt\">F</div></span></div></div>"), 0.01);
	}

	private static double x(final String body) throws Exception {
		final File dir = Files.createTempDirectory("orthogonal-float-in-cell").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 200pt 200pt; margin: 0 } body { margin: 0; font: 10pt/1.2 serif } p, div { margin: 0; "
				+ "padding: 0 }</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final Matcher m = TEXT.matcher(page);
		assertTrue(page, m.find());
		return Double.parseDouble(m.group(1));
	}
}
