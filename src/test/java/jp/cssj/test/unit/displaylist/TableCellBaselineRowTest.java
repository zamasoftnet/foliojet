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
 * A row is long enough for the content of its baseline-aligned cells moved down to the row's baseline (2026-10-28,
 * the sweep's mirror check, out-of-cell observations): a cell of three lines beside a cell with 20pt of padding above
 * its line was moved down by 20pt, but the row kept the height of the unmoved cell, so its last line was drawn over
 * the next row (T3 at y=51, T5 of the next row at 51.25). Chrome makes the row about 66pt long.
 */
public class TableCellBaselineRowTest extends TestCase {
	public TableCellBaselineRowTest(final String name) {
		super(name);
	}

	public void testRowHoldsTheMovedCell() throws Exception {
		check("", "");
	}

	/** The same with a cell spanning both rows (Retained's spanned demand). */
	public void testSpanningCell() throws Exception {
		check("<td rowspan=\"2\" style=\"vertical-align: baseline\">S1</td>", "");
	}

	/** A table of fixed layout, laid out row by row (IncrementalTableBuilder). */
	public void testFixedTable() throws Exception {
		check("", "; table-layout: fixed; width: 120pt");
	}

	private static void check(final String extra, final String tableStyle) throws Exception {
		final File dir = Files.createTempDirectory("table-cell-baseline-row").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 10pt } body { margin: 0; font: 10pt/14pt serif }"
				+ " td { border: 1pt solid }</style></head><body><table style=\"border-spacing: 2pt" + tableStyle
				+ "\"><tr>"
				+ "<td style=\"vertical-align: baseline\">T1<br>T2<br>T3</td>"
				+ "<td style=\"vertical-align: baseline; padding-top: 20pt\">T4</td>" + extra
				+ "</tr><tr><td>T5</td><td>T6</td></tr></table></body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		final double t3 = y(page, "T3");
		final double t5 = y(page, "T5");
		// T3's line (14pt) ends before the next row's content begins
		assertTrue(page, t3 + 14 <= t5);
	}

	private static double y(final String page, final String token) {
		final Matcher m = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"" + token + "\"").matcher(page);
		assertTrue(page, m.find());
		return Double.parseDouble(m.group(2));
	}
}
