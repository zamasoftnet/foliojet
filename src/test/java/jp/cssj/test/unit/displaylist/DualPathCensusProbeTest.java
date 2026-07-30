package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * dual path(PLAN §2)のlegacy発火をコーパス全体で定量化するcensusです
 * (2026-07-30、DP増分0)。増分ごとの縮退を[DP-TOTAL]行で実測する
 * ワークストリームの計測装置——数値の固定(不変条件)は
 * {@code DisplayListGoldenTest}のseal:bind 1:1 assert群が担い、こちらは
 * 全体量の観測に徹する(コーパス追加で自然に動く値をassertしない)。
 *
 * <p>
 * 実測推移(467文書、LEGACY_RECORD_BINDS): 開始時729 → DP増分1
 * (Incrementalセルseal)137 → DP増分2(EmptyBody)66。
 * </p>
 */
public class DualPathCensusProbeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testCensus() throws Exception {
		final File root = new File("files/unittest");
		final File[] dirFiles = root.listFiles(File::isDirectory);
		assertNotNull(dirFiles);
		java.util.Arrays.sort(dirFiles);
		long totalRange = 0, totalLegacy = 0, totalCellRange = 0, totalCellLegacy = 0, totalPassC = 0,
				totalLegacyRows = 0, totalDocs = 0, legacyDocs = 0;
		final java.util.Map<ContinuationStats.TwoPassSealReject, Long> rejects = new java.util.EnumMap<>(
				ContinuationStats.TwoPassSealReject.class);
		for (final ContinuationStats.TwoPassSealReject r : ContinuationStats.TwoPassSealReject.values()) {
			rejects.put(r, 0L);
		}
		final java.util.Map<ContinuationStats.LegacyBindOrigin, Long> origins = new java.util.EnumMap<>(
				ContinuationStats.LegacyBindOrigin.class);
		for (final ContinuationStats.LegacyBindOrigin o : ContinuationStats.LegacyBindOrigin.values()) {
			origins.put(o, 0L);
		}
		for (final File d : dirFiles) {
			final File[] files = d.listFiles((x, n) -> n.endsWith(".html"));
			if (files == null) {
				continue;
			}
			java.util.Arrays.sort(files);
			for (final File f : files) {
				ContinuationStats.reset();
				try {
					transcode(f);
				} catch (final Exception e) {
					System.out.println("[DP] " + d.getName() + "/" + f.getName() + " EXCEPTION " + e);
					continue;
				}
				++totalDocs;
				final long legacy = ContinuationStats.LEGACY_RECORD_BINDS.get();
				final long cellLegacy = ContinuationStats.CELL_LEGACY_BINDS.get();
				final long legacyRows = ContinuationStats.TABLE_LEGACY_BINDROWS.get();
				totalRange += ContinuationStats.RANGE_FIRST_BINDS.get();
				totalLegacy += legacy;
				totalCellRange += ContinuationStats.CELL_RANGE_BINDS.get();
				totalCellLegacy += cellLegacy;
				totalPassC += ContinuationStats.TABLE_PASS_C_TABLES.get();
				totalLegacyRows += legacyRows;
				for (final ContinuationStats.TwoPassSealReject r : ContinuationStats.TwoPassSealReject.values()) {
					rejects.merge(r, ContinuationStats.twoPassSealRejects(r), Long::sum);
				}
				for (final ContinuationStats.LegacyBindOrigin o : ContinuationStats.LegacyBindOrigin.values()) {
					origins.merge(o, ContinuationStats.legacyRecordBinds(o), Long::sum);
				}
				if (legacy > 0 || cellLegacy > 0 || legacyRows > 0) {
					++legacyDocs;
					System.out.println("[DP] " + d.getName() + "/" + f.getName() + " legacyBind=" + legacy
							+ " cellLegacy=" + cellLegacy + " legacyRows=" + legacyRows);
				}
			}
		}
		System.out.println("[DP-TOTAL] docs=" + totalDocs + " legacyDocs=" + legacyDocs);
		System.out.println("[DP-TOTAL] 2pass: rangeBinds=" + totalRange + " legacyRecordBinds=" + totalLegacy);
		System.out.println("[DP-TOTAL] cell: rangeBinds=" + totalCellRange + " legacyBinds=" + totalCellLegacy);
		System.out.println("[DP-TOTAL] table: passC=" + totalPassC + " legacyBindRows=" + totalLegacyRows);
		System.out.println("[DP-TOTAL] sealRejects=" + rejects);
		System.out.println("[DP-TOTAL] legacyOrigins=" + origins);
	}

	private void transcode(File source) throws Exception {
		File pdf = new File("local/unittest/continuation/dp-probe.pdf");
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
