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
import net.zamasoft.foliojet.layout.builder.impl.TableRetentionReason;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.ua.SelectorFacts;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 保持系(RetainedTableBuilder / TwoPassBlockBuilder / LayoutSource /
 * SelectorFacts)の保持量high-waterのレポートテストです(E-6増分1、
 * 2026-07-24。spillableテープ基盤のspill閾値・対象選定の実測基盤——
 * docs/consultations/consult-e6-spillable-tape-codex.md §3-1)。
 *
 * <p>
 * TableBuildCharacterizationTestと同じ「空虚な緑の防止」: 観測カウンタが
 * 実際に配線されている(既知の文書で発火する)ことを固定し、あわせて
 * 現在値をstderrへレポートする。E-6の後続増分(shadow比較・切替)で
 * カウンタが死んだ場合、このテストが検出する。
 * </p>
 */
public class RetentionHighWaterReportTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * auto表(thead/tfoot・colspanつき)で、Retained保持形状・TwoPass・
	 * LayoutSourceの各high-waterが観測されることを固定する。
	 */
	public void testRetainedAutoTableHighWaterObserved() throws Exception {
		final int bodyRows = 200;
		final int columns = 6;
		final long autoBefore = TableBuildStats.retentionReasonCount(TableRetentionReason.AUTO_COLUMNS);
		final long cellSealsBefore = ContinuationStats.CELL_RANGE_SEALS.get();
		final long cellRangeBindsBefore = ContinuationStats.CELL_RANGE_BINDS.get();
		final long passCTablesBefore = ContinuationStats.TABLE_PASS_C_TABLES.get();
		final long passBMeasuresBefore = ContinuationStats.TABLE_PASS_B_CELL_MEASURES.get();
		final File doc = generateAutoTable("e6-hw-auto-table", bodyRows, columns);
		this.transcode(doc, "e6-hw-auto-table", 1);

		assertTrue("auto表がRetained(AUTO_COLUMNS)として記録されていません",
				TableBuildStats.retentionReasonCount(TableRetentionReason.AUTO_COLUMNS) > autoBefore);
		assertTrue("Retained行数high-waterが観測されていません: " + TableBuildStats.RETAINED_ROW_HIGH_WATER.get(),
				TableBuildStats.RETAINED_ROW_HIGH_WATER.get() >= bodyRows);
		assertTrue("Retained論理セルslot数high-waterが観測されていません: "
				+ TableBuildStats.RETAINED_CELL_SLOT_HIGH_WATER.get(),
				TableBuildStats.RETAINED_CELL_SLOT_HIGH_WATER.get() >= (long) bodyRows * columns);
		assertTrue("Retained実セル数high-waterが観測されていません: " + TableBuildStats.RETAINED_CELL_HIGH_WATER.get(),
				TableBuildStats.RETAINED_CELL_HIGH_WATER.get() >= bodyRows);
		assertTrue("反復ヘッダ行数high-waterが観測されていません",
				TableBuildStats.RETAINED_REPEATED_HEADER_ROW_HIGH_WATER.get() >= 1);
		assertTrue("反復フッタ行数high-waterが観測されていません",
				TableBuildStats.RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER.get() >= 1);
		assertTrue("colspan制約数(Uspan)high-waterが観測されていません: "
				+ TableBuildStats.RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER.get(),
				TableBuildStats.RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER.get() >= 2);
		assertTrue("TwoPass recordsのhigh-waterが観測されていません",
				TableBuildStats.TWO_PASS_RECORD_HIGH_WATER.get() > 0);
		assertTrue("TwoPass glyph数のhigh-waterが観測されていません",
				TableBuildStats.TWO_PASS_GLYPH_HIGH_WATER.get() > 0);
		assertTrue("TwoPassネスト深さのhigh-waterが観測されていません",
				TableBuildStats.TWO_PASS_NEST_DEPTH_HIGH_WATER.get() >= 1);
		assertTrue("LayoutSourceイベント数のhigh-waterが観測されていません",
				ContinuationStats.SOURCE_EVENT_HIGH_WATER.get() > 0);

		// E-6増分5a: セルclose時のrange sealがこの表の全実セル規模で発火し
		// (プレーンテキストセルは全て適格のはず)、seal数とbind数が一致する
		// (リース1:1)。kill switch(foliojet.noTwoPassRangeBind)下では
		// sealが起きないため検証をスキップする
		if (!Boolean.getBoolean("foliojet.noTwoPassRangeBind")) {
			final long cellSeals = ContinuationStats.CELL_RANGE_SEALS.get() - cellSealsBefore;
			final long cellRangeBinds = ContinuationStats.CELL_RANGE_BINDS.get() - cellRangeBindsBefore;
			assertTrue("セルrange seal(E-6増分5a)がauto表の実セル規模で発火していません: " + cellSeals,
					cellSeals >= bodyRows);
			assertEquals("セルseal数とセルrange bind数が一致しません(リース取り残しの疑い)", cellSeals,
					cellRangeBinds);

			// E-6増分5b-2: 全実セルがseal適格のこの表はPass C(行単位逐次bind)で
			// 処理され、Pass B(行計測)が実セル規模で発火する——「行高計算中は
			// bind済みセル本文木ゼロ(計測木は都度破棄)」の観測指標
			assertTrue("auto表が表Pass C(行単位逐次bind)で処理されていません",
					ContinuationStats.TABLE_PASS_C_TABLES.get() > passCTablesBefore);
			final long passBMeasures = ContinuationStats.TABLE_PASS_B_CELL_MEASURES.get() - passBMeasuresBefore;
			assertTrue("表Pass B(行計測)がauto表の実セル規模で発火していません: " + passBMeasures,
					passBMeasures >= bodyRows);
		}

		report();
	}

	/**
	 * STRUCTURE_SCAN(:last-child系)と:has()で、SelectorFactsの各Map/Setの
	 * エントリ数high-waterが観測されることを固定する。
	 */
	public void testSelectorFactsHighWaterObserved() throws Exception {
		this.transcode(new File("files/unittest/3000-SELECTOR/last-child-family.html"), "e6-hw-last-child-family", 2);
		this.transcode(new File("files/unittest/3000-SELECTOR/has.html"), "e6-hw-has", 2);

		assertTrue("lastChildのhigh-waterが観測されていません", SelectorFacts.LAST_CHILD_HIGH_WATER.get() > 0);
		assertTrue("lastOfTypeのhigh-waterが観測されていません", SelectorFacts.LAST_OF_TYPE_HIGH_WATER.get() > 0);
		assertTrue("positionFromEndのhigh-waterが観測されていません",
				SelectorFacts.POSITION_FROM_END_HIGH_WATER.get() > 0);
		assertTrue("typePositionFromEndのhigh-waterが観測されていません",
				SelectorFacts.TYPE_POSITION_FROM_END_HIGH_WATER.get() > 0);
		assertTrue(":has()要素数のhigh-waterが観測されていません",
				SelectorFacts.HAS_MATCH_ELEMENT_HIGH_WATER.get() > 0);
		assertTrue(":has()ペア数のhigh-waterが観測されていません", SelectorFacts.HAS_MATCH_PAIR_HIGH_WATER.get() > 0);

		report();
	}

	/** 現在のhigh-water値をstderrへレポートする(このJVMで走った全テストの累積)。 */
	private static void report() {
		final StringBuilder s = new StringBuilder();
		s.append("[E-6 retention high-water]\n");
		s.append("  RETAINED_ROW_HIGH_WATER=").append(TableBuildStats.RETAINED_ROW_HIGH_WATER.get()).append('\n');
		s.append("  RETAINED_CELL_HIGH_WATER=").append(TableBuildStats.RETAINED_CELL_HIGH_WATER.get()).append('\n');
		s.append("  RETAINED_CELL_SLOT_HIGH_WATER=").append(TableBuildStats.RETAINED_CELL_SLOT_HIGH_WATER.get())
				.append('\n');
		s.append("  RETAINED_REPEATED_HEADER_ROW_HIGH_WATER=")
				.append(TableBuildStats.RETAINED_REPEATED_HEADER_ROW_HIGH_WATER.get()).append('\n');
		s.append("  RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER=")
				.append(TableBuildStats.RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER.get()).append('\n');
		s.append("  RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER=")
				.append(TableBuildStats.RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER.get()).append('\n');
		s.append("  RETAINED_CELL_GLYPH_HIGH_WATER=").append(TableBuildStats.RETAINED_CELL_GLYPH_HIGH_WATER.get())
				.append('\n');
		s.append("  CELL_RANGE_SEALS=").append(ContinuationStats.CELL_RANGE_SEALS.get()).append('\n');
		s.append("  CELL_RANGE_BINDS=").append(ContinuationStats.CELL_RANGE_BINDS.get()).append('\n');
		s.append("  CELL_LEGACY_BINDS=").append(ContinuationStats.CELL_LEGACY_BINDS.get()).append('\n');
		s.append("  TABLE_PASS_C_TABLES=").append(ContinuationStats.TABLE_PASS_C_TABLES.get()).append('\n');
		s.append("  TABLE_LEGACY_BINDROWS=").append(ContinuationStats.TABLE_LEGACY_BINDROWS.get()).append('\n');
		s.append("  TABLE_PASS_B_CELL_MEASURES=").append(ContinuationStats.TABLE_PASS_B_CELL_MEASURES.get())
				.append('\n');
		for (final TableRetentionReason reason : TableRetentionReason.values()) {
			s.append("  RETENTION_REASON_").append(reason).append('=')
					.append(TableBuildStats.retentionReasonCount(reason)).append('\n');
		}
		s.append("  TWO_PASS_RECORD_HIGH_WATER=").append(TableBuildStats.TWO_PASS_RECORD_HIGH_WATER.get()).append('\n');
		s.append("  TWO_PASS_GLYPH_HIGH_WATER=").append(TableBuildStats.TWO_PASS_GLYPH_HIGH_WATER.get()).append('\n');
		s.append("  TWO_PASS_NEST_DEPTH_HIGH_WATER=").append(TableBuildStats.TWO_PASS_NEST_DEPTH_HIGH_WATER.get())
				.append('\n');
		s.append("  SOURCE_EVENT_HIGH_WATER=").append(ContinuationStats.SOURCE_EVENT_HIGH_WATER.get()).append('\n');
		s.append("  SELECTOR_LAST_CHILD_HIGH_WATER=").append(SelectorFacts.LAST_CHILD_HIGH_WATER.get()).append('\n');
		s.append("  SELECTOR_LAST_OF_TYPE_HIGH_WATER=").append(SelectorFacts.LAST_OF_TYPE_HIGH_WATER.get())
				.append('\n');
		s.append("  SELECTOR_EMPTY_HIGH_WATER=").append(SelectorFacts.EMPTY_HIGH_WATER.get()).append('\n');
		s.append("  SELECTOR_POSITION_FROM_END_HIGH_WATER=").append(SelectorFacts.POSITION_FROM_END_HIGH_WATER.get())
				.append('\n');
		s.append("  SELECTOR_TYPE_POSITION_FROM_END_HIGH_WATER=")
				.append(SelectorFacts.TYPE_POSITION_FROM_END_HIGH_WATER.get()).append('\n');
		s.append("  SELECTOR_HAS_MATCH_ELEMENT_HIGH_WATER=").append(SelectorFacts.HAS_MATCH_ELEMENT_HIGH_WATER.get())
				.append('\n');
		s.append("  SELECTOR_HAS_MATCH_PAIR_HIGH_WATER=").append(SelectorFacts.HAS_MATCH_PAIR_HIGH_WATER.get())
				.append('\n');
		System.err.print(s);
	}

	/**
	 * table-layout:auto(既定)の表を、thead/tfoot・colspan(2種類の
	 * (開始列,colspan)制約)つきで生成する。golden比較対象ではないため
	 * files/unittestへは置かずlocal/unittestへ都度生成する。
	 */
	private static File generateAutoTable(String name, int bodyRows, int columns) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"400pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}td,th{border:1pt solid black}</style>\n");
			w.write("</head><body><table>\n");
			w.write("<thead><tr>");
			for (int c = 0; c < columns; ++c) {
				w.write("<th>h" + c + "</th>");
			}
			w.write("</tr></thead>\n");
			w.write("<tfoot><tr>");
			for (int c = 0; c < columns; ++c) {
				w.write("<td>f" + c + "</td>");
			}
			w.write("</tr></tfoot>\n");
			w.write("<tbody>\n");
			for (int i = 0; i < bodyRows; ++i) {
				w.write("<tr>");
				int c = 0;
				if (i == 0) {
					// (開始列0, colspan2)の制約
					w.write("<td colspan=\"2\">span2</td>");
					c = 2;
				} else if (i == 1) {
					// (開始列0, colspan3)の制約
					w.write("<td colspan=\"3\">span3</td>");
					c = 3;
				}
				for (; c < columns; ++c) {
					w.write("<td>r" + i + "c" + c + "</td>");
				}
				w.write("</tr>\n");
			}
			w.write("</tbody></table></body></html>\n");
		}
		return file;
	}

	private void transcode(File source, String name, int passCount) throws Exception {
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
				if (passCount > 1) {
					session.property("processing.pass-count", String.valueOf(passCount));
				}
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
