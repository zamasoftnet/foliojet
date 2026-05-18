package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class HrizCellInVertTest extends AbstractTestCase {
	public HrizCellInVertTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/hriz-cell-in-vert.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(157, x, 1);
			assertEquals(16.75, y, 0);
			assertEquals(159.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(157, x, 1);
			assertEquals(84.75, y, 0);
			assertEquals(159.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(27, x, 1);
			assertEquals(16.75, y, 0);
			assertEquals(129.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(27, x, 1);
			assertEquals(84.75, y, 0);
			assertEquals(129.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
