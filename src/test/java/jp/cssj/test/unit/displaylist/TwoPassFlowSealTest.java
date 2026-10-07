package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusEvent;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** Pin down paths for unsealed documents identified in T0. Report observed counts to stderr. */
public final class TwoPassFlowSealTest extends TestCase {
	/** The 17 T0 FLOW_NO_SEAL documents converted to ranges in T1. */
	static final List<String> FLOW_DOCUMENTS = List.of(
			"0390-writing-mode/absolute.html",
			"0390-writing-mode/float-in-flow.html",
			"0390-writing-mode/flow-pagebreak.html",
			"0390-writing-mode/orthogonal-page-axis-percent.html",
			"0390-writing-mode/orthogonal-percent-height.html",
			"0400-column-count/writing-mode-column.html",
			"0400-column-count/writing-mode-column2.html",
			"0480-rescue-split/avoid-chain-fits-alone-horizontal.html",
			"0480-rescue-split/avoid-chain-fits-alone.html",
			"0480-rescue-split/avoid-chain-fitting.html",
			"0480-rescue-split/avoid-chain-oversized-horizontal.html",
			"0480-rescue-split/avoid-chain-oversized.html",
			"3080-MODERN-CSS/width-fit-content.html",
			"3080-MODERN-CSS/width-intrinsic-page-split.html",
			"3080-MODERN-CSS/width-max-content.html",
			"3080-MODERN-CSS/width-min-content.html",
			"3090-bidi/two-pass.html");

	/** In T2, pass the seal proof from a child owning emphasis-mark absolutes to the parent flow. */
	static final List<String> ABSOLUTE_FLOW_DOCUMENTS = List.of(
			"ioprops/text-shadow-vertical-none.html",
			"ioprops/text-shadow-vertical.html");

	static final List<String> SCRATCH_DOCUMENTS = List.of(
			"0070-table-layout/float-in-auto-3.html",
			"0070-table-layout/float-in-auto.html",
			"0240-table/absolute-in-float-in-cell.html");

	/**
	 * A running element whose template contains a table (TwoPass body). This is the only document that uses
	 * independent event replay without a main source (ReplayOnly). The four former T0 NO_SOURCE documents
	 * disappeared with removal of the 3.2 compatibility layer on 2026-09-08.
	 */
	static final List<String> RUNNING_DOCUMENTS = List.of(
			"0370-page-content/running-table.html");

	public void testT0FlowDocumentsUseRanges() throws Exception {
		for (final String doc : FLOW_DOCUMENTS) {
			try (final var census = ContinuationStats.beginTwoPassCensus()) {
				ContinuationStats.reset();
				render(new File("files/unittest", doc));
				report(doc);

				assertTrue(doc + ": range bind未発火", ContinuationStats.RANGE_FIRST_BINDS.get() > 0);
				assertNoRejects(doc);
				RangeOnlyInvariantTest.assertCensus(doc, census);
			}
		}
	}

	public void testT0ScratchFloatsUseRanges() throws Exception {
		for (final String doc : SCRATCH_DOCUMENTS) {
			try (final var census = ContinuationStats.beginTwoPassCensus()) {
				ContinuationStats.reset();
				render(new File("files/unittest", doc));
				report(doc);
				assertNoRejects(doc);

				assertTrue(doc + ": float本文のMEASURE範囲再生が未発火",
						census.snapshot(TwoPassCensusEvent.MEASURE_RANGE).keySet().stream()
								.anyMatch(key -> key.measurement() && (key.rootKind() == TwoPassRootKind.TOPLEVEL
										|| key.rootKind() == TwoPassRootKind.NESTED)));
			}
		}
	}

	public void testT0EmphasisFlowsUseRanges() throws Exception {
		for (final String doc : ABSOLUTE_FLOW_DOCUMENTS) {
			try (final var census = ContinuationStats.beginTwoPassCensus()) {
				ContinuationStats.reset();
				render(new File("files/unittest", doc));
				report(doc);

				assertTrue(doc + ": range bind未発火", ContinuationStats.RANGE_FIRST_BINDS.get() > 0);
				assertTrue(doc, census.snapshot(TwoPassCensusEvent.BIND).keySet().stream()
						.anyMatch(key -> key.sealAttempted() && key.sealOutcome().equals("accepted")));
				assertNoRejects(doc);
				assertFalse(doc, census.snapshot(TwoPassCensusEvent.BIND).keySet().stream()
						.anyMatch(key -> key.sealOutcome().equals("FLOW_NO_SEAL")));
				assertLeaseBalance(doc);
			}
		}
	}

	public void testRunningReplayHasNoSourceReject() throws Exception {
		for (final String doc : RUNNING_DOCUMENTS) {
			ContinuationStats.reset();
			render(new File("files/unittest", doc));
			report(doc);
			assertEquals(doc, 0, ContinuationStats.twoPassSealRejects(TwoPassSealReject.NO_SOURCE));

			assertTrue(doc + ": 独立イベント再生未発火", ContinuationStats.TWO_PASS_REPLAY_ONLY_BINDS.get() > 0);

			assertLeaseBalance(doc);
		}
	}

	/** The state machine alone cannot detect missing termination, so also check balance as a necessary condition. */
	static void assertLeaseBalance(final String doc) {
		assertEquals(doc + ": 未終端のRangeHandle", ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get(),
				ContinuationStats.TWO_PASS_RANGES_CONSUMED.get() + ContinuationStats.TWO_PASS_SEALS_SUBSUMED.get()
						+ ContinuationStats.TWO_PASS_SEALS_ABANDONED.get());
		assertEquals(doc + ": 未終端のセルリース", ContinuationStats.CELL_RANGE_SEALS.get(),
				ContinuationStats.CELL_RANGE_BINDS.get() + ContinuationStats.CELL_RANGE_SEALS_SUBSUMED.get()
						+ ContinuationStats.CELL_RANGE_SEALS_ABANDONED.get());
	}

	private static void assertNoRejects(final String doc) {
		for (final TwoPassSealReject reason : TwoPassSealReject.values()) {
			assertEquals(doc + ": reject=" + reason, 0, ContinuationStats.twoPassSealRejects(reason));
		}
	}

	static void report(final String doc) {
		System.err.println("[T1] " + doc + " seals=" + ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get()
				+ " consumed=" + ContinuationStats.TWO_PASS_RANGES_CONSUMED.get()
				+ " subsumed=" + ContinuationStats.TWO_PASS_SEALS_SUBSUMED.get()
				+ " abandoned=" + ContinuationStats.TWO_PASS_SEALS_ABANDONED.get()
				+ " range=" + ContinuationStats.RANGE_FIRST_BINDS.get()
				+ " replayOnly=" + ContinuationStats.TWO_PASS_REPLAY_ONLY_BINDS.get());
		for (final TwoPassSealReject reason : TwoPassSealReject.values()) {
			final long count = ContinuationStats.twoPassSealRejects(reason);
			if (count != 0) {
				System.err.println("[T1] " + doc + " reject=" + reason + " count=" + count);
			}
		}
	}

	/** Do not retain the PDF; collect only display lists from temporary storage as byte arrays. */
	static List<byte[]> render(final File source) throws Exception {
		return render(source, 1, null);
	}

	static List<byte[]> render(final File source, final int passCount,
			final String defaultStylesheet) throws Exception {
		return render(source, null, passCount, defaultStylesheet);
	}

	static List<byte[]> render(final String html) throws Exception {
		return render(null, html, 1, null);
	}

	private static List<byte[]> render(final File source, final String html,
			final int passCount, final String defaultStylesheet) throws Exception {
		final Path dir = Files.createTempDirectory("foliojet-t1-");
		try (final AutoCloseable output = DisplayListDumper.scopedDir(dir.toString())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(OutputStream.nullOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (passCount > 1) session.property("processing.pass-count", String.valueOf(passCount));
				if (defaultStylesheet != null) session.property("input.default-stylesheet", defaultStylesheet);
				if (source != null) {
					CTISessionHelper.transcodeFile(session, source, "text/html", null);
				} else {
					CTISessionHelper.transcodeStream(session,
							new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
							URI.create("file:/twopass-t1.html"), "text/html", "UTF-8");
				}
			} finally {
				session.close();
			}
			final List<byte[]> pages = new ArrayList<>();
			try (final var paths = Files.list(dir)) {
				for (final Path page : paths.sorted().toList()) {
					pages.add(Files.readAllBytes(page));
				}
			}
			assertFalse(source + ": 表示リストがありません", pages.isEmpty());
			return pages;
		} finally {
			try (final var paths = Files.walk(dir)) {
				for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
	}

	static void assertPagesEqual(final String label, final List<byte[]> expected, final List<byte[]> actual) {
		assertEquals(label + ": ページ数", expected.size(), actual.size());
		for (int i = 0; i < expected.size(); ++i) {
			assertTrue(label + ": page=" + (i + 1), Arrays.equals(expected.get(i), actual.get(i)));
		}
	}
}
