package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * 改ページ・段分割の途中で閉じた文字列runを、次のグリフで安全に再開できる
 * ことの回帰テストです。100万シード掃過で見つかったNPEを固定します。
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
	 * 段バランスの再生中に同じfloatを複数回切断すると、各切断で継続側の
	 * テキストブロックが再開される。次の切断へ進む前に閉じ、唯一の本文
	 * トークンも失わないことを固定する(seed 41546の最小形)。
	 */
	public void testRepeatedFloatFragmentationClosesTextAtEveryBoundary() throws Exception {
		final RandomDocumentFuzzTest.Generated generated = new RandomDocumentFuzzTest.Generated(
				REPEATED_FLOAT_FRAGMENTATION, List.of("T4"), Set.of(), 60, 60, 61, true, true);
		final File base = new File("build/fuzz-regressions/repeated-float-fragmentation");
		RandomDocumentFuzzTest.checkDocument(generated, new File(base + ".html"), new File(base + "-dl"), true,
				"repeated-float-fragmentation");
	}

	/** 元の生成器入力も固定し、将来の語彙変更で最小形との対応を失わない。 */
	public void testStrictSeed41546() throws Exception {
		checkSeed(41546);
	}

	/**
	 * 100万件掃過で同じ不変条件違反に分類された非除外の代表seedを固定する。
	 * seed 185022も不変条件違反は解消したが、元から紙面より大きい箱を持ち、
	 * 修正後は既定どおり {@code ExcludedByOversizedBox} に分類される。
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
