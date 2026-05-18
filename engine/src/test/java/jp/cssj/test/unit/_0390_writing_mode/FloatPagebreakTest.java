package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatPagebreakTest extends AbstractTestCase {
	public FloatPagebreakTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/float-pagebreak.html");
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
				assertEquals(75, box.getWidth(), 1);
			} else {
				assertEquals(51, x, 1);
				assertEquals(6, y, 0);
				assertEquals(100, box.getWidth(), 0);
			}
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(-22, x, 1);
			assertEquals(121, y, 1);
			assertEquals(194, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
