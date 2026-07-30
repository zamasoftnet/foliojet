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
 * legacy再帰driver撤去(PLAN §2「新旧2経路の一本化」)の増分0:
 * {@link ContinuationStats#LEGACY_RECURSIVE_DESCENTS}と
 * {@link ContinuationStats#WORKLIST_RECURSIVE_FALLBACKS}の到達形を
 * 現状のまま固定する観測テストです(2026-07-30、codex相談
 * docs/consultations/consult-codex-2026-07-30-multicol-descent-proof.txt
 * §5 増分0)。
 *
 * <p>
 * 増分0時点は「MULTICOL含みfixtureでlegacyが正」の到達形カタログだった
 * (columns-float=2・page-first=2・入れ子段組=3)。増分2(gateの
 * MULTICOL許可、2026-07-30)で全fixtureが「legacy==0」へ反転済み。
 * 撤去完了(増分4)後は0固定の回帰ガードとして残す。
 * </p>
 */
public class LegacyRecursiveDescentCensusTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * MULTICOL上のfloatを跨ぐ改ページ: 残存tailにMULTICOLが残る代表
	 * fixture。増分0時点(gateがMULTICOLをlegacyへ落としていた)は
	 * legacy=2——増分2(2026-07-30、gateのMULTICOL許可)でworklist駆動
	 * となり0へ反転した。
	 */
	public void testColumnsFloatUsesWorklist() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0400-column-count/columns-float.html"), "census-columns-float");
		assertTrue("チェーン発火が観測されていません(fixtureが弱体化)",
				ContinuationStats.RESTYLE_CHAIN_FIRINGS.get() > 0);
		assertEquals("columns-float.htmlでlegacy再帰が発火(増分2の切替が退行)", 0,
				ContinuationStats.LEGACY_RECURSIVE_DESCENTS.get());
	}

	/**
	 * 先頭ページからの段組: PAGE経由でMULTICOL tailが残る代表fixture。
	 * 増分0時点はlegacy=2——増分2で0へ反転した。
	 */
	public void testPageFirstUsesWorklist() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0400-column-count/page-first.html"), "census-page-first");
		assertTrue("チェーン発火が観測されていません(fixtureが弱体化)",
				ContinuationStats.RESTYLE_CHAIN_FIRINGS.get() > 0);
		assertEquals("page-first.htmlでlegacy再帰が発火(増分2の切替が退行)", 0,
				ContinuationStats.LEGACY_RECURSIVE_DESCENTS.get());
	}

	/**
	 * 入れ子段組で開いたチェーンがMULTICOL境界を貫通する代表文書
	 * ({@code NestedMulticolDuplicationTest}の経路3 MOVE_SENTINELと
	 * 同型。増分0時点の実測legacy=3、増分2以後はnative降下で処理)。
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
		assertTrue("入れ子段組でnative降下が観測されていません(増分2の切替が退行)",
				ContinuationStats.MULTICOL_NATIVE_DESCENTS.get() > 0);
		assertEquals("入れ子段組でlegacy再帰が発火(増分2の切替が退行)", 0,
				ContinuationStats.LEGACY_RECURSIVE_DESCENTS.get());
	}

	/**
	 * plainなチェーン破断(MULTICOL tailなし): worklist driverが処理し、
	 * legacy再帰は発火しない。v-frame.htmlは実測chain=11・legacy=0で、
	 * 「チェーンは大量に発火するがすべてworklistが処理する」ことの証拠。
	 */
	public void testPlainChainDoesNotFireLegacyDescent() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0460-segment-restyle/float-split-in-chain.html"), "census-plain-chain");
		this.transcode(new File("files/unittest/0400-column-count/v-frame.html"), "census-v-frame");
		assertTrue("チェーン発火が観測されていません(fixtureが弱体化)",
				ContinuationStats.RESTYLE_CHAIN_FIRINGS.get() > 0);
		assertEquals("plainチェーン破断でlegacy再帰が発火(worklist gateの退行)", 0,
				ContinuationStats.LEGACY_RECURSIVE_DESCENTS.get());
	}

	/**
	 * 現状のgate({@code WORKLIST_ELIGIBLE} = 残存tailすべて
	 * PLAIN_FLOW)の下では、worklist driverが再帰フォールバックへ
	 * 落ちることは決してない——この不変条件を全代表fixtureで固定する。
	 * MULTICOL native化(増分1)後も0のまま維持すべき値。
	 */
	public void testWorklistNeverFallsBackToRecursion() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0400-column-count/columns-float.html"), "census-fb1");
		this.transcode(new File("files/unittest/0400-column-count/v-frame.html"), "census-fb2");
		this.transcode(new File("files/unittest/0460-segment-restyle/float-split-in-chain.html"), "census-fb3");
		assertEquals("worklist driverが再帰フォールバックへ落ちました", 0,
				ContinuationStats.WORKLIST_RECURSIVE_FALLBACKS.get());
	}

	/**
	 * rootless COLUMN経路({@code BreakableBuilder.columnBreak()}の
	 * {@code root == null}分岐)が段組系代表fixtureで発火しないことの固定
	 * (2026-07-30、増分3)。この分岐の存在理由だった隔離バランス
	 * プローブは2026-07-25に全撤去済みで、全コーパス+合成文書の実測でも
	 * 発火0——死んだ入口と考えられる。<b>このassertが落ちたら「未知の
	 * rootless文脈が出現した」ことを意味する</b>ので、削除せず到達経路を
	 * 調査すること(分岐自体は統一worklist driverへ接続済みのため挙動は
	 * 安全だが、想定外の到達は設計前提の崩れを示す)。
	 */
	public void testRootlessColumnPathStaysUnreached() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0415-column-fill/v-balance.html"), "census-v-balance");
		this.transcode(new File("files/unittest/0415-column-fill/h-balance.html"), "census-h-balance");
		this.transcode(new File("files/unittest/0400-column-count/columns-float.html"), "census-rootless-cf");
		assertEquals("rootless COLUMN経路が発火——未知のrootless文脈が出現(到達経路を調査すること)", 0,
				ContinuationStats.ROOTLESS_COLUMN_RESTYLES.get());
	}

	/** {@code reset()}が新カウンタも戻すことの確認。 */
	public void testResetClearsNewCounters() {
		ContinuationStats.LEGACY_RECURSIVE_DESCENTS.set(7);
		ContinuationStats.WORKLIST_RECURSIVE_FALLBACKS.set(7);
		ContinuationStats.reset();
		assertEquals(0, ContinuationStats.LEGACY_RECURSIVE_DESCENTS.get());
		assertEquals(0, ContinuationStats.WORKLIST_RECURSIVE_FALLBACKS.get());
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
