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
 * A table in a block of the other writing mode in a table cell (2026-10-27, fit sweep seed 44161211): the measurement
 * of the cell counted the table's page-axis extent 0, so the block and the cell were 0 thick and a floated table was put
 * at the paper's edge with its content past it. Chrome (print) puts the text within 1.5pt of these positions on the
 * 200pt page (T0 of the two-row tables at 167 and 168, the vertical paper's at 188.3).
 */
public class OrthogonalTableInCellTest extends TestCase {
	private static final String FLOATED = "<div style=\"display: table; float: right\"><div style=\"writing-mode: %s\">";

	public void testFloatedTable() throws Exception {
		assertEquals(188, x("horizontal-tb", String.format(FLOATED, "vertical-lr") + "<div style=\"display: table; float: "
				+ "right\"><p>T0</p></div></div></div>"), 0.01);
	}

	public void testBlockTable() throws Exception {
		assertEquals(188, x("horizontal-tb", String.format(FLOATED, "vertical-lr") + "<table style=\"border-spacing: 0\">"
				+ "<tr><td>T0</td></tr></table></div></div>"), 0.01);
	}

	/** Rows stack with the spacing and the cells' padding: 2 + 12 + 2 + 18 + 2. */
	public void testRows() throws Exception {
		assertEquals(166, x("horizontal-tb", String.format(FLOATED, "vertical-lr") + "<table style=\"border-spacing: 2pt\">"
				+ "<tr><td>T0</td></tr><tr><td style=\"padding: 3pt\">T1</td></tr></table></div></div>"), 0.01);
	}

	/** Collapsed borders and the table's margins: 5 + 2 + 12 + 2 + 12 + 2 + 5. */
	public void testCollapsed() throws Exception {
		assertEquals(168, x("horizontal-tb", String.format(FLOATED, "vertical-lr") + "<table style=\"border-collapse: "
				+ "collapse; margin: 5pt\"><tr><td style=\"border: 2pt solid\">T0</td></tr><tr><td style=\"border: 2pt "
				+ "solid\">T1</td></tr></table></div></div>"), 0.01);
	}

	/** A length for a row's size, and a caption with its length (codex 2026-10-27; Chrome 144 and 188). */
	public void testRowSizeAndCaption() throws Exception {
		assertEquals(144, x("horizontal-tb", String.format(FLOATED, "vertical-lr") + "<table style=\"border-spacing: 0\">"
				+ "<tr style=\"block-size: 100pt\"><td>T0</td></tr></table></div></div>"), 0.01);
		assertEquals(188, x("horizontal-tb", String.format(FLOATED, "vertical-lr") + "<table style=\"border-spacing: 0\">"
				+ "<caption style=\"block-size: 80pt\">C</caption><tr><td>T0</td></tr></table></div></div>"), 0.01);
	}

	/**
	 * Staggered row spans add what their rows lack to the last row: 100pt, not 300pt (codex 2026-10-27; Chrome 144, T0
	 * in the middle of the 100pt).
	 */
	public void testStaggeredRowSpans() throws Exception {
		assertEquals(144, x("horizontal-tb", String.format(FLOATED, "vertical-lr") + "<table style=\"border-spacing: 0\">"
				+ "<tr><td rowspan=\"3\" style=\"block-size: 100pt\">T0</td><td></td><td></td></tr><tr><td rowspan=\"2\" "
				+ "style=\"block-size: 100pt\"></td><td></td></tr><tr><td style=\"block-size: 100pt\"></td></tr></table>"
				+ "</div></div>"), 0.01);
	}

	/** The seed's shape: vertical paper, a horizontal block holding a floated table. */
	public void testVerticalPaper() throws Exception {
		assertEquals(188, y("vertical-lr", String.format(FLOATED, "horizontal-tb") + "<div style=\"display: table; float: "
				+ "right\"><p>T0</p></div></div></div>"), 0.01);
	}

	private static double x(final String writingMode, final String body) throws Exception {
		return Double.parseDouble(t0(writingMode, body).group(1));
	}

	private static double y(final String writingMode, final String body) throws Exception {
		return Double.parseDouble(t0(writingMode, body).group(2));
	}

	private static Matcher t0(final String writingMode, final String body) throws Exception {
		final File dir = Files.createTempDirectory("orthogonal-table-in-cell").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 200pt 200pt; margin: 0 } html { writing-mode: " + writingMode + " } body { margin: 0; "
				+ "font: 10pt/1.2 serif } p, div, td { margin: 0; padding: 0 }</style></head><body>" + body
				+ "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final Matcher m = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"T0\"").matcher(page);
		assertTrue(page, m.find());
		return m;
	}
}
