package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * Regression test for safely reopening a text run closed during a page break or column split
 * at the next glyph. Pin down the NPE found in the one-million-seed sweep.
 */
public class TextRunReopenAfterFragmentationTest extends TestCase {
	private static final String REPEATED_FLOAT_FRAGMENTATION = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="60pt"?>
			<?jp.cssj.property name="output.page-height" value="60pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{margin:0;font:normal 13pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<div style="column-count:3">
			<div style="page-break-inside:avoid">
			T4
			<div style="display:list-item;float:left;writing-mode:horizontal-tb;width:61pt"></div>
			</div>
			</div>
			</body></html>
			""";

	public TextRunReopenAfterFragmentationTest(final String name) {
		super(name);
	}

	public void testStrictSeedsThatPreviouslyLostTheOpenTextRun() throws Exception {
		checkSeed(594254);
		checkSeed(79618);
	}

	/**
	 * Splitting the same float repeatedly during column-balance replay reopens the continuation's
	 * text block at each split. Verify that it closes before the next split and that the sole
	 * body-text token is preserved (minimal case for seed 41546).
	 */
	public void testRepeatedFloatFragmentationClosesTextAtEveryBoundary() throws Exception {
		final RandomDocumentFuzzTest.Generated generated = new RandomDocumentFuzzTest.Generated(
				REPEATED_FLOAT_FRAGMENTATION, List.of("T4"), Set.of(), 60, 60, 61, true, true);
		final File base = new File("build/fuzz-regressions/repeated-float-fragmentation");
		RandomDocumentFuzzTest.checkDocument(generated, new File(base + ".html"), new File(base + "-dl"), true,
				"repeated-float-fragmentation");
	}

	/** Pin down the original generator input too, preserving its link to the minimal case across future vocabulary changes. */
	public void testStrictSeed41546() throws Exception {
		checkSeed(41546);
	}

	/**
	 * Pin down representative non-excluded seeds classified as the same invariant violation in the million-case sweep.
	 * Seed 185022's invariant violation is also fixed, but it already had a box larger than the paper;
	 * after the fix, it is classified as {@code ExcludedByOversizedBox}, as intended.
	 */
	public void testStrictTextBuilderBoundarySeedsFromMillionSweep() throws Exception {
		for (final int seed : new int[] { 45517, 55060, 96144, 106638, 140136, 175726 }) {
			checkSeed(seed);
		}
	}

	private static void checkSeed(final int seed) throws Exception {
		final RandomDocumentFuzzTest.Generated generated = RandomDocumentFuzzTest.generate(seed, true);
		final File base = new File("build/fuzz-regressions/text-run-reopen-" + seed);
		RandomDocumentFuzzTest.checkDocument(generated, new File(base + ".html"), new File(base + "-dl"), true,
				"text-run-reopen-" + seed);
	}
}
