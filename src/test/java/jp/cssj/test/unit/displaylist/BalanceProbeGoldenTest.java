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
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code processing.balance-probe=true}(M6c-4実採用、2026-07-24)の
 * display list goldenテストです。
 *
 * <p>
 * <b>採用対象文書</b>(codex設計§1.8のfixture集合: 横書き/縦書きbalance・
 * 外側floatで実幅が狭くなる{@code columns-float}・強制改段・
 * indivisible/widows/orphans/avoid・空/1段/複数段済み)は、プローブ有効
 * 時のレイアウトを{@code files/unittest/display-list-golden-balance-probe/}
 * のgoldenで固定する(初回生成→目視確認→以後回帰検出)。各文書で
 * commitが実際に発生したこと(カウンタ)も検証する。
 * </p>
 *
 * <p>
 * <b>フォールバック対象文書</b>(段組内float・入れ子段組=プローブ
 * 不適格)は、同一文書を「プローブ無効」「有効」で二重変換し、
 * display listがバイト一致することを直接検証する——「不適格時の
 * フォールバック無害性」(2026-07-24の堅牢性較正で最重要とされた性質)の
 * 機械的証明。goldenは不要。
 * </p>
 */
public class BalanceProbeGoldenTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * プローブが実採用(commit)するはずの文書(一部のbalance呼び出しが
	 * フォールバックする混在文書を含む——probe-edge-casesの空multicol、
	 * probe-nestedの外側multicol(入れ子含みで不適格。内側は適格で採用))。
	 */
	private static final String[] ADOPTED_DOCUMENTS = { //
			"0415-column-fill/h-balance.html", // 横書きbalance
			"0415-column-fill/v-balance.html", // 縦書きbalance
			"0415-column-fill/probe-forced-break.html", // 強制改段
			"0415-column-fill/probe-avoid-orphans.html", // widows/orphans/avoid
			"0415-column-fill/probe-edge-cases.html", // 空(fallback)/1段/複数段済み
			"0415-column-fill/probe-nested.html", // 入れ子段組(外側fallback+内側採用)
			"0400-column-count/columns-float.html", // 外側floatで実幅が狭くなる反例
	};

	/**
	 * 全balance呼び出しがプローブ不適格でlegacyへフォールバックするはずの
	 * 文書(段組内float・column-span分割で部分木が開いたままのbalance・
	 * anchorなしの再構成multicol——M6c-5まで全て不適格)。
	 */
	private static final String[] FALLBACK_DOCUMENTS = { //
			"0415-column-fill/float-in-flow.html", //
	};

	/** 採用対象: プローブ有効時のレイアウトをgoldenで固定+commit発生を確認。 */
	public void testAdoptedDocumentsMatchGolden() throws Exception {
		final List<String> failures = new ArrayList<>();
		for (final String doc : ADOPTED_DOCUMENTS) {
			final String name = doc.replace('/', '_').replace(".html", "");
			final File outDir = new File("local/unittest/display-list-balance-probe/" + name);
			deleteChildren(outDir);
			ContinuationStats.reset();
			this.transcode(doc, name + "-on", outDir, true);
			if (ContinuationStats.BALANCE_PROBE_COMMITS.get() < 1) {
				failures.add(doc + ": プローブのcommitが1回も発生していません (sessions="
						+ ContinuationStats.BALANCE_PROBE_SESSIONS.get() + ", fallbacks="
						+ ContinuationStats.BALANCE_PROBE_FALLBACKS.get() + ")");
			}
			if (ContinuationStats.BALANCE_PROBE_SESSIONS.get() != ContinuationStats.BALANCE_PROBE_COMMITS.get()
					+ ContinuationStats.BALANCE_PROBE_FALLBACKS.get()) {
				failures.add(doc + ": sessions != commits + fallbacks");
			}
			this.compareWithGolden(doc, outDir, new File("files/unittest/display-list-golden-balance-probe/" + name),
					failures);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	/**
	 * フォールバック対象: プローブ有効でもdisplay listが無効時と完全に
	 * 一致する(不適格文書へ一切の漏れ出しがない)。
	 */
	public void testFallbackDocumentsAreUnaffected() throws Exception {
		final List<String> failures = new ArrayList<>();
		for (final String doc : FALLBACK_DOCUMENTS) {
			final String name = doc.replace('/', '_').replace(".html", "");
			final File offDir = new File("local/unittest/display-list-balance-probe/" + name + "-off");
			final File onDir = new File("local/unittest/display-list-balance-probe/" + name + "-on");
			deleteChildren(offDir);
			deleteChildren(onDir);
			this.transcode(doc, name + "-off", offDir, false);
			ContinuationStats.reset();
			this.transcode(doc, name + "-on", onDir, true);
			assertTrue(doc + ": プローブが起動していません(fixtureがbalance対象外?)",
					ContinuationStats.BALANCE_PROBE_SESSIONS.get() >= 1);
			assertEquals(doc + ": 不適格文書でcommitが発生してはいけません", 0,
					ContinuationStats.BALANCE_PROBE_COMMITS.get());
			this.compareDirs(doc, offDir, onDir, failures);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void compareWithGolden(final String doc, final File outDir, final File goldenDir,
			final List<String> failures) throws Exception {
		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + doc, pages);
		assertTrue("表示リストが出力されていません: " + doc, pages.length > 0);
		if (!goldenDir.isDirectory()) {
			// 基準データの初回生成(DisplayListGoldenTestと同じ運用)
			goldenDir.mkdirs();
			for (final File page : pages) {
				Files.copy(page.toPath(), new File(goldenDir, page.getName()).toPath());
			}
			failures.add(doc + ": 基準データを生成しました。内容を確認してコミットしてください: " + goldenDir);
			return;
		}
		final File[] goldenPages = goldenDir.listFiles((d, n) -> n.endsWith(".txt"));
		if (goldenPages.length != pages.length) {
			failures.add(doc + ": ページ数が基準と異なります (golden=" + goldenPages.length + ", actual=" + pages.length + ")");
			return;
		}
		for (final File golden : goldenPages) {
			final Path actual = new File(outDir, golden.getName()).toPath();
			final String expected = Files.readString(golden.toPath(), StandardCharsets.UTF_8);
			final String got = Files.readString(actual, StandardCharsets.UTF_8);
			if (!expected.equals(got)) {
				failures.add(doc + "/" + golden.getName() + ": 表示リストが基準と一致しません (expected=" + golden + ", actual="
						+ actual + ")");
			}
		}
	}

	private void compareDirs(final String doc, final File offDir, final File onDir, final List<String> failures)
			throws Exception {
		final File[] offPages = offDir.listFiles((d, n) -> n.endsWith(".txt"));
		final File[] onPages = onDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(doc + ": プローブ無効の表示リストがありません", offPages);
		assertNotNull(doc + ": プローブ有効の表示リストがありません", onPages);
		if (offPages.length != onPages.length) {
			failures.add(doc + ": ページ数がプローブ有効/無効で異なります (off=" + offPages.length + ", on=" + onPages.length + ")");
			return;
		}
		for (final File off : offPages) {
			final String expected = Files.readString(off.toPath(), StandardCharsets.UTF_8);
			final String got = Files.readString(new File(onDir, off.getName()).toPath(), StandardCharsets.UTF_8);
			if (!expected.equals(got)) {
				failures.add(doc + "/" + off.getName() + ": プローブ有効時の表示リストが無効時と一致しません");
			}
		}
	}

	private void transcode(final String doc, final String name, final File dumpDir, final boolean probe)
			throws Exception {
		final File pdf = new File("local/unittest/display-list-balance-probe/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, dumpDir.getPath());
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (probe) {
					session.property("processing.balance-probe", "true");
				}
				CTISessionHelper.transcodeFile(session, new File("files/unittest/" + doc), "text/html", null);
			} finally {
				session.close();
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
