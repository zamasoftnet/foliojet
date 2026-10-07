package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * Regressions for float <b>termination and exclusion-area contracts</b>
 * (2026-09-17, reduced wild sweep cases).
 *
 * <ul>
 * <li>{@code float-split-without-progress} (seed 2264275): splitting a page-start float could not
 * split its contents on the page axis (orthogonal-flow cell + explicit size). The remainder was
 * rebuilt to the same 63.25 pt on every page, <b>endlessly emitting blank pages</b> on 60 pt paper.
 * The release CLI emitted 27,820 pages before OutOfMemoryError (production has no limit).
 * If dimensions have not shrunk since the previous page-start split, treat it as unsplittable
 * and fall back to rescue splitting or placement with overflow.</li>
 * <li>{@code top-float-exclusion-after-limit-shrinks} (seed 2266831): after a footnote reservation
 * reduced fragmentLimit within the same page generation, stacking a zero-height top float made
 * the exclusion area's end precede its start, violating {@code ExclusionSpace.copyOfSorted}'s
 * ordering contract. Production disables assertions, so it used the malformed exclusion ordering.</li>
 * </ul>
 */
public class PageFloatTerminationTest extends TestCase {
	public PageFloatTerminationTest(final String name) {
		super(name);
	}

	public void testFloatSplitWithoutProgressTerminates() throws Exception {
		check("float-split-without-progress");
	}

	public void testTopFloatExclusionStaysSorted() throws Exception {
		check("top-float-exclusion-after-limit-shrinks");
	}

	/**
	 * Seed 3361501: when page-float exclusion prevents the first line from fitting the type area and
	 * {@code requireTextBlock()} breaks the page, resumption returns with the immediately preceding
	 * completed text block open, as required by the depth convention. Missing cleanup caused conversion
	 * to fail with "text builder remains open at a block boundary".
	 */
	public void testFirstLineBreakClosesReopenedText() throws Exception {
		check("first-line-break-reopens-closed-text");
		// WILD checks only no crashes/termination, so verify through the display list that this fix
		// does not duplicate words (exclude continued rescue slices, which are drawn as artifacts).
		final java.util.Map<String, Integer> draws = new java.util.TreeMap<>();
		final File[] pages = new File("build/fuzz-regressions/first-line-break-reopens-closed-text-dl")
				.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		assertTrue("表示リストが出ていない", pages.length > 0);
		final java.util.regex.Pattern token = java.util.regex.Pattern.compile("Text\\[\"(T\\d+)\"");
		for (final File page : pages) {
			for (final String line : Files.readAllLines(page.toPath(), StandardCharsets.UTF_8)) {
				if (line.contains(" artifact ")) {
					continue;
				}
				final java.util.regex.Matcher m = token.matcher(line);
				while (m.find()) {
					draws.merge(m.group(1), 1, Integer::sum);
				}
			}
		}
		assertFalse("語が 1 つも描かれていない", draws.isEmpty());
		draws.forEach((k, v) -> assertEquals("語 " + k + " が複数回描かれた", 1, v.intValue()));
	}

	/**
	 * Seed 8471349 (2026-09-19, strict in the restarted sweep): on 60 pt paper (`vertical-lr`),
	 * an unsplittable page-start float (grid, min-width 104 pt) was placed with overflow, leaving
	 * the page-end float only 1 pt per page. A 207 pt float split into 207 slices of 1 pt
	 * (finite, but 301 pages). The cycle came from avoid backtracking: when the ul after the float
	 * (UA default {@code page-break-before:avoid}) moved to the next page, it pulled back the preceding
	 * empty bordered div too. The cut line fell inside that div's 1 pt border and split the float there.
	 * If backtracking leaves no body content on the page (the chain starts at page start and only
	 * the frame precedes the cut), no earlier break point exists; ignore avoid and move only the ul
	 * ({@code FlowContainer.hasInFlowContentBefore}). This yields eight pages and preserves all words.
	 */
	public void testFloatSplitIntoSliversStops() throws Exception {
		check("float-sliver-progress");
		final File[] pages = new File("build/fuzz-regressions/float-sliver-progress-dl")
				.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		assertTrue("ページ数 " + pages.length + " (薄片の分割が止まっていない)", pages.length <= 20);
		// WILD does not check words, so verify that all six are drawn once (excluding rescue-slice artifacts).
		final java.util.Map<String, Integer> draws = new java.util.TreeMap<>();
		final java.util.regex.Pattern token = java.util.regex.Pattern.compile("Text\\[\"(T\\d+)\"");
		for (final File page : pages) {
			for (final String line : Files.readAllLines(page.toPath(), StandardCharsets.UTF_8)) {
				if (line.contains(" artifact ")) {
					continue;
				}
				final java.util.regex.Matcher m = token.matcher(line);
				while (m.find()) {
					draws.merge(m.group(1), 1, Integer::sum);
				}
			}
		}
		assertEquals(draws.toString(), java.util.Set.of("T7", "T9", "T13", "T17", "T20", "T25"), draws.keySet());
		draws.forEach((k, v) -> assertEquals("語 " + k + " が複数回描かれた", 1, v.intValue()));
	}

	private static void check(final String name) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File work = new File("build/fuzz-regressions/" + name + ".html");
		final File displayList = new File("build/fuzz-regressions/" + name + "-dl");
		// Check WILD invariants: no crashes, termination, and no excessive page count.
		RandomDocumentFuzzTest.checkDocument(generated, work, displayList, false, name + "-regression");
	}
}
