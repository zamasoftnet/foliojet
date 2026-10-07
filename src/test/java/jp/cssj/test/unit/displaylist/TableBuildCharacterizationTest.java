package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.builder.impl.TableBuildStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Table-building characterization tests (P2-1; §5.2b preservation contract for table builder unification.
 * C4-A/B: routing distinguishes "early commit possible (Incremental) or entire-table retention (Retained)",
 * rather than "fixed versus auto"; see {@link TableRetentionReason}).
 *
 * <p>
 * Use counters to pin down properties that golden equality alone cannot detect: fixed tables
 * with definite dimensions, an auto page-axis size, and FLOW positioning route to Incremental;
 * their streaming is bounded; tables with auto layout, specified page-axis dimensions, or non-FLOW
 * positioning use Retained; split and rowspan-cut paths actually execute.
 * The P2 replacement and C4 redesign must preserve these properties.
 * </p>
 */
public class TableBuildCharacterizationTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testFixedPagedTableStreamsOnePass() throws Exception {
		final long onePass = TableBuildStats.ONE_PASS_BUILDS.get();
		final long fragments = TableBuildStats.TABLE_FRAGMENTS.get();
		TableBuildStats.ONE_PASS_ROW_HIGH_WATER.set(0);
		this.transcode(new File("files/unittest/0390-writing-mode/fixed-table-pagebreak.html"), "char-fixed");
		assertTrue("この定寸法・ページ軸auto・FLOW配置のfixed表がIncremental(OnePass)に"
				+ "ルーティングされていません", TableBuildStats.ONE_PASS_BUILDS.get() > onePass);
		assertTrue("ページ跨ぎで表断片が生成されていません",
				TableBuildStats.TABLE_FRAGMENTS.get() > fragments);
		// Incremental streaming is bounded: retaining all rows of a nine-row table is a regression.
		// Pin down current measured values as a preservation contract (maintain them after the P2 replacement).
		final long highWater = TableBuildStats.ONE_PASS_ROW_HIGH_WATER.get();
		assertTrue("行保持の high-water が観測されていません", highWater > 0);
		assertTrue("Incrementalストリーミングが全体保持に退化しています: high-water=" + highWater, highWater < 9);
	}

	public void testAutoTableUsesTwoPass() throws Exception {
		final long twoPass = TableBuildStats.TWO_PASS_BUILDS.get();
		this.transcode(new File("files/unittest/0070-table-layout/auto-rowspan.html"), "char-auto");
		assertTrue("auto 表が TwoPass にルーティングされていません",
				TableBuildStats.TWO_PASS_BUILDS.get() > twoPass);
	}

	/**
	 * Even with table-layout:fixed, a table with an explicitly specified page-axis dimension
	 * (height in horizontal writing) routes to Retained (RetainedTableBuilder).
	 * Verify that routing distinguishes "whether early commit is possible", not "fixed versus auto"
	 * (TableRetentionReason.SPECIFIED_PAGE_SIZE, C4-B, external design review 2026-07-19).
	 */
	public void testFixedTableWithSpecifiedHeightUsesRetained() throws Exception {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "char-fixed-specified-height.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}"
					+ "table{table-layout:fixed;width:150pt;height:100pt}td{border:1pt solid black}</style>\n");
			w.write("</head><body><table><tr><td>a</td><td>b</td></tr>"
					+ "<tr><td>c</td><td>d</td></tr></table></body></html>\n");
		}
		final long twoPass = TableBuildStats.TWO_PASS_BUILDS.get();
		this.transcode(file, "char-fixed-specified-height");
		assertTrue(
				"ページ軸寸法(height)が明示指定されたfixed表がRetainedTableBuilder"
						+ "(Retained)にルーティングされていません",
				TableBuildStats.TWO_PASS_BUILDS.get() > twoPass);
	}

	public void testRowspanCutFires() throws Exception {
		final long cuts = TableBuildStats.ROWSPAN_CUTS.get();
		this.transcode(new File("files/unittest/0218-pagebreak-table-span/fixed-rowspan.html"), "char-rowspan");
		assertTrue("rowspan 連結セルの切断経路が通っていません",
				TableBuildStats.ROWSPAN_CUTS.get() > cuts);
	}

	/**
	 * A table-layout:fixed table with non-FLOW positioning (float) remains handled by RetainedTableBuilder.
	 * Routing it to IncrementalTableBuilder breaks startLayout()'s FLOW assumption in an assert/cast,
	 * so it must be excluded (found in external design review 2026-07-19, P0-1).
	 * Before the fix, needsIntrinsicSizing() returned true even for non-FLOW positioning,
	 * incorrectly routing it to Incremental; this document should have failed with AssertionError
	 * when assertions were enabled.
	 */
	public void testNonFlowFixedTableStaysOnTwoPass() throws Exception {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "char-nonflow-fixed.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}"
					+ "table{table-layout:fixed;width:150pt;float:left}td{border:1pt solid black}</style>\n");
			w.write("</head><body><table><tr><td>a</td><td>b</td></tr>"
					+ "<tr><td>c</td><td>d</td></tr></table></body></html>\n");
		}
		final long twoPass = TableBuildStats.TWO_PASS_BUILDS.get();
		this.transcode(file, "char-nonflow-fixed");
		assertTrue("非FLOW配置のfixed表がRetainedTableBuilderにルーティングされていません(P0-1回帰)",
				TableBuildStats.TWO_PASS_BUILDS.get() > twoPass);
	}

	/**
	 * Verify that retained row high-water does not scale with total row count for a large fixed table
	 * (5,000 rows, no rowspan). This is a prerequisite for C4 table unification: even if unification
	 * appears successful, this invariant detects regression in retention behavior
	 * (raised in codex external consultation 2026-07-19).
	 */
	public void testFixedTableHighWaterIsBoundedAtScale() throws Exception {
		final int totalRows = 5000;
		final File doc = generateFixedTable("char-fixed-scale", totalRows, 1);
		TableBuildStats.ONE_PASS_ROW_HIGH_WATER.set(0);
		this.transcode(doc, "char-fixed-scale");
		final long highWater = TableBuildStats.ONE_PASS_ROW_HIGH_WATER.get();
		assertTrue("high-waterが観測されていません", highWater > 0);
		assertTrue(
				"fixedストリーミングが総行数(" + totalRows + ")に比例して育っています: high-water=" + highWater,
				highWater < totalRows / 50);
	}

	/**
	 * With a spanning cell of rowspan=N, verify that retained row high-water scales with N,
	 * not total row count (likewise raised in codex external consultation 2026-07-19).
	 */
	public void testFixedTableHighWaterScalesWithRowspanNotTotalRows() throws Exception {
		final int totalRows = 2000;
		final int rowspan = 50;
		final File doc = generateFixedTable("char-fixed-rowspan-scale", totalRows, rowspan);
		TableBuildStats.ONE_PASS_ROW_HIGH_WATER.set(0);
		this.transcode(doc, "char-fixed-rowspan-scale");
		final long highWater = TableBuildStats.ONE_PASS_ROW_HIGH_WATER.get();
		assertTrue("high-waterが観測されていません", highWater > 0);
		assertTrue("high-waterがrowspan(" + rowspan + ")に対して大きすぎます: high-water=" + highWater,
				highWater < rowspan * 3L);
		assertTrue(
				"high-waterが総行数(" + totalRows + ")に比例して育っています: high-water=" + highWater,
				highWater < totalRows / 10);
	}

	/**
	 * An absolute-height row group (&lt;tbody style="height:..."&gt;) keeps accumulating all rows in rowsUnit
	 * until the group closes (IncrementalTableBuilder.endInnerTable() does not reset bindUnit=false
	 * until the row group ends). Observe and pin down that even with table-layout:fixed,
	 * an absolute row-group height can create an unbounded growth path besides table-layout:auto
	 * (found in external design review 2026-07-19, P0-2). Whether to fix this remains undecided:
	 * retention is valid because distributing absolute height within a row group requires the whole group,
	 * but CSS-SUPPORT.md must explicitly state that this path can scale with document size.
	 */
	public void testFixedTableAbsoluteHeightRowGroupRetainsWholeGroup() throws Exception {
		final int totalRows = 3000;
		final File doc = generateFixedTableWithAbsoluteRowGroup("char-fixed-absolute-rowgroup", totalRows);
		TableBuildStats.ONE_PASS_ROW_HIGH_WATER.set(0);
		this.transcode(doc, "char-fixed-absolute-rowgroup");
		final long highWater = TableBuildStats.ONE_PASS_ROW_HIGH_WATER.get();
		assertTrue("high-waterが観測されていません", highWater > 0);
		assertTrue(
				"絶対高さrow-groupの保持がtotalRows(" + totalRows + ")に達していません"
						+ "(挙動が変わった可能性、既知の限界の記述を見直すこと): high-water=" + highWater,
				highWater >= totalRows);
	}

	/**
	 * Generate a table-layout:fixed table with the specified row count and optional rowspan in the first column
	 * (for large-scale characterization tests). Since it is not a golden comparison target,
	 * generate it each time under local/unittest rather than storing it under files/unittest.
	 *
	 * @param name    generated file name (without extension)
	 * @param rows    total row count
	 * @param rowspan 1 for normal cells; 2 or more adds rowspan to the first column of the first row
	 */
	private static File generateFixedTable(String name, int rows, int rowspan) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}"
					+ "table{table-layout:fixed;width:200pt}td{border:1pt solid black}</style>\n");
			w.write("</head><body><table>\n");
			for (int i = 0; i < rows; ++i) {
				w.write("<tr>");
				if (rowspan > 1 && i == 0) {
					w.write("<td rowspan=\"" + rowspan + "\">span</td>");
				} else if (rowspan > 1 && i < rowspan) {
					// Row covered by rowspan: do not output a cell in the first column.
				} else {
					w.write("<td>r" + i + "</td>");
				}
				w.write("<td>c" + i + "</td></tr>\n");
			}
			w.write("</table></body></html>\n");
		}
		return file;
	}

	/**
	 * Generate a table-layout:fixed table with all rows wrapped in a single absolute-height tbody
	 * (for the P0-2 characterization test).
	 */
	private static File generateFixedTableWithAbsoluteRowGroup(String name, int rows) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}"
					+ "table{table-layout:fixed;width:200pt}td{border:1pt solid black}</style>\n");
			w.write("</head><body><table><tbody style=\"height:20000pt\">\n");
			for (int i = 0; i < rows; ++i) {
				w.write("<tr><td>r" + i + "</td><td>c" + i + "</td></tr>\n");
			}
			w.write("</tbody></table></body></html>\n");
		}
		return file;
	}

	private void transcode(File source, String name) throws Exception {
		File pdf = new File("local/unittest/display-list/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
