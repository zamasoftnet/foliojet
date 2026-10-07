package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Tests all four {@code string()} modes ({@code first}/{@code last}/{@code first-except}/{@code start})
 * when {@code string-set} values contain only strings/counters (resolved immediately at build time),
 * including references from the same page as the assignment source.
 * This matches book.css's Roman/Arabic numeral switching (#s1/#s2 are assignment sources; checks cover
 * both references immediately afterward on the same page and references across a page without assignments).
 */
public class StringSetCounterTest extends AbstractTestCase {
	public StringSetCounterTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/string-set-counter.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	private void assertBlockText(IBox box, String expected) {
		StringBuilder text = new StringBuilder();
		box.getText(text);
		assertEquals(expected, text.toString());
	}

	// Page 1: #s1 assigns v="one". References are from the same page.
	public boolean check_p1first(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	public boolean check_p1last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	// first-except is an empty string when a new assignment occurs on this very page.
	public boolean check_p1except(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "");
			return true;
		}
		return false;
	}

	// Page 2: No assignment on this page (inherits the entry value from the previous page).
	public boolean check_p2first(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	public boolean check_p2last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	// With no assignment on this page, first-except is the same as first (not an empty string).
	public boolean check_p2except(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	public boolean check_p2start(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	// Page 3: #s2 assigns v="two". References are from the same page.
	public boolean check_p3first(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "two");
			return true;
		}
		return false;
	}

	public boolean check_p3last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "two");
			return true;
		}
		return false;
	}

	public boolean check_p3except(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "");
			return true;
		}
		return false;
	}

	// start is simplified to always use the entry value (the new assignment "two" on this page is not reflected).
	public boolean check_p3start(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}
}
