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
 * Shadow validation tests for table Pass B (row measurement) (E-6 increment 5b-1, added 2026-07-24;
 * demonstrates the feasibility of Pass C, incremental per-row commit, in codex design §4.4).
 *
 * <p>
 * Pass C requires that measurements from scratch replay of a cell range at finalized column widths
 * ({@link CellPassBMeasurer}) match the actual dimensions after the final bind exactly.
 * During normal transcoding, this test independently measures each sealed cell of a Retained table
 * just before bind (driving SegmentExecutor on a replica cell box and discarding the tree), then
 * asserts, cell by cell, equality with the actual dimensions immediately after bind
 * (used page-axis size and first ascent).
 * </p>
 *
 * <p>
 * Also locks down non-destructive measurement: the display list from transcoding the same document
 * without shadow measurement exactly matches the display list with shadow measurement.
 * This proves that measurement does not contaminate live layout state, log, or pageGenerator,
 * and implies that Pass B range recapture is a non-destructive read while a lease is held.
 * </p>
 *
 * <p>
 * Prevents vacuous passes: asserts that measurement actually ran (overall and on the scale of all real
 * cells in the 200-row fixture), and reports measurement cost to stderr (the reread cost equivalent to
 * Pass B, to inform the decision whether to adopt Pass C).
 * </p>
 */
public class RetainedCellPassBShadowTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Floating-point comparison tolerance (rerunning the same mechanism should in principle be bit-identical). */
	private static final double EPS = 1e-9;

	/**
	 * Target documents (the table corpus from TwoPassRangeBindParityTest, plus existing fixtures for
	 * rowspan/colspan, percentage heights, specified table heights, vertical writing, and orthogonal cells).
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
			// DP increment 5 (2026-07-30): shadow proof of Pass C eligibility for
			// tables with captions. Caption bind is entirely outside row processing (top =
			// before row-height calculation; bottom = after addBound), so cell Pass B measurements
			// should be bit-identical to actual dimensions from legacy bulk bind.
			"0240-table/float-table-caption.html", //
			"0460-segment-restyle/moved-table-caption.html", //
			// DP increment 6 (2026-07-30): Pass B replica measurement for multi-column cells
			// (td{column-count:2}). The replica starts with an empty FlowContainer, and column-break
			// commit during range replay lazily creates a ColumnsContainer (the same path as live).
			"0400-column-count/table-cell.html", //
	};

	/** Body row and column counts for the 200-row fixture (same structure as RetentionHighWaterReportTest). */
	private static final int GENERATED_ROWS = 200, GENERATED_COLUMNS = 6;

	public void testPassBMeasurementMatchesBind() throws Exception {
		final List<String> failures = new ArrayList<>();
		final StringBuilder report = new StringBuilder("[E-6 table Pass B shadow]\n");
		long totalMeasured = 0;

		final List<String[]> jobs = new ArrayList<>();
		for (final String doc : DOCUMENTS) {
			jobs.add(new String[] { "files/unittest/" + doc, doc, null });
		}
		// 200-row fixture: measure the actual measurement cost and determinism under
		// K-P line breaking (text-wrap-style: pretty).
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

			// Baseline display list without shadow measurement.
			this.dump(path, name + "-baseline", baselineDir, defaultStylesheet, null);

			// Transcode with shadow measurement.
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

			// Non-destructive measurement: the display list matches exactly even with shadow measurement.
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

			// Prevent vacuous passes: measurement runs on the scale of all real cells in the 200-row fixture.
			// (thead 6 + tfoot 6 + body 200*6 - colspan reduction 3 = 1209 cells, all plain
			// text and thus expected to be eligible for sealing).
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
	 * Implements shadow observation. Measures independently with {@link CellPassBMeasurer} just before
	 * bind and compares with the actual dimensions immediately after bind.
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
				// Unsealed cells (retaining records): outside the scope of Pass B.
				++this.legacySkipped;
				return;
			}
			final long t0 = System.nanoTime();
			final CellPassBMeasurer.Result result = CellPassBMeasurer.measure(cell, layoutStack, vertical);
			this.measureNanos += System.nanoTime() - t0;
			if (result == null) {
				// Cannot replicate, e.g. multi-column cells.
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
	 * Generates a 200-row table with table-layout:auto (default), thead/tfoot, and colspan
	 * (same structure as RetentionHighWaterReportTest; generated in local/unittest each time because
	 * it is not a golden comparison target).
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
