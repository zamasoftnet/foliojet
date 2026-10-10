package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * Two regressions of 19127 (7181a2e6) the strict sweep found (2026-10-10, seeds 11500000-11749999), checked as the
 * sweep checks them: the documents pass or are excluded as 19126 classified them.
 *
 * <ul>
 * <li>{@code flex-keep-after-probe-split} (seed 11500164): a continuation flex line at the start of the page whose item
 * paints past its extent (a float overflowing it) was kept whole after the probe had split the item, so the remainder
 * and its content were dropped (T65 lost). 19126: excluded as a broken layout placed off the page.</li>
 * <li>{@code nested-break-float-carry} (seed 11535244): a page break nested in the resume of the last one counted a
 * shrinking float carry as progress, so the break guard never stopped the recursion and the stack overflowed. 19126:
 * the guard stopped it (excluded as a broken layout with blank pages).</li>
 * </ul>
 */
public class Regression19127Test extends TestCase {
	public void testFlexKeepAfterProbeSplitKeepsContent() throws Exception {
		check("flex-keep-after-probe-split");
	}

	public void testNestedBreakFloatCarryStops() throws Exception {
		check("nested-break-float-carry");
	}

	private static void check(final String name) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File work = new File("build/fuzz-regressions/" + name + ".html");
		final File displayList = new File("build/fuzz-regressions/" + name + "-dl");
		try {
			RandomDocumentFuzzTest.checkDocument(generated, work, displayList, true, name + "-regression");
		} catch (final Throwable t) {
			final String kind = RandomDocumentFuzzTest.classify(t);
			assertTrue(name + ": " + kind + " : " + t, kind.startsWith("(除外)"));
		}
	}
}
