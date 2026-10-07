package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/** Double-bind regression for nested Flex in a footnote inside a page float (seed 932). */
public class NestedFlexInPageFloatFootnoteTest extends TestCase {
	public NestedFlexInPageFloatFootnoteTest(final String name) {
		super(name);
	}

	public void testNestedFlexIsBoundOnlyOnce() throws Exception {
		final File fixture = new File("files/fuzz-repro/nested-flex-in-page-float-footnote.html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		RandomDocumentFuzzTest.checkDocument(generated,
				new File("build/fuzz-regressions/nested-flex-page-float-footnote.html"),
				new File("build/fuzz-regressions/nested-flex-page-float-footnote-dl"), false,
				"nested-flex-page-float-footnote-regression");
	}
}
