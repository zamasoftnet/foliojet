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
 * 改ページ・改段の再開操作トレースの golden 比較テストです。
 *
 * <p>
 * Continuation 移行(ARCHITECTURE §5.7)の各スライスに対して、
 * 「再開の操作列(replay/box-restyle の分岐・順序・深さ)が変わらない」
 * ことを固定します。意図的な移行(例: restyle-box → replay-subtree)は
 * 該当 golden を削除して再生成し、差分をレビューしてコミットします。
 * </p>
 */
public class ResumeTraceGoldenTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 対象文書。改ページ・改段・フロート・表・尾部再開をカバーする。 */
	private static final String[] DOCUMENTS = { //
			"0460-segment-restyle/mid-paragraph.html", //
			"0460-segment-restyle/moved-blocks.html", //
			// 脚注(F4/F5): 予約起因のavoid移動と本文短縮の再開操作列を固定
			// (脚注機構の追加でreplay/restyle分岐が意図せず変わらないこと)
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
			// ルビ段落のページまたぎ(2026-07-25、注釈付きテキスト方式)。
			// ルビ単位の文字はCollectorへ横取りされglyph()を通らないため、
			// 尾部再開の位置(単位のソース終端)が壊れていないことをここで
			// 固定する
			"3060-RUBY/ruby-split-through.html", //
	};

	public void testResumeTraces() throws Exception {
		List<String> failures = new ArrayList<>();
		// 2026-07-21(M6b Phase B5d-0)新設のColumnsContainer系カウンタの
		// 固定。B5d本実装の要否判断自体は2026-07-22にclose済みで、現在は
		// ContainerCut.Plainのsentinel解釈差の観測(E-4)として残置——
		// 退役条件はContinuationStats.COLUMNS_LAST_COLUMN_MOVE_CANDIDATE
		// のjavadoc参照。B5c由来のCHAIN_MEMBER_KEEP/MOVE頻度assertは
		// 2026-07-24(E-5)に退役した(Keep/Move経路の挙動自体は本テストの
		// resume-trace golden比較が固定している)。
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
				// 基準データの初回生成
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
		// 2026-07-21(M6b Phase B5d-0): ColumnsContainer(2列以上に実体化
		// 済みの段組)自体がsplitPageAxisを呼ばれる回数自体がこの
		// fixture集合では1回のみ(多くの段組は1列のまま完結し、
		// ColumnsContainerへ遅延ラップされる前にページが閉じるため)。
		// そのうち最後列自体が丸ごとMOVEした候補は0件——B5d(段組全体
		// move型付け)の優先度が低いという既存判断を裏付ける実測値。
		assertEquals("ColumnsContainer.splitPageAxisの呼び出し回数はこのfixture集合で1回のみのはずです", 1,
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
