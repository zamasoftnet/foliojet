package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests footnote F3 (reducing body space). A footnote reservation (height + gap) reduces effective
 * page capacity ({@code RootBuilder.getPageLimit()}), splitting body text across two pages that would
 * fit on one without the footnote. 53 lines × 14.4 pt = 763.2 pt fits in the A4 type area (≈775.9 pt),
 * but not in the capacity remaining after reserving one footnote (≈21 pt).
 */
public class FootnotePageLimitTest extends AbstractTestCase {
	public FootnotePageLimitTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-pagelimit.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** The footnote body goes to the bottom of the page with its call (page 1). */
	public boolean check_fnp(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the note must sit on the calling page", 1, pageNumber);
			return true;
		}
		return false;
	}

	/** The reservation pushes the last line to page 2 (one page without the footnote). */
	public boolean check_last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the last line must be pushed to page 2 by the reservation", 2, pageNumber);
			return true;
		}
		return false;
	}
}
