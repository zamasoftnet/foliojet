package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowInlinePagebreakTest extends AbstractTestCase {
	public FlowInlinePagebreakTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/flow-inline-pagebreak.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			if (pageNumber == 1) {
				assertEquals(0, x, 0);
				assertEquals(6, y, 0);
				assertEquals(61, box.getWidth(), 1);
			} else if (pageNumber == 2) {
				assertEquals(0, x, 0);
				assertEquals(6, y, 0);
				assertEquals(243, box.getWidth(), 1);
			} else if (pageNumber == 3) {
				assertEquals(177, x, 1);
				assertEquals(6, y, 1);
				assertEquals(66, box.getWidth(), 1);
			}
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println("pageNumber: " + pageNumber);
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			if (pageNumber == 2) {
				assertEquals(0, x, 1);
				assertEquals(16, y, 1);
				assertEquals(12, box.getWidth(), 0);
			} else if (pageNumber == 3) {
				assertEquals(229, x, 1);
				assertEquals(16, y, 0);
				assertEquals(12, box.getWidth(), 0);
			}
			return true;
		}
		return false;
	}
}
