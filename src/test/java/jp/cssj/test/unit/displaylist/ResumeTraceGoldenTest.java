package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.fragment.ResumeTrace;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Golden comparison tests for resume operation traces at page and column breaks.
 *
 * <p>
 * For each slice of the Continuation migration (ARCHITECTURE §5.7),
 * verify that "the resume operation sequence (replay/box-restyle branches, order, and depth) stays unchanged."
 * For intentional migrations (e.g., restyle-box → replay-subtree), delete and regenerate
 * the relevant golden, review the diff, and commit it.
 * </p>
 */
public class ResumeTraceGoldenTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Target documents. Cover page breaks, column breaks, floats, tables, and tail resume. */
	private static final String[] DOCUMENTS = { //
			"0460-segment-restyle/mid-paragraph.html", //
			"0460-segment-restyle/moved-blocks.html", //
			// Footnotes (F4/F5): pin down resume sequences for avoid moves caused by reservations and body shortening.
			// (Adding the footnote mechanism must not unintentionally change replay/restyle branches.)
			"0125-footnote/footnote-avoidmove.html", //
			"0125-footnote/footnote-pagelimit.html", //
			"0460-segment-restyle/text-tail-avoid.html", //
			"0460-segment-restyle/float-in-moved.html", //
			"0460-segment-restyle/float-split-in-chain.html", //
			"0460-segment-restyle/float-uncut-before-prefix.html", //
			"0460-segment-restyle/nested-break-in-replay.html", //
			"0460-segment-restyle/moved-table-caption.html", //
			"0120-float/float-in-moved-block.html", //
			"0400-column-count/simple.html", //
			"0400-column-count/columns-float.html", //
			"0215-pagebreak-table/auto-page-break-margin.html", //
			// Ruby paragraphs across pages (2026-07-25, annotated-text approach).
			// Ruby-unit characters are intercepted by the Collector and bypass glyph(), so
			// verify here that the tail resume position (the unit's source end)
			// remains intact.
			"3060-RUBY/ruby-split-through.html", //
	};

	public void testResumeTraces() throws Exception {
		List<String> failures = new ArrayList<>();
		// Pin down the ColumnsContainer counters introduced on 2026-07-21 (M6b Phase B5d-0).
		// The decision on whether to implement B5d was closed on 2026-07-22; these now
		// remain to observe differences in ContainerCut.Plain sentinel interpretation (E-4).
		// See the Javadoc of ContinuationStats.COLUMNS_LAST_COLUMN_MOVE_CANDIDATE
		// for retirement criteria. The B5c-derived CHAIN_MEMBER_KEEP/MOVE frequency assertions
		// were retired on 2026-07-24 (E-5). The Keep/Move paths' behavior itself is pinned down
		// by this test's resume-trace golden comparison.
		long totalColumnsSplitAttempts = 0;
		long totalColumnsLastColumnMoveCandidate = 0;
		for (String doc : DOCUMENTS) {
			String name = doc.replace('/', '_').replace(".html", "");
			File outDir = new File("local/unittest/resume-trace/" + name);
			deleteChildren(outDir);
			File goldenDir = new File("files/unittest/resume-trace-golden/" + name);

			net.zamasoft.foliojet.layout.fragment.ContinuationStats.reset();
			ResumeTrace.reset();
			System.setProperty(ResumeTrace.DIR_PROPERTY, outDir.getPath());
			try {
				this.transcode(new File("files/unittest/" + doc), name);
			} finally {
				System.clearProperty(ResumeTrace.DIR_PROPERTY);
			}

			totalColumnsSplitAttempts += net.zamasoft.foliojet.layout.fragment.ContinuationStats.COLUMNS_SPLIT_ATTEMPTS
					.get();
			totalColumnsLastColumnMoveCandidate += net.zamasoft.foliojet.layout.fragment.ContinuationStats.COLUMNS_LAST_COLUMN_MOVE_CANDIDATE
					.get();
			File[] breaks = outDir.listFiles((d, n) -> n.endsWith(".txt"));
			assertNotNull("再開トレースが出力されていません: " + doc, breaks);
			assertTrue("再開トレースが出力されていません: " + doc, breaks.length > 0);

			if (!goldenDir.isDirectory()) {
				// Generate the baseline data for the first time.
				goldenDir.mkdirs();
				for (File b : breaks) {
					Files.copy(b.toPath(), new File(goldenDir, b.getName()).toPath());
				}
				failures.add(doc + ": 基準データを生成しました。内容を確認してコミットしてください: " + goldenDir);
				continue;
			}

			File[] goldenBreaks = goldenDir.listFiles((d, n) -> n.endsWith(".txt"));
			if (goldenBreaks.length != breaks.length) {
				failures.add(doc + ": 破断数が基準と異なります (golden=" + goldenBreaks.length + ", actual="
						+ breaks.length + ")");
				continue;
			}
			for (File golden : goldenBreaks) {
				Path actual = new File(outDir, golden.getName()).toPath();
				String expected = Files.readString(golden.toPath(), StandardCharsets.UTF_8);
				String got = Files.readString(actual, StandardCharsets.UTF_8);
				if (!expected.equals(got)) {
					failures.add(doc + "/" + golden.getName() + ": 再開トレースが基準と一致しません (expected="
							+ golden + ", actual=" + actual + ")");
				}
			}
		}
		// 2026-07-21 (M6b Phase B5d-0): in this fixture set, splitPageAxis is called
		// on a ColumnsContainer itself (multi-column layout materialized as two or more columns)
		// only once. Most multi-column layouts finish with one column, and the page closes
		// before they are lazily wrapped in a ColumnsContainer.
		// Of those, zero candidates MOVE the entire last column itself. This measurement
		// supports the existing low priority of B5d (typing moves of the entire multi-column layout).
		//
		// **2026-08-06: changed 1→3**. Fixed a defect where an <img> that failed to load
		// ignored CSS width/height and collapsed to 0x0 (HTMLStyle.
		// applyBrokenImage; introduced AltTextImage). The circle.svg referenced by 0400-column-count/
		// columns-float.html never existed in unittest
		// (broken reference), so after the fix, `img{width:20mm}` correctly applies
		// and gives the floating image real dimensions. Multi-column overflow increased
		// the number of actual ColumnsContainer materializations from 1 to 3.
		// (Visually verified; multi-column layout and floats themselves are intact.)
		assertEquals("ColumnsContainer.splitPageAxisの呼び出し回数はこのfixture集合で3回のみのはずです", 3,
				totalColumnsSplitAttempts);
		assertEquals("最後列丸ごとMOVEの候補はこのfixture集合では発生しないはずです", 0,
				totalColumnsLastColumnMoveCandidate);
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void transcode(File source, String name) throws Exception {
		File pdf = new File("local/unittest/resume-trace/" + name + ".pdf");
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

	private static void deleteChildren(File dir) {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			child.delete();
		}
	}
}
