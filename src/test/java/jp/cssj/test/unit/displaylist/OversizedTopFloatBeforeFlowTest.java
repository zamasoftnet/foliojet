package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/** Continuation reentry regression immediately after a top float exceeding the type area (reduced seed 91). */
public class OversizedTopFloatBeforeFlowTest extends TestCase {
	public OversizedTopFloatBeforeFlowTest(final String name) {
		super(name);
	}

	public void testFlowKeepsMinimumPageAreaAfterOversizedTopFloat() throws Exception {
		final File fixture = new File("files/fuzz-repro/oversized-top-float-before-flow.html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		RandomDocumentFuzzTest.checkDocument(generated,
				new File("build/fuzz-regressions/oversized-top-float-before-flow.html"),
				new File("build/fuzz-regressions/oversized-top-float-before-flow-dl"), false,
				"oversized-top-float-before-flow-regression");
	}
}
