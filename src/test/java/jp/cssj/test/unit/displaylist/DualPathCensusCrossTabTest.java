package jp.cssj.test.unit.displaylist;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusEvent;

/** 全コーパスの範囲census。変換失敗も分母へ残す。 */
public final class DualPathCensusCrossTabTest extends TestCase {
	public void testCensusCrossTab() throws Exception {
		final var documents = TwoPassDigestParityTest.corpusDocuments();
		final List<String> failures = new ArrayList<>();
		final StringBuilder rows = new StringBuilder("docPath\tevent\trootKind\tsealAttempted\tsealOutcome\tphase\tbarrierReason\titemKind\tcount\n");
		long converted = 0, rangeBinds = 0, emptyBinds = 0;
		try (final var census = ContinuationStats.beginTwoPassCensus()) {
			for (final var document : documents.entrySet()) {
				ContinuationStats.reset();
				try {
					TwoPassDigestParityTest.transcode(document.getValue());
					RangeOnlyInvariantTest.assertCensus(document.getKey(), census);
					TwoPassFlowSealTest.assertLeaseBalance(document.getKey());
					++converted;
				} catch (final Exception | AssertionError e) {
					failures.add(document.getKey() + ": " + e);
				}
				rangeBinds += ContinuationStats.RANGE_FIRST_BINDS.get();
				emptyBinds += ContinuationStats.TWO_PASS_EMPTY_BINDS.get();
				for (final var event : TwoPassCensusEvent.values()) {
					census.snapshot(event).entrySet().stream()
							.sorted(Comparator.comparing(entry -> entry.getKey().toString())).forEach(entry -> {
								final var key = entry.getKey();
								rows.append(document.getKey()).append('\t').append(event).append('\t')
										.append(key.rootKind()).append('\t').append(key.sealAttempted()).append('\t')
										.append(key.sealOutcome()).append('\t').append(key.measurement() ? "MEASURE" : "MAIN")
										.append('\t').append(key.barrierReason()).append('\t').append(key.itemKind())
										.append('\t').append(entry.getValue()).append('\n');
							});
				}
			}
		} finally {
			ContinuationStats.reset();
		}
		final Path report = Path.of("build/reports/twopass-census");
		Files.createDirectories(report);
		Files.writeString(report.resolve("crosstab.tsv"), rows, StandardCharsets.UTF_8);
		final String summary = "documents=" + converted + "/" + documents.size()
				+ " range=" + rangeBinds + " empty=" + emptyBinds + " failures=" + failures.size();
		System.err.println("[range census] " + summary);
		Files.writeString(report.resolve("summary.md"), summary + "\n" + String.join("\n", failures), StandardCharsets.UTF_8);
		assertFalse("コーパスが空", documents.isEmpty());
		assertTrue(String.join("\n", failures), failures.isEmpty());
		assertEquals(documents.size(), converted);
		assertTrue("範囲bind未観測", rangeBinds > 0);
	}

	public void testCorpusConversionConditions() throws Exception {
		final var documents = TwoPassDigestParityTest.corpusDocuments();
		for (final var doc : DisplayListGoldenTest.corpusDocuments()) {
			assertEquals(doc.path(), doc.passCount(), documents.get(doc.path()).passCount());
		}
		assertEquals(2, documents.get("3000-SELECTOR/has.html").passCount());
		assertEquals(2, documents.get("3000-SELECTOR/last-child-family.html").passCount());
	}
}
