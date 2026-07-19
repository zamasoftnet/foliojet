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
 * 表構築の特性テストです(P2-1。§5.2b 表ビルダー統一の保存契約。
 * C4-A/B——ルーティングは「fixed対auto」ではなく「早期コミット可能
 * (Incremental)か表全体保持(Retained)か」の軸、{@link TableRetentionReason}
 * 参照)。
 *
 * <p>
 * golden 一致だけでは検出できない特性 — 定寸法・ページ軸auto・FLOW配置の
 * fixed表がIncrementalにルーティングされること、そのストリーミングが
 * 有界であること、autoやページ軸寸法指定・非FLOW配置の表がRetainedで
 * あること、分割・rowspan切断の経路が実際に通ること — をカウンタで
 * 固定します。P2の置換・C4の再設計はこれらを保存しなければならない。
 * </p>
 */
public class TableBuildCharacterizationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

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
		// Incrementalのストリーミングは有界: 9行の表で全行保持なら退化。
		// 現行実装の実測値を保存契約として固定する(P2 置換後も維持)
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
	 * table-layout:fixedでも、表にページ軸寸法(横書きならheight)が明示指定
	 * されていればRetained(TwoPassTableBuilder)へルーティングされる
	 * ——ルーティングは「fixed対auto」ではなく「早期コミット可能か否か」の
	 * 軸であることを固定する(TableRetentionReason.SPECIFIED_PAGE_SIZE、
	 * C4-B・外部設計レビュー2026-07-19)。
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
				"ページ軸寸法(height)が明示指定されたfixed表がTwoPassTableBuilder"
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
	 * processing.strict-one-pass=trueは、無制限バッファを要する
	 * table-layout:autoのTwoPassTableBuilder使用を避け、警告付きで
	 * OnePassTableBuilder(table-layout:fixed相当)へ近似する
	 * (例外にはしない。未対応セレクタと同じ「警告+縮退」経路。
	 * docs/PLAN.md「2パス制御モード」参照)。
	 */
	public void testStrictOnePassDegradesAutoTableToOnePass() throws Exception {
		final long twoPass = TableBuildStats.TWO_PASS_BUILDS.get();
		final long onePass = TableBuildStats.ONE_PASS_BUILDS.get();
		final long degrades = TableBuildStats.STRICT_ONE_PASS_DEGRADES.get();
		this.transcode(new File("files/unittest/0070-table-layout/auto-rowspan.html"), "char-auto-strict", true);
		assertEquals("strict-one-pass中でもTwoPassTableBuilderが構築されています",
				twoPass, TableBuildStats.TWO_PASS_BUILDS.get());
		assertTrue("auto表がOnePassTableBuilderへ近似されていません",
				TableBuildStats.ONE_PASS_BUILDS.get() > onePass);
		assertTrue("STRICT_ONE_PASS_DEGRADESが計上されていません",
				TableBuildStats.STRICT_ONE_PASS_DEGRADES.get() > degrades);
	}

	/**
	 * processing.strict-one-pass=trueでも、無制限バッファを要しない
	 * (table-layout:fixed)文書は通常どおり処理できる。
	 */
	public void testStrictOnePassAllowsFixedTable() throws Exception {
		this.transcode(new File("files/unittest/0390-writing-mode/fixed-table-pagebreak.html"), "char-fixed-strict",
				true);
	}

	/**
	 * 非FLOW配置(float)のtable-layout:fixed表は、processing.strict-one-pass=true
	 * でもTwoPassTableBuilderのまま処理される(OnePassTableBuilderへ縮退させると
	 * startLayout()のFLOW前提assert/castが破綻するため対象外にする必要がある——
	 * 外部設計レビュー2026-07-19で発見、P0-1。修正前はneedsIntrinsicSizing()が
	 * 非FLOW配置でもtrueを返すため誤ってOnePassへ回り、この文書はassert有効時に
	 * AssertionErrorで失敗していたはず)。
	 */
	public void testStrictOnePassKeepsNonFlowFixedTableOnTwoPass() throws Exception {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "char-nonflow-fixed-strict.html");
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
		this.transcode(file, "char-nonflow-fixed-strict", true);
		assertTrue("非FLOW配置のfixed表がTwoPassTableBuilderにルーティングされていません(P0-1回帰)",
				TableBuildStats.TWO_PASS_BUILDS.get() > twoPass);
	}

	/**
	 * 大規模(5,000行・rowspanなし)fixed表で、retained row high-waterが
	 * 総行数に比例しないことを確認する(C4=表統一の前提条件。統一が
	 * 見かけ上成功しても保持方式が退化していないかをこの不変条件で
	 * 固定する。codex外部相談2026-07-19で指摘)。
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
	 * rowspan=Nの結合セルがある場合、retained row high-waterはNに比例し、
	 * 総行数には比例しないことを確認する(同上、codex外部相談2026-07-19で
	 * 指摘)。
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
	 * 絶対高さrow-group(&lt;tbody style="height:..."&gt;)は、その行グループが
	 * 閉じるまで全行をrowsUnitへ蓄積し続ける(OnePassTableBuilder.endInnerTable()の
	 * bindUnit=falseがrow-group終了までリセットされないため)。table-layout:fixed
	 * であっても、行グループの絶対高さ指定はtable-layout:auto以外の無限成長経路に
	 * なりうることを観測して固定する(外部設計レビュー2026-07-19で発見、P0-2。
	 * 修正の要否は未確定——row-group内の絶対高さ分配には行グループ全体が必要という
	 * 意味では正当な保持だが、文書サイズに比例しうる経路であることは
	 * CSS-SUPPORT.mdに明記する必要がある)。
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
	 * table-layout:fixedの表を、指定行数・(任意で)先頭列のrowspanつきで
	 * 生成する(大規模特性テスト用。golden比較対象ではないため
	 * files/unittestへは置かず、local/unittestへ都度生成する)。
	 *
	 * @param name    生成ファイル名(拡張子なし)
	 * @param rows    総行数
	 * @param rowspan 1なら通常セル、2以上なら先頭行の第1列にrowspanを付与
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
					// rowspanで覆われている行: 第1列のセルは出力しない
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
	 * table-layout:fixedの表を、絶対高さのtbody(1個)で全行を包んで生成する
	 * (P0-2特性テスト用)。
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
		this.transcode(source, name, false);
	}

	private void transcode(File source, String name, boolean strictOnePass) throws Exception {
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
				if (strictOnePass) {
					session.property("processing.strict-one-pass", "true");
				}
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
