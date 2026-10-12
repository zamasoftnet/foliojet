package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * The sweep documents that 19129's changes for splittable floats in columns and float continuations broke, checked as
 * the sweep checks them: the document passes or is excluded. The column splitting of floats (67b78dfa, eb8ad7ba, d106f1dc
 * and their follow-ups) and the max-height of continuations (132a5fcf) were taken out on 2026-10-11; the floor a float's
 * continuation keeps of its definite size (4c3a010e with ee892c2e) came back on 2026-10-12, since without it the
 * continuation of a float in an opposite-progression region ran off the paper.
 *
 * <ul>
 * <li>{@code float-continuation-floor} (FIT seed 11599998): a float of {@code width: 0pt} in vertical writing split
 * across pages. Its continuation took the rest of that size as a floor its content outgrew, and the next split carried
 * the content's size on as the definite size, page after page (732 pages, failed at the page limit).</li>
 * <li>{@code float-continuation-blank-page} (FIT seed 11846535): a float of {@code width: 96pt} in vertical writing
 * split 92+4pt at the page end. Its continuation kept the other 4pt, and the empty line after the float (the end of an
 * inline element) went down past it: the second page painted nothing. A text block whose lines all hold nothing now
 * also paints nothing (TextBlockBox, 7256e77c).</li>
 * <li>{@code multicol-in-column-float-rest} (STRICT seed 12585971): a multicol in a column of another, holding a float
 * taller than the page in vertical writing. Capped at the room left and grown to its last column's floats, it made the
 * two balancings feed each other until the progress guard aborted the conversion.</li>
 * <li>{@code float-continuation-opposite-region} (FIT seed 12344062): a floated column flex in vertical-rl inside a
 * vertical-lr page, split across pages. Without the floor its continuation put the rest of its items 70 to 190pt past
 * the 120pt paper on one page (19128 and 19134 did); with it, they go on page by page.</li>
 * </ul>
 */
public class Regression19129Test extends TestCase {
	public void testFloatContinuationFloorEnds() throws Exception {
		check("float-continuation-floor");
	}

	public void testEmptyFloatContinuationAddsNoPage() throws Exception {
		check("float-continuation-blank-page");
	}

	public void testMulticolInColumnEnds() throws Exception {
		check("multicol-in-column-float-rest");
	}

	public void testFloatContinuationInOppositeRegionStaysOnPaper() throws Exception {
		check("float-continuation-opposite-region");
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
