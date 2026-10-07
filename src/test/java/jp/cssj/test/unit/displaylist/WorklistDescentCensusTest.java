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
 * A census of reachable forms in the worklist executor (the sole OpenChain driver)
 * (2026-07-30, removal of legacy recursion = PLAN §2 "新旧2経路の一本化").
 *
 * <p>
 * <b>History</b>: the former {@code LegacyRecursiveDescentCensusTest} (increment 0) cataloged
 * which fixtures triggered the old recursive driver
 * (columns-float=2, page-first=2, nested multi-column layout=3). The gate switch in increment 2
 * changed all fixtures to legacy 0. Increment 4 physically removed the old driver and its
 * observation counter ({@code LEGACY_RECURSIVE_DESCENTS}), so this test was redefined to fix
 * zero/nonzero counts at the remaining observation points (compatibility fallback, native descent,
 * and rootless entry).
 * </p>
 */
public class WorklistDescentCensusTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * Representative fixtures containing MULTICOL tails plus a plain chain: fix the requirement
	 * that the worklist actually drives the chain (the path is exercised) and never enters
	 * the compatibility fallback.
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
	 * A representative document whose open chain crosses a MULTICOL boundary in nested multi-column layout
	 * (the same form as path 3 MOVE_SENTINEL in {@code NestedMulticolDuplicationTest}).
	 * Verify that native scope descent is actually exercised.
	 *
	 * <p>
	 * Note: {@code 0400-column-count/nest.html} completes within one page and never triggers a chain,
	 * so it provides no evidence of reachability (measured with a probe on 2026-07-30.
	 * The fixture assumed in the codex consultation was replaced based on actual measurements).
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
	 * Check that column-balancing fixtures complete (2026-07-30, increment 5). Previously,
	 * a counter here fixed the requirement that the rootless COLUMN path (the root==null branch in
	 * columnBreak) never fired. A user decision in increment 5 physically removed the branch;
	 * reaching it now stops immediately with {@code ContinuationInvariantViolationException}.
	 * If an unknown rootless context appears, these fixtures (or any conversion) fail with an exception,
	 * which serves as the detector.
	 */
	public void testColumnBalanceCompletesWithoutRootlessPath() throws Exception {
		ContinuationStats.reset();
		this.transcode(new File("files/unittest/0415-column-fill/v-balance.html"), "census-v-balance");
		this.transcode(new File("files/unittest/0415-column-fill/h-balance.html"), "census-h-balance");
		this.transcode(new File("files/unittest/0400-column-count/columns-float.html"), "census-rootless-cf");
		assertEquals("worklist executorが互換フォールバックへ落ちました", 0,
				ContinuationStats.WORKLIST_COMPAT_FALLBACKS.get());
	}

	/** Check that {@code reset()} resets the observation counters. */
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
