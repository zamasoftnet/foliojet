package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * A regression of 19129 (4c3a010e) the FIT sweep found (2026-10-10, seed 11599998), checked as the sweep checks it:
 * the document passes or is excluded, as before 4c3a010e.
 *
 * <ul>
 * <li>{@code float-continuation-floor}: a float of {@code width: 0pt} in vertical writing (a definite page-axis size)
 * split across pages. Its continuation took the rest of that size as a floor its content outgrew (to 538pt), and was
 * marked as having a definite size, so the next split carried the rest of the content's size on as the definite size,
 * page after page: the original document ran to 732 pages and failed at the page limit. The split goes on with what is
 * left of the floor.</li>
 * <li>{@code float-continuation-blank-page} (2026-10-11, seed 11846535): a float of {@code width: 96pt} in vertical
 * writing, holding one column of text, split 92+4pt at the page end. Its continuation keeps the other 4pt, and the empty
 * line after the float (the end of an inline element) went down past it, 4pt into the body's text block. That block
 * answered that it paints, by the end of its last line, and the second page, which paints nothing, was kept: a blank
 * page. A line of no extent paints nothing wherever it sits.</li>
 * </ul>
 */
public class Regression19129Test extends TestCase {
	public void testFloatContinuationFloorEnds() throws Exception {
		check("float-continuation-floor");
	}

	public void testEmptyFloatContinuationAddsNoPage() throws Exception {
		check("float-continuation-blank-page");
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
