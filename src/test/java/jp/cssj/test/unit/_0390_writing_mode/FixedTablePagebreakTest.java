package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedTablePagebreakTest extends AbstractTestCase {
	public FixedTablePagebreakTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/fixed-table-pagebreak.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_COLUMN) {
			System.out.println("y: " + y);
			System.out.println("height: " + box.getHeight());
			assertEquals(66.75, y, 0);
			assertEquals(94.25, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println("y: " + y);
			System.out.println("height: " + box.getHeight());
			assertEquals(66.75, y, 0);
			assertEquals(94.25, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(3, pageNumber);
			return true;
		}
		return false;
	}
}
