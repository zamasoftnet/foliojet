package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedColTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0070-table-layout/fixed-col.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FixedColTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(0, x, 0);
			assertEquals(280, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(0, x, 0);
			assertEquals(93, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(93, x, 1);
			assertEquals(93, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(186, x, 1);
			assertEquals(93, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(0, x, 0);
			assertEquals(280, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(0, x, 0);
			assertEquals(51, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(51, x, 1);
			assertEquals(131, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(180, x, 1);
			assertEquals(100, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(0, x, 0);
			assertEquals(280, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(0, x, 0);
			assertEquals(51, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
