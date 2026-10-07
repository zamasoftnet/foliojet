package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests footnote F5 (renumbering on each page)
 * (consult-codex-2026-07-31-footnote-f5.txt F5-d fixture 1). Numbers restart at 1 on each
 * "page where the call remains", rather than running throughout the document:
 * page 1 has [1,2], and page 2 also has [1,2].
 */
public class FootnotePageResetTest extends AbstractTestCase {
	public FootnotePageResetTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-pagereset.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	private boolean checkNote(IBox box, int pageNumber, int expectedPage, String expectedPrefix) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertEquals(expectedPage, pageNumber);
		final StringBuilder buff = new StringBuilder();
		box.getText(buff);
		assertTrue("note text must start with \"" + expectedPrefix + "\": " + buff,
				buff.toString().startsWith(expectedPrefix));
		return true;
	}

	public boolean check_a1(IBox box, int pageNumber, double x, double y) {
		return this.checkNote(box, pageNumber, 1, "1. note a1");
	}

	public boolean check_a2(IBox box, int pageNumber, double x, double y) {
		return this.checkNote(box, pageNumber, 1, "2. note a2");
	}

	/** The first footnote on page 2 has page-local number 1, not document-wide number 3. */
	public boolean check_b1(IBox box, int pageNumber, double x, double y) {
		return this.checkNote(box, pageNumber, 2, "1. note b1");
	}

	public boolean check_b2(IBox box, int pageNumber, double x, double y) {
		return this.checkNote(box, pageNumber, 2, "2. note b2");
	}
}
