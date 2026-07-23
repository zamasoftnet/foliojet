package net.zamasoft.foliojet.layout.box.content;

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
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.ResumeTrace;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * B6a1のworklist executor(`FlowContainer.restyleWorklist()`)が、
 * legacy再帰driverと**同一の再開操作トレース**を発行することを検証
 * します(2026-07-22新設)。2つのモードを検証する:
 * {@code -Dfoliojet.openTailExecutor=worklist}(全継続で無条件有効化、
 * 広範囲テスト用)と{@code =eligible}(`RootBuilder`が継続ごとに
 * `PlainFlowTailProgram.allPlainFlow()`を見て限定的に有効化する、
 * 本番ルーティング用の実際の機構)。
 *
 * <p>
 * `ResumeTraceGoldenTest`と同じ文書集合・同じ{@code ResumeTrace}
 * ダンプ機構を使い、同一文書を同一JVM内でlegacy(既定)→各モードの順に
 * transcodeして再開トレースを突き合わせる——codex設計相談の検証計画
 * item3「旧/new別run比較」に対応する(`restyle()`は自身のfloatings/
 * flows等を消費してnull化するため、同一containerへ旧/新を順に適用
 * することはできない——別transcode(別box木)での比較が必須)。
 * </p>
 *
 * <p>
 * 2026-07-22: 初回実装(worklistモードのみ)は`flowStack`不変条件
 * 違反という実バグをこの当時のテストで検出し、修正後に再導入した
 * (`docs/history/2026-07-22-b6a1-worklist-executor-bug-found-and
 * -reverted.md`参照)。
 * </p>
 */
public class WorklistExecutorParityTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** `ResumeTraceGoldenTest`と同じ文書集合(改ページ・改段・フロート・表・尾部再開を広くカバー)。 */
	private static final String[] DOCUMENTS = { //
			"0460-segment-restyle/mid-paragraph.html", //
			"0460-segment-restyle/moved-blocks.html", //
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
	};

	public void testWorklistMatchesLegacyResumeTrace() throws Exception {
		this.assertModeMatchesLegacy("worklist");
	}

	public void testEligibleModeMatchesLegacyResumeTrace() throws Exception {
		this.assertModeMatchesLegacy("eligible");
	}

	private void assertModeMatchesLegacy(final String mode) throws Exception {
		List<String> failures = new ArrayList<>();
		long totalChainFirings = 0;
		for (final String doc : DOCUMENTS) {
			final String name = doc.replace('/', '_').replace(".html", "");
			final File legacyDir = new File("local/unittest/worklist-parity/" + name + "-legacy");
			final File modeDir = new File("local/unittest/worklist-parity/" + name + "-" + mode);
			deleteChildren(legacyDir);
			deleteChildren(modeDir);

			ContinuationStats.reset();
			ResumeTrace.reset();
			// 2026-07-22: `eligible`が既定になったため、真のlegacy基準を
			// 得るには明示的にセットする必要がある(clearPropertyでは
			// 既定=eligibleになってしまい、比較が自明になる)。
			System.setProperty("foliojet.openTailExecutor", "legacy");
			System.setProperty(ResumeTrace.DIR_PROPERTY, legacyDir.getPath());
			try {
				this.transcode(new File("files/unittest/" + doc), name + "-legacy");
			} finally {
				System.clearProperty(ResumeTrace.DIR_PROPERTY);
				System.clearProperty("foliojet.openTailExecutor");
			}
			totalChainFirings += ContinuationStats.RESTYLE_CHAIN_FIRINGS.get();

			ContinuationStats.reset();
			ResumeTrace.reset();
			System.setProperty("foliojet.openTailExecutor", mode);
			System.setProperty(ResumeTrace.DIR_PROPERTY, modeDir.getPath());
			try {
				this.transcode(new File("files/unittest/" + doc), name + "-" + mode);
			} finally {
				System.clearProperty(ResumeTrace.DIR_PROPERTY);
				System.clearProperty("foliojet.openTailExecutor");
			}

			final File[] legacyBreaks = legacyDir.listFiles((d, n) -> n.endsWith(".txt"));
			final File[] modeBreaks = modeDir.listFiles((d, n) -> n.endsWith(".txt"));
			final int legacyCount = legacyBreaks == null ? 0 : legacyBreaks.length;
			final int modeCount = modeBreaks == null ? 0 : modeBreaks.length;
			if (legacyCount != modeCount) {
				failures.add(doc + ": 破断数がlegacy/" + mode + "で異なります (legacy=" + legacyCount + ", " + mode
						+ "=" + modeCount + ")");
				continue;
			}
			for (final File legacy : legacyBreaks) {
				final Path modeFile = new File(modeDir, legacy.getName()).toPath();
				final String expected = Files.readString(legacy.toPath(), StandardCharsets.UTF_8);
				final String got = Files.readString(modeFile, StandardCharsets.UTF_8);
				if (!expected.equals(got)) {
					failures.add(doc + "/" + legacy.getName() + ": 再開トレースがlegacy/" + mode + "で一致しません"
							+ " (legacy=" + legacy + ", " + mode + "=" + modeFile + ")");
				}
			}
		}
		assertTrue("この文書集合ではOpenChainが少なくとも1回は発火するはずです(このテストが空振りしていないことの確認)",
				totalChainFirings > 0);
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void transcode(File source, String name) throws Exception {
		File pdf = new File("local/unittest/worklist-parity/" + name + ".pdf");
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
