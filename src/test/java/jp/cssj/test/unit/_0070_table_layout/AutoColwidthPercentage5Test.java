package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoColwidthPercentage5Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/auto-colwidth-percentage-5.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoColwidthPercentage5Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(25, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(17, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(25, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(17, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(51, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(17, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(102, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_h(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(82, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_i(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(21, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_j(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(56, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_k(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(102, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
