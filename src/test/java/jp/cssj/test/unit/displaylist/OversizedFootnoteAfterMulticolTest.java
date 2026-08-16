package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/** 巨大脚注の予約で先行する段組断片を無限に送らない回帰(seed 7676)。 */
public class OversizedFootnoteAfterMulticolTest extends TestCase {
	public OversizedFootnoteAfterMulticolTest(final String name) {
		super(name);
	}

	public void testOversizedFootnoteIsCarriedAfterCallingPage() throws Exception {
		final File fixture = new File("files/fuzz-repro/oversized-footnote-after-multicol.html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File outDir = new File("build/fuzz-regressions/oversized-footnote-after-multicol-dl");
		RandomDocumentFuzzTest.checkDocument(generated,
				new File("build/fuzz-regressions/oversized-footnote-after-multicol.html"), outDir, false,
				"oversized-footnote-after-multicol-regression");
		final File[] pages = outDir.listFiles((dir, name) -> name.endsWith(".txt"));
		assertNotNull(pages);
		assertTrue("oversized footnote must not create a page chain: " + pages.length, pages.length <= 2);
	}
}
