package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests footnote F4 (capacity-based deferral = carry-in)
 * (consult-codex-2026-07-31-footnote-f4.txt validation fixture 3).
 * Two 400 pt footnotes exceed the maximum footnote area (≈755.9 pt) in total, so note 2 moves to page 2
 * in FIFO order even though both calls are on page 1. It is placed with top priority even without a call
 * on page 2 (establishing that "attach from the front only as many notes as there are calls" is wrong).
 * Page 2 contains notes only (EOF deferral path).
 */
public class FootnoteCarryInTest extends AbstractTestCase {
	public FootnoteCarryInTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-carryin.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_n1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("note 1 must stay on the calling page", 1, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertTrue("marker must carry number 1: " + buff, buff.toString().startsWith("1. "));
			return true;
		}
		return false;
	}

	/** Cumulative overflow causes FIFO deferral, not an exception (carry-in placement on a page without a call). */
	public boolean check_n2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("note 2 must be carried to the next page", 2, pageNumber);
			// F5: The carry-in number is not reassigned on the note's placement page; it keeps number 2
			// from the call page (page 1) (the most important fixture: F5 recommendation F5-e).
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertTrue("carried marker must keep the call-page number 2: " + buff,
					buff.toString().startsWith("2. "));
			return true;
		}
		return false;
	}
}
