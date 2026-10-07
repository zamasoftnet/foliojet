package jp.cssj.test.unit.displaylist;

import java.io.File;

import junit.framework.TestCase;

/** Regression: rebuilding a table continuation must not endlessly move the same tbody (seed 7662). */
public class TableContinuationLivelockTest extends TestCase {
	public TableContinuationLivelockTest(final String name) {
		super(name);
	}

	public void testWildSeed7662Terminates() throws Exception {
		final RandomDocumentFuzzTest.Generated generated = RandomDocumentFuzzTest.generate(7662, false);
		final File outDir = new File("build/fuzz-regressions/table-continuation-livelock-dl");
		RandomDocumentFuzzTest.checkDocument(generated,
				new File("build/fuzz-regressions/table-continuation-livelock.html"), outDir, false,
				"table-continuation-livelock-regression");
		final File[] pages = outDir.listFiles((dir, name) -> name.endsWith(".txt"));
		assertNotNull(pages);
		assertTrue("table continuation must not create a page chain: " + pages.length, pages.length < 100);
	}
}
