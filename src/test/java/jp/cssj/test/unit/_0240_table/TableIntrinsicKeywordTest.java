package jp.cssj.test.unit._0240_table;

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
 * A table's intrinsic size keywords resolve against its own min-content and max-content (2026-10-11; the layouts are
 * Chrome's): width: min-content wraps its content, and min-width: max-content wins over a narrower max-width. They were
 * ignored. The table holds two 45pt boxes that can break between them.
 */
public class TableIntrinsicKeywordTest extends TestCase {
	private static final Pattern BOX = Pattern.compile("x=([\\d.]+) y=([\\d.]+) AbsoluteRectFrame\\[w=45.00 h=7.50\\]");

	public void testMinContentWidth() throws Exception {
		// Chrome puts the second box under the first (it was beside it)
		assertEquals(List.of(0.0, 0.0, 0.0, 7.5), boxes("width: min-content"));
	}

	public void testMaxContentMinWidthOverMaxWidth() throws Exception {
		// Chrome keeps the boxes on one line (it wrapped them at max-width: 30pt)
		assertEquals(List.of(0.0, 0.0, 45.0, 0.0), boxes("min-width: max-content; max-width: 30pt"));
	}

	public void testPercentColumnResolvedOnce() throws Exception {
		// A 25% column in a table with min-width: max-content: Chrome keeps each cell on one line, the table 180pt wide
		// (it was ignored; a second resolution of the columns multiplied the percentage again)
		assertEquals(List.of(0.0, 0.0, 45.0, 0.0, 90.0, 0.0), boxesIn("<div style=\"width: 45pt\"><table style=\""
				+ "border-spacing: 0; min-width: max-content\"><tr><td style=\"width: 25%; padding: 0\"><i></i></td>"
				+ "<td style=\"padding: 0\"><i></i> <i></i></td></tr></table></div>"));
	}

	public void testNumericMinWidthOverMaxWidthKeyword() throws Exception {
		// width and min-width 90pt with max-width: min-content: min-width wins, Chrome keeps the boxes on one line
		assertEquals(List.of(0.0, 0.0, 45.0, 0.0), boxes("width: 90pt; min-width: 90pt; max-width: min-content"));
	}

	public void testItemTableKeepsItsFlexedSize() throws Exception {
		// A table with flex: 1 and width: min-content fills its flex item (142.5pt): Chrome keeps the boxes on one line
		assertEquals(List.of(0.0, 0.0, 45.0, 0.0), boxesIn("<div style=\"display: flex; width: 150pt\"><table style=\""
				+ "flex: 1; width: min-content; border-spacing: 0\"><tr><td style=\"padding: 0\"><i></i> <i></i></td></tr>"
				+ "</table><div style=\"flex: none; width: 7.5pt\"></div></div>"));
	}

	public void testWidthKeywordWithNumericMinWidth() throws Exception {
		// width: min-content with min-width: 90pt: min-width wins, Chrome keeps the boxes on one line
		assertEquals(List.of(0.0, 0.0, 45.0, 0.0), boxes("width: min-content; min-width: 90pt"));
	}

	public void testMaxContentWidthOverflowsItsBlock() throws Exception {
		// width: max-content in a 45pt block: Chrome keeps the boxes on one line, the table overflowing the block
		assertEquals(List.of(0.0, 0.0, 45.0, 0.0), boxesIn("<div style=\"width: 45pt\"><div style=\"display: table; "
				+ "width: max-content\"><i></i> <i></i></div></div>"));
	}

	public void testFitContentMaxWidthWithinItsBlock() throws Exception {
		// width: max-content with max-width: fit-content in a 45pt block: fit-content is bounded by the block, Chrome
		// puts the second box under the first
		assertEquals(List.of(0.0, 0.0, 0.0, 7.5), boxesIn("<div style=\"width: 45pt\"><div style=\"display: table; "
				+ "width: max-content; max-width: fit-content\"><i></i> <i></i></div></div>"));
	}

	public void testCaptionFloorsMinContent() throws Exception {
		// width: min-content of a table whose caption holds a 90pt box: the caption's min-content is the table's floor,
		// Chrome keeps the two boxes of the cell on one line
		assertEquals(List.of(0.0, 7.5, 45.0, 7.5), boxesIn("<table style=\"width: min-content; border-spacing: 0\">"
				+ "<caption style=\"padding: 0\"><b style=\"display: inline-block; width: 90pt; height: 7.5pt\"></b></caption>"
				+ "<tr><td style=\"padding: 0\"><i></i> <i></i></td></tr></table>"));
	}

	public void testPercentColumnWidensFitContentOnly() throws Exception {
		// A 25% column holding a 45pt box: Chrome makes a fit-content table 180pt wide, as an auto one, and a max-content
		// one 90pt (the second box right-aligned in its cell)
		final String row = "; border-spacing: 0\"><tr><td style=\"width: 25%; padding: 0\"><i></i></td>"
				+ "<td style=\"padding: 0; text-align: right\"><i></i></td></tr></table>";
		assertEquals(List.of(0.0, 0.0, 135.0, 0.0), boxesIn("<table style=\"width: fit-content" + row));
		assertEquals(List.of(0.0, 0.0, 45.0, 0.0), boxesIn("<table style=\"width: max-content" + row));
		// As a limit max-content keeps the widening: Chrome leaves a max-width: max-content table at 180pt
		assertEquals(List.of(0.0, 0.0, 135.0, 0.0), boxesIn("<table style=\"max-width: max-content" + row));
	}

	/** {x, y} of the two boxes, in order. */
	private static List<Double> boxes(final String style) throws Exception {
		return boxesIn("<div style=\"display: table; " + style + "\"><i></i> <i></i></div>");
	}

	/** {x, y} of the boxes, in order. */
	private static List<Double> boxesIn(final String body) throws Exception {
		final Matcher m = BOX.matcher(convert(body));
		final List<Double> xy = new ArrayList<>();
		while (m.find()) {
			xy.add(Double.parseDouble(m.group(1)));
			xy.add(Double.parseDouble(m.group(2)));
		}
		return xy;
	}

	private static String convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("table-intrinsic-keyword").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 7.5pt } body { margin: 0; font-size: 0 }"
				+ " i { display: inline-block; width: 45pt; height: 7.5pt; background: #ccc }"
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
