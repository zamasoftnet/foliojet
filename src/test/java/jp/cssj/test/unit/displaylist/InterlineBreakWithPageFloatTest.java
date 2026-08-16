package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/** 行間改ページをページフロートが拒んだ場合の回帰テスト(seed 552の縮小形)。 */
public class InterlineBreakWithPageFloatTest extends TestCase {
	public InterlineBreakWithPageFloatTest(final String name) {
		super(name);
	}

	public void testFailedPageBreakReopensTextBuilder() throws Exception {
		final File fixture = new File("files/fuzz-repro/interline-break-with-page-float.html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File work = new File("build/fuzz-regressions/interline-break.html");
		final File displayList = new File("build/fuzz-regressions/interline-break-dl");
		RandomDocumentFuzzTest.checkDocument(generated, work, displayList, false, "interline-break-regression");
	}
}
