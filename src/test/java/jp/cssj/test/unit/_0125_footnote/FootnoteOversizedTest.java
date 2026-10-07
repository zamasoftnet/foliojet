package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Boundary contract for footnote F4/F6: an oversized <b>atomic</b> footnote that fits on no page
 * (800pt+page-break-inside:avoid; fixed-height blocks can split by height, so avoid prohibits splitting).
 *
 * <p>
 * <b>The contract changed on 2026-08-02.</b> Previously, conversion failed with a typed error
 * ({@code FootnoteOverflowException}). However, {@code ARCHITECTURE.md} §5.13 (user decisions on
 * 2026-07-26/27) states that "<b>a conversion failure is always an engine defect</b>; exclusions for
 * documents with broken layout do not apply to conversion failures". The behavior therefore degrades to
 * <b>warning and placing the note with overflow, without failing</b>. In a sweep using the improved
 * generator, this failure type accounted for 531 of 2,000 seeds (77% of all failures).
 * </p>
 *
 * <p>
 * This test verifies only the requirement that <b>conversion succeeds</b>. It does not assess the appearance
 * of the placed result (which overflows the page). How to display a footnote larger than the type area
 * is the responsibility of the layout author (§5.13).
 * </p>
 */
public class FootnoteOversizedTest extends AbstractTestCase {
	public FootnoteOversizedTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-oversized.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	@Override
	public void testDocument() throws Exception {
		this.transcode();
	}
}
