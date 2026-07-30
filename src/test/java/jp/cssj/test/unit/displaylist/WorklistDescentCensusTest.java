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
 * worklist executor(唯一のOpenChain driver)の到達形censusです
 * (2026-07-30、legacy再帰撤去=PLAN §2「新旧2経路の一本化」)。
 *
 * <p>
 * <b>歴史</b>: 旧{@code LegacyRecursiveDescentCensusTest}(増分0)は
 * 「どのfixtureが旧再帰driverを発火させるか」のカタログだった
 * (columns-float=2・page-first=2・入れ子段組=3)。増分2のgate切替で
 * 全fixtureがlegacy 0へ反転し、増分4で旧driverとその観測カウンタ
 * ({@code LEGACY_RECURSIVE_DESCENTS})自体が物理撤去されたため、
 * 残る観測点(互換フォールバック・native降下・rootless入口)の
 * 0/非0固定へ再定義した。
 * </p>
 */
public class WorklistDescentCensusTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * MULTICOL tailを含む代表fixture+plainチェーン: worklistがチェーンを
	 * 実際に駆動し(非空振り)、互換フォールバックへ一切落ちないことを
	 * 固定する。
	 */
	public void testChainsRunOnWorklistWithoutFallback() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0400-column-count/columns-float.html"), "census-columns-float");
		this.transcode(new File("files/unittest/0400-column-count/page-first.html"), "census-page-first");
		this.transcode(new File("files/unittest/0400-column-count/v-frame.html"), "census-v-frame");
		this.transcode(new File("files/unittest/0460-segment-restyle/float-split-in-chain.html"), "census-plain-chain");
		assertTrue("チェーン発火が観測されていません(fixtureが弱体化)",
				ContinuationStats.RESTYLE_CHAIN_FIRINGS.get() > 0);
		assertEquals("worklist executorが互換フォールバックへ落ちました(未知のbox/container組み合わせの出現)", 0,
				ContinuationStats.WORKLIST_COMPAT_FALLBACKS.get());
	}

	/**
	 * 入れ子段組で開いたチェーンがMULTICOL境界を貫通する代表文書
	 * ({@code NestedMulticolDuplicationTest}の経路3 MOVE_SENTINELと
	 * 同型)。native scope降下の非空振りを固定する。
	 *
	 * <p>
	 * 注意: {@code 0400-column-count/nest.html}は1ページ内で完結し
	 * チェーン発火自体が0のため到達形の証拠にならない(2026-07-30
	 * プローブ実測。codex相談の想定fixtureを実測で差し替えた)。
	 * </p>
	 */
	public void testNestedMulticolUsesNativeDescent() throws Exception {
		final File input = new File("local/unittest/continuation/census-nested-multicol.html");
		input.getParentFile().mkdirs();
		try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(input),
				java.nio.charset.StandardCharsets.UTF_8)) {
			w.write("""
					<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
					<?jp.cssj.property name="output.page-width" value="595pt"?>
					<?jp.cssj.property name="output.page-height" value="842pt"?>
					<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
					<style>
					@page{margin:10pt}
					body{font:normal 9pt/1.2 serif}
					</style></head><body>
					<div style="column-count:3">
					T2
					<div style="column-count:2">
					T4
					<p></p>
					T6
					</div>
					</div>
					</body></html>
					""");
		}
		ContinuationStats.reset();
		this.transcode(input, "census-nested-multicol");
		assertTrue("入れ子段組でnative降下が観測されていません",
				ContinuationStats.MULTICOL_NATIVE_DESCENTS.get() > 0);
		assertEquals("worklist executorが互換フォールバックへ落ちました", 0,
				ContinuationStats.WORKLIST_COMPAT_FALLBACKS.get());
	}

	/**
	 * 段バランス系fixtureの完走確認(2026-07-30、増分5)。かつてここは
	 * rootless COLUMN経路(columnBreakのroot==null分岐)の不発火を
	 * カウンタで固定していたが、増分5のユーザー裁定で分岐ごと物理削除
	 * し、到達時は{@code ContinuationInvariantViolationException}で
	 * 即座に停止する形になった——未知のrootless文脈が出現すれば
	 * これらのfixture(または任意の変換)が例外で落ちることが検出器。
	 */
	public void testColumnBalanceCompletesWithoutRootlessPath() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0415-column-fill/v-balance.html"), "census-v-balance");
		this.transcode(new File("files/unittest/0415-column-fill/h-balance.html"), "census-h-balance");
		this.transcode(new File("files/unittest/0400-column-count/columns-float.html"), "census-rootless-cf");
		assertEquals("worklist executorが互換フォールバックへ落ちました", 0,
				ContinuationStats.WORKLIST_COMPAT_FALLBACKS.get());
	}

	/** {@code reset()}が観測カウンタを戻すことの確認。 */
	public void testResetClearsCounters() {
		ContinuationStats.WORKLIST_COMPAT_FALLBACKS.set(7);
		ContinuationStats.MULTICOL_NATIVE_DESCENTS.set(7);
		ContinuationStats.reset();
		assertEquals(0, ContinuationStats.WORKLIST_COMPAT_FALLBACKS.get());
		assertEquals(0, ContinuationStats.MULTICOL_NATIVE_DESCENTS.get());
	}

	private void transcode(File source, String name) throws Exception {
		File pdf = new File("local/unittest/continuation/" + name + ".pdf");
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
