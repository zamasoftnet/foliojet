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
import jp.cssj.test.unit.TextWrapStyleOptIn;
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
 * こと、(c)ネストビルダーの親range化への吸収(DP増分3、2026-07-30——
 * 旧NESTED_BUILDER rejectの解消)が実際に発火し、このコーパスで
 * NESTED_BUILDERが残らないこと、(d)絶対配置のrecipe記録化(E-6増分4e)後
 * このコーパスでNO_RANGEが残らないこと、(e)seal:bind:吸収のリース収支
 * (seals == binds + subsumed)を固定し、適格/不適格の実測をstderrへ
 * レポートする。
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
	 * 作用)・inline-block(置換要素含む)・絶対配置(不適格計上の実測)、
	 * E-6増分5aからはRetained表のセル(rowspan・%行高・thead反復+改ページ・
	 * rowspanのページ跨ぎ分割・縦書き+つぶし境界・直交セル)もカバーする
	 * (kill switchはセルsealも同時に無効化するため、baseline側は従来の
	 * records再演になる)。キャプション付きの表はこのコーパスへ入れない
	 * こと——キャプションはOpaque記録のためNO_RANGE==0の固定が壊れる
	 * (セル自身はTABLE_CELL recipe記録のためNO_RANGEにならない)。
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
			"0242-table-height/percent-rowspan-groups.html", //
			"0217-pagebreak-table-row-group/020-HEADER.html", //
			"0218-pagebreak-table-span/090-ROWSPAN.html", //
			"0390-writing-mode/border-collapse.html", //
			"0390-writing-mode/hriz-cell-in-vert.html", //
			// DP増分4(2026-07-30): MIXED_FLOW_RANGE reject撤去のパリティ
			// 固定——絶対配置内の縦横混在(旧来この文書だけがrejectしていた)
			"0390-writing-mode/absolute.html", //
			// E-6増分5a回帰(2026-07-24、040-8BITS_ASCII.htmlのNPE):
			// soft hyphen(U+00AD)のみのセルはtextShaperだけが作られ
			// ビルダーへ何も届かない——bind時のclose連鎖の空flushの固定
			// (BlockBuilder.flushのnullガード)。制御文字・ゼロ幅文字の
			// セルのlive/replay対称性も同時にカバーする
			"0240-table/cell-control-chars.html", //
	};

	/**
	 * Knuth-Plass行分割(CSS {@code text-wrap-style: pretty})下のfloat。
	 * range bindはbind先のBlockBuilder配下でTotalFitSessionを通常構築と
	 * 同じに再駆動する——その決定性の固定。オプトインは著者スタイル
	 * シート({@link TextWrapStyleOptIn#PRETTY_STYLESHEET})で与える。
	 */
	private static final String[] OPTIMIZED_DOCUMENTS = { //
			"3200-line-breaker/parity-float.html", //
	};

	public void testRangeBindMatchesLegacyRecords() throws Exception {
		final List<String> failures = new ArrayList<>();
		long rangeBinds = 0;
		long sealsEligible = 0;
		long sealsSubsumed = 0;
		long cellSeals = 0;
		long cellRangeBinds = 0;
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
			jobs.add(new String[] { doc, TextWrapStyleOptIn.PRETTY_STYLESHEET });
		}
		for (final String[] job : jobs) {
			final String doc = job[0];
			final String defaultStylesheet = job[1];
			final String name = doc.replace('/', '_').replace(".html", "");
			final File baselineDir = new File("local/unittest/two-pass-range-bind/" + name + "-baseline");
			final File rangeDir = new File("local/unittest/two-pass-range-bind/" + name + "-range");

			// legacy(kill switchで退避)の基準
			ContinuationStats.reset();
			this.dump(doc, name + "-baseline", baselineDir, defaultStylesheet, false);
			assertEquals(doc + ": kill switch下でrange bindが発火してはならない", 0,
					ContinuationStats.RANGE_FIRST_BINDS.get());
			assertEquals(doc + ": kill switch下でsealが適格になってはならない", 0,
					ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get());
			assertEquals(doc + ": kill switch下でセルsealが発火してはならない(E-6増分5a)", 0,
					ContinuationStats.CELL_RANGE_SEALS.get());

			// range bind(4bのproduction既定)
			ContinuationStats.reset();
			this.dump(doc, name + "-range", rangeDir, defaultStylesheet, true);
			rangeBinds += ContinuationStats.RANGE_FIRST_BINDS.get();
			sealsEligible += ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get();
			sealsSubsumed += ContinuationStats.TWO_PASS_SEALS_SUBSUMED.get();
			cellSeals += ContinuationStats.CELL_RANGE_SEALS.get();
			cellRangeBinds += ContinuationStats.CELL_RANGE_BINDS.get();
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

		// 空虚な緑の防止: 適格・不適格の両分類が実際に発火している。
		// E-6増分4e: 絶対配置はrecipe記録化により適格になった(旧「絶対配置
		// =NO_RANGE」の検証は撤去)。このコーパスでNO_RANGEが残らないこと
		// 自体が4eの適格化の証拠になるため0を固定する
		assertTrue("range bindが一度も発火していません", rangeBinds > 0);
		assertTrue("seal適格(records解放)が一度も発火していません", sealsEligible > 0);
		// E-6増分5a: 表セルのrange化がこのコーパスで実際に発火し、seal数と
		// bind数が一致する(リース1:1——取り残しはcompactを永久にclampする)
		assertTrue("表セルのrange seal(E-6増分5a)が一度も発火していません", cellSeals > 0);
		assertEquals("セルseal数とセルrange bind数が一致しません(セルのリース取り残しの疑い)", cellSeals,
				cellRangeBinds);
		assertEquals("絶対配置のrecipe記録化(4e)後、このコーパスでNO_RANGEは残らないはずです", 0,
				(long) rejects.get(ContinuationStats.TwoPassSealReject.NO_RANGE));
		// DP増分3(2026-07-30): 非表のネストビルダーは親range化に吸収される
		// ようになった——このコーパス(表を含む範囲はOPAQUEゲートが先に弾く)
		// でNESTED_BUILDERは残らず、吸収が実際に発火する
		assertEquals("nested lease吸収(DP増分3)後、非表ネストはNESTED_BUILDERにならないはずです", 0,
				(long) rejects.get(ContinuationStats.TwoPassSealReject.NESTED_BUILDER));
		assertTrue("nested吸収(TWO_PASS_SEALS_SUBSUMED)が一度も発火していません(空虚な緑)", sealsSubsumed > 0);
		assertEquals("seal適格数とrange bind+吸収数が一致しません(リース取り残しの疑い)", sealsEligible,
				rangeBinds + sealsSubsumed);

		// 適格/不適格の実測レポート(表外float/absolute/inline-blockの母数
		// = seal試行の総数)
		long total = sealsEligible;
		final StringBuilder s = new StringBuilder();
		s.append("[E-6 two-pass range bind]\n");
		s.append("  RANGE_FIRST_BINDS=").append(rangeBinds).append('\n');
		s.append("  TWO_PASS_SEALS_ELIGIBLE=").append(sealsEligible).append('\n');
		s.append("  TWO_PASS_SEALS_SUBSUMED=").append(sealsSubsumed).append('\n');
		s.append("  CELL_RANGE_SEALS=").append(cellSeals).append('\n');
		s.append("  CELL_RANGE_BINDS=").append(cellRangeBinds).append('\n');
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

	private void dump(final String doc, final String name, final File outDir, final String defaultStylesheet,
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
					if (defaultStylesheet != null) {
						session.property("input.default-stylesheet", defaultStylesheet);
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
