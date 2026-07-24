package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * TwoPass range化(E-6増分4a、2026-07-24新設)のshadow bind比較テストです。
 *
 * <p>
 * 表外float/absolute/inline-blockの{@code TwoPassBlockBuilder}について、
 * 「LegacyRecords(records再演)のbind結果」と「同じLayoutSource範囲の
 * {@code SourceRangeBody}(SegmentExecutor駆動)再生結果」を2回の
 * transcodeで比較し、<b>display listが完全一致</b>することを固定する。
 * 増分4bでrange bindはproduction default-onになったため、baseline側は
 * kill switch({@code foliojet.noTwoPassRangeBind}システムプロパティ)で
 * LegacyRecordsへ退避して比較する。
 * </p>
 *
 * <p>
 * <b>比較を文書全体のdisplay list parityで行う理由</b>: legacyのrecords
 * 再演は記録済みliveボックス(StartFlow/ReplacedEventのインスタンス)を
 * 消費・変異させるため、同一ビルダーに両経路を1回の実行で適用する
 * per-bindのshadow比較は本番状態を壊す(E-3の教訓どおりproductionへの
 * shadow配線も常設しない)。全文書parityは同一入力・同一設定で両経路を
 * 独立に走らせる、より強い比較(bind結果の断片一致は全体一致に含意される)。
 * </p>
 *
 * <p>
 * あわせて「空虚な緑の防止」: range側の実行で(a)seal適格
 * (records解放)が実際に発火したこと、(b)range bindが実際に発火した
 * こと、(c)不適格分類(絶対配置=NO_RANGE、ネストビルダー=
 * NESTED_BUILDER)が実際に計上されることを固定し、適格/不適格の実測を
 * stderrへレポートする。
 * </p>
 */
public class TwoPassRangeBindParityTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * E-6増分4bのrange bind退避スイッチ(TwoPassBlockBuilder.rangeBindEnabled
	 * と対)。baseline側のtranscodeがこれを設定してLegacyRecordsへ退避する。
	 */
	static final String NO_RANGE_BIND_PROPERTY = "foliojet.noTwoPassRangeBind";

	/**
	 * 対象文書。表外float(shrink-to-fit・ネスト・改ページ再生との相互
	 * 作用)・inline-block(置換要素含む)・絶対配置(不適格計上の実測)を
	 * カバーする。
	 */
	private static final String[] DOCUMENTS = { //
			"0120-float/auto-width.html", //
			"0120-float/nested-float-shrink.html", //
			"0120-float/float-in-moved-block.html", //
			"0120-float/collapse-float-measure.html", //
			"0110-clear/break-by-float-float.html", //
			"0380-inline-block/image-in-inline-block.html", //
			"0380-inline-block/inline-block-in-absolute.html", //
			"0170-position/absolute-in-inline.html", //
			"0460-segment-restyle/float-split-in-chain.html", //
	};

	/**
	 * Knuth-Plass行分割({@code text.line-breaker=optimized})下のfloat。
	 * range bindはbind先のBlockBuilder配下でTotalFitSessionを通常構築と
	 * 同じに再駆動する——その決定性の固定。
	 */
	private static final String[] OPTIMIZED_DOCUMENTS = { //
			"3200-line-breaker/parity-float.html", //
	};

	public void testRangeBindMatchesLegacyRecords() throws Exception {
		final List<String> failures = new ArrayList<>();
		long rangeBinds = 0;
		long sealsEligible = 0;
		final Map<ContinuationStats.TwoPassSealReject, Long> rejects = new EnumMap<>(
				ContinuationStats.TwoPassSealReject.class);
		for (final ContinuationStats.TwoPassSealReject r : ContinuationStats.TwoPassSealReject.values()) {
			rejects.put(r, 0L);
		}

		final List<String[]> jobs = new ArrayList<>();
		for (final String doc : DOCUMENTS) {
			jobs.add(new String[] { doc, null });
		}
		for (final String doc : OPTIMIZED_DOCUMENTS) {
			jobs.add(new String[] { doc, "optimized" });
		}
		for (final String[] job : jobs) {
			final String doc = job[0];
			final String lineBreaker = job[1];
			final String name = doc.replace('/', '_').replace(".html", "");
			final File baselineDir = new File("local/unittest/two-pass-range-bind/" + name + "-baseline");
			final File rangeDir = new File("local/unittest/two-pass-range-bind/" + name + "-range");

			// legacy(kill switchで退避)の基準
			ContinuationStats.reset();
			this.dump(doc, name + "-baseline", baselineDir, lineBreaker, false);
			assertEquals(doc + ": kill switch下でrange bindが発火してはならない", 0,
					ContinuationStats.RANGE_FIRST_BINDS.get());
			assertEquals(doc + ": kill switch下でsealが適格になってはならない", 0,
					ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get());

			// range bind(4bのproduction既定)
			ContinuationStats.reset();
			this.dump(doc, name + "-range", rangeDir, lineBreaker, true);
			rangeBinds += ContinuationStats.RANGE_FIRST_BINDS.get();
			sealsEligible += ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get();
			for (final ContinuationStats.TwoPassSealReject r : ContinuationStats.TwoPassSealReject.values()) {
				rejects.merge(r, ContinuationStats.twoPassSealRejects(r), Long::sum);
			}

			// display listの完全一致
			final File[] baselinePages = baselineDir.listFiles((d, n) -> n.endsWith(".txt"));
			final File[] rangePages = rangeDir.listFiles((d, n) -> n.endsWith(".txt"));
			assertNotNull(doc + ": 表示リストが出力されていません", baselinePages);
			assertTrue(doc + ": 表示リストが出力されていません", baselinePages.length > 0);
			if (rangePages == null || baselinePages.length != rangePages.length) {
				failures.add(doc + ": ページ数が一致しません (baseline=" + baselinePages.length + ", range="
						+ (rangePages == null ? 0 : rangePages.length) + ")");
				continue;
			}
			for (final File baseline : baselinePages) {
				final File range = new File(rangeDir, baseline.getName());
				final String expected = Files.readString(baseline.toPath(), StandardCharsets.UTF_8);
				final String got = Files.readString(range.toPath(), StandardCharsets.UTF_8);
				if (!expected.equals(got)) {
					failures.add(doc + "/" + baseline.getName() + ": range bindの出力がLegacyRecordsと一致しません"
							+ " (baseline=" + baseline + ", range=" + range + ")");
				}
			}
		}

		// 空虚な緑の防止: 適格・不適格の両分類が実際に発火している
		assertTrue("range bindが一度も発火していません", rangeBinds > 0);
		assertTrue("seal適格(records解放)が一度も発火していません", sealsEligible > 0);
		assertTrue("絶対配置の不適格(NO_RANGE)が計上されていません",
				rejects.get(ContinuationStats.TwoPassSealReject.NO_RANGE) > 0);
		assertTrue("ネストビルダーの不適格(NESTED_BUILDER)が計上されていません",
				rejects.get(ContinuationStats.TwoPassSealReject.NESTED_BUILDER) > 0);

		// 適格/不適格の実測レポート(表外float/absolute/inline-blockの母数
		// = seal試行の総数)
		long total = sealsEligible;
		final StringBuilder s = new StringBuilder();
		s.append("[E-6 two-pass range bind]\n");
		s.append("  RANGE_FIRST_BINDS=").append(rangeBinds).append('\n');
		s.append("  TWO_PASS_SEALS_ELIGIBLE=").append(sealsEligible).append('\n');
		for (final ContinuationStats.TwoPassSealReject r : ContinuationStats.TwoPassSealReject.values()) {
			total += rejects.get(r);
			s.append("  REJECT_").append(r).append('=').append(rejects.get(r)).append('\n');
		}
		s.append("  ELIGIBLE_RATE=").append(sealsEligible).append('/').append(total).append('\n');
		System.err.print(s);

		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void dump(final String doc, final String name, final File outDir, final String lineBreaker,
			final boolean rangeBind) throws Exception {
		deleteChildren(outDir);
		outDir.mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		if (!rangeBind) {
			// 4bのproduction既定はrange bind。baselineはkill switchで退避
			System.setProperty(NO_RANGE_BIND_PROPERTY, "true");
		}
		try {
			final File pdf = new File("local/unittest/two-pass-range-bind/" + name + ".pdf");
			pdf.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					if (lineBreaker != null) {
						session.property("text.line-breaker", lineBreaker);
					}
					CTISessionHelper.transcodeFile(session, new File("files/unittest/" + doc), "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
			if (!rangeBind) {
				System.clearProperty(NO_RANGE_BIND_PROPERTY);
			}
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
