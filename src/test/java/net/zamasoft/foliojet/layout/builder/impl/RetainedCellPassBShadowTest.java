package net.zamasoft.foliojet.layout.builder.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import jp.cssj.test.unit.TextWrapStyleOptIn;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 表Pass B(行計測)のshadow検証テストです(E-6増分5b-1、2026-07-24新設——
 * codex設計§4.4のPass C(行単位逐次コミット)成立性の実証)。
 *
 * <p>
 * Pass Cの成立には「確定列幅でセルrangeをscratch再生した計測結果
 * ({@link CellPassBMeasurer})が、最終bindの実寸と完全一致する」ことが
 * 必要。本テストは通常のtranscode中、Retained表の各seal済みセルについて
 * bind直前に独立のscratch計測(複製セルbox上のSegmentExecutor駆動、
 * 木は破棄)を行い、bind直後の実寸(使用ページ方向寸法・first ascent)との
 * 一致をセルごとにassertする。
 * </p>
 *
 * <p>
 * あわせて「計測の非破壊性」を固定する: 同じ文書をshadow計測なしで
 * transcodeしたdisplay listと、shadow計測ありのdisplay listが完全一致する
 * (計測がliveレイアウト状態・log・pageGeneratorを汚さない証拠。
 * Pass Bの範囲再captureがリース保持下の非破壊読みであることも含意)。
 * </p>
 *
 * <p>
 * 空虚な緑の防止: 計測が実際に発火したこと(全体+200行fixtureでの
 * 全実セル規模)をassertし、計測コスト(Pass B相当の再読コスト——Pass C
 * 採否の判断材料)をstderrへレポートする。
 * </p>
 */
public class RetainedCellPassBShadowTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 浮動小数点比較の許容誤差(同一機構の再実行なので原則bit一致のはず)。 */
	private static final double EPS = 1e-9;

	/**
	 * 対象文書(TwoPassRangeBindParityTestの表コーパス+rowspan/colspan/
	 * %高さ/表指定高/縦書き・直交セルの既存fixture)。
	 */
	private static final String[] DOCUMENTS = { //
			"0242-table-height/percent-rowspan-groups.html", //
			"0217-pagebreak-table-row-group/020-HEADER.html", //
			"0218-pagebreak-table-span/090-ROWSPAN.html", //
			"0390-writing-mode/border-collapse.html", //
			"0390-writing-mode/hriz-cell-in-vert.html", //
			"0218-pagebreak-table-span/080-COLSPAN.html", //
			"0242-table-height/row-percent.html", //
			"0242-table-height/table-height.html", //
			"0242-table-height/rowspan.html", //
			"0390-writing-mode/vert-cell-in-hriz.html", //
	};

	/** 200行fixtureの本文行数・列数(RetentionHighWaterReportTestと同型)。 */
	private static final int GENERATED_ROWS = 200, GENERATED_COLUMNS = 6;

	public void testPassBMeasurementMatchesBind() throws Exception {
		final List<String> failures = new ArrayList<>();
		final StringBuilder report = new StringBuilder("[E-6 table Pass B shadow]\n");
		long totalMeasured = 0;

		final List<String[]> jobs = new ArrayList<>();
		for (final String doc : DOCUMENTS) {
			jobs.add(new String[] { "files/unittest/" + doc, doc, null });
		}
		// 200行fixture: 計測コストの実測+K-P行分割(text-wrap-style:
		// pretty)下の決定性
		final File generated = generateAutoTable("e6-passb-auto-table", GENERATED_ROWS, GENERATED_COLUMNS);
		jobs.add(new String[] { generated.getPath(), "generated-200rows", null });
		jobs.add(new String[] { generated.getPath(), "generated-200rows-optimized",
				TextWrapStyleOptIn.PRETTY_STYLESHEET });

		for (final String[] job : jobs) {
			final String path = job[0];
			final String label = job[1];
			final String defaultStylesheet = job[2];
			final String name = label.replace('/', '_').replace(".html", "");
			final File baselineDir = new File("local/unittest/cell-pass-b/" + name + "-baseline");
			final File shadowDir = new File("local/unittest/cell-pass-b/" + name + "-shadow");

			// shadow計測なしの基準display list
			this.dump(path, name + "-baseline", baselineDir, defaultStylesheet, null);

			// shadow計測ありのtranscode
			final Shadow shadow = new Shadow(label);
			RetainedTableBuilder.cellBindShadow = shadow;
			final long wall0 = System.nanoTime();
			try {
				this.dump(path, name + "-shadow", shadowDir, defaultStylesheet, shadow);
			} finally {
				RetainedTableBuilder.cellBindShadow = null;
			}
			final long wallNanos = System.nanoTime() - wall0;

			failures.addAll(shadow.mismatches);
			totalMeasured += shadow.measured;
			report.append("  ").append(label).append(": cells=").append(shadow.measured).append(" legacySkipped=")
					.append(shadow.legacySkipped).append(" replicaSkipped=").append(shadow.replicaSkipped)
					.append(" maxDiff=").append(shadow.maxDiff).append(" measure=")
					.append(shadow.measureNanos / 1_000_000L).append("ms transcode=").append(wallNanos / 1_000_000L)
					.append("ms\n");

			// 計測の非破壊性: shadow計測ありでもdisplay listが完全一致する
			final File[] baselinePages = baselineDir.listFiles((d, n) -> n.endsWith(".txt"));
			final File[] shadowPages = shadowDir.listFiles((d, n) -> n.endsWith(".txt"));
			assertNotNull(label + ": 表示リストが出力されていません", baselinePages);
			assertTrue(label + ": 表示リストが出力されていません", baselinePages.length > 0);
			if (shadowPages == null || baselinePages.length != shadowPages.length) {
				failures.add(label + ": shadow計測でページ数が変わりました (baseline=" + baselinePages.length + ", shadow="
						+ (shadowPages == null ? 0 : shadowPages.length) + ")");
				continue;
			}
			for (final File baseline : baselinePages) {
				final File shadowPage = new File(shadowDir, baseline.getName());
				final String expected = Files.readString(baseline.toPath(), StandardCharsets.UTF_8);
				final String got = Files.readString(shadowPage.toPath(), StandardCharsets.UTF_8);
				if (!expected.equals(got)) {
					failures.add(label + "/" + baseline.getName()
							+ ": shadow計測がdisplay listを変えました(計測の非破壊性の破れ)");
				}
			}

			// 空虚な緑の防止: 200行fixtureでは全実セル規模で計測が発火する
			// (thead 6+tfoot 6+本文 200*6-colspan縮約3=1209セル、全てプレーン
			// テキストでseal適格のはず)
			if (label.startsWith("generated-200rows")) {
				assertTrue(label + ": 200行fixtureの計測発火数が実セル規模に達していません: " + shadow.measured,
						shadow.measured >= 1200);
			}
		}

		assertTrue("Pass B計測が一度も発火していません", totalMeasured > 0);
		System.err.print(report);

		if (!failures.isEmpty()) {
			fail(failures.size() + "件の不一致:\n" + String.join("\n", failures));
		}
	}

	/**
	 * shadow観測の実装です。bind直前に{@link CellPassBMeasurer}で独立計測し、
	 * bind直後の実寸と比較する。
	 */
	private static final class Shadow implements RetainedTableBuilder.CellBindShadow {
		private final String label;
		final List<String> mismatches = new ArrayList<>();
		private final Map<TableCellBox, CellPassBMeasurer.Result> pending = new IdentityHashMap<>();
		long measured, legacySkipped, replicaSkipped, measureNanos;
		double maxDiff;

		Shadow(final String label) {
			this.label = label;
		}

		@Override
		public void beforeCellBind(final CellContent cell, final TableCellBox cellBox, final LayoutStack layoutStack,
				final boolean vertical) {
			if (cell.rangeBody() == null) {
				// 未seal(records保持)セル: Pass B対象外
				++this.legacySkipped;
				return;
			}
			final long t0 = System.nanoTime();
			final CellPassBMeasurer.Result result = CellPassBMeasurer.measure(cell, layoutStack, vertical);
			this.measureNanos += System.nanoTime() - t0;
			if (result == null) {
				// 段組セル等、複製不能
				++this.replicaSkipped;
				return;
			}
			this.pending.put(cellBox, result);
		}

		@Override
		public void afterCellBind(final CellContent cell, final TableCellBox cellBox, final boolean vertical) {
			final CellPassBMeasurer.Result result = this.pending.remove(cellBox);
			if (result == null) {
				return;
			}
			++this.measured;
			final double actualSize = vertical ? cellBox.getWidth() : cellBox.getHeight();
			this.check("pageAxisSize", result.pageAxisSize(), actualSize, cellBox);
			this.check("firstAscent", result.firstAscent(), cellBox.getFirstAscent(), cellBox);
		}

		private void check(final String what, final double measured, final double actual, final TableCellBox cellBox) {
			if (Double.isNaN(measured) && Double.isNaN(actual)) {
				return;
			}
			final double diff = Math.abs(measured - actual);
			if (Double.doubleToLongBits(measured) == Double.doubleToLongBits(actual) || diff <= EPS) {
				if (diff > this.maxDiff) {
					this.maxDiff = diff;
				}
				return;
			}
			this.mismatches.add(this.label + ": " + what + " 不一致 measured=" + measured + " actual=" + actual
					+ " diff=" + diff + " cell=(row " + cellBox.getTableCellPos().rowspan + "r/"
					+ cellBox.getTableCellPos().colspan + "c span, anchor=" + cellBox.getSourceAnchor() + ")");
		}
	}

	/**
	 * table-layout:auto(既定)・thead/tfoot・colspanつきの200行表を生成する
	 * (RetentionHighWaterReportTestと同型。golden比較対象ではないため
	 * local/unittestへ都度生成)。
	 */
	private static File generateAutoTable(final String name, final int bodyRows, final int columns) throws IOException {
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
					w.write("<td colspan=\"2\">span2</td>");
					c = 2;
				} else if (i == 1) {
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

	private void dump(final String sourcePath, final String name, final File outDir,
			final String defaultStylesheet, final Shadow shadow) throws Exception {
		deleteChildren(outDir);
		outDir.mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			final File pdf = new File("local/unittest/cell-pass-b/" + name + ".pdf");
			pdf.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					if (defaultStylesheet != null) {
						session.property("input.default-stylesheet", defaultStylesheet);
					}
					CTISessionHelper.transcodeFile(session, new File(sourcePath), "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}
	}

	private static void deleteChildren(final File dir) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			child.delete();
		}
	}
}
