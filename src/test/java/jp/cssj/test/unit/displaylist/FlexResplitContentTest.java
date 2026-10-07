package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * Regression verifying that <b>splitting the head of an already split flex/grid again does not duplicate content</b>
 * (2026-09-17, reduced sweep seeds 2010872 and 2477193).
 *
 * <p>
 * {@code FlexBox.split}/{@code GridBox.split} did not truncate the head's line/row records
 * ({@code lines}/{@code lineItems}, {@code rows}/{@code rowItems}) after splitting,
 * leaving references to rows and items already transferred to the next fragment. When the same head
 * split again, as during column balancing, stale records selected the boundary row and split an
 * already transferred item again to produce a remainder, placing the same content in two fragments.
 * Seed 2010872 drew "T106" in two columns on the same page (duplicate content); seed 2477193
 * placed later words on an earlier page (reversed reading order).
 * </p>
 *
 * <p>
 * Both documents reproduce only under narrow conditions: removing any one component prevents reproduction
 * (checked with 15 variants). Still, duplication and broken reading order violate absolute requirements,
 * so preserve these regressions.
 * </p>
 *
 * <p>
 * On 2026-10-05, the first table in the reduced seed 2010872 changed from {@code width:76%} to
 * {@code width:45.6pt}. The percentage reference in vertical writing was corrected from paper width (60 pt)
 * to type-area width (40 pt). At 76%, the table narrowed, all later floats moved off the paper,
 * and the splitting path was no longer exercised. 45.6 pt is 76% of the old reference, preserving the
 * display list from before the change (the page-range path already used type-area width, so its digest changed).
 * </p>
 */
public class FlexResplitContentTest extends TestCase {
	public FlexResplitContentTest(final String name) {
		super(name);
	}

	public void testResplitDoesNotDuplicateContent() throws Exception {
		check("flex-resplit-duplicates-content");
	}

	public void testResplitKeepsReadingOrder() throws Exception {
		check("flex-resplit-reorders-content");
	}

	private static void check(final String name) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File work = new File("build/fuzz-regressions/" + name + ".html");
		final File displayList = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, work, displayList, true, name + "-regression");
	}
}
