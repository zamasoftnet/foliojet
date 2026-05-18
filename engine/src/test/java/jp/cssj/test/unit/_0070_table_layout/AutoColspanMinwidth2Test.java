package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoColspanMinwidth2Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/auto-colspan-minwidth-2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoColspanMinwidth2Test(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(64, box.getWidth(), 4);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(38, box.getWidth(), 4);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(64, box.getWidth(), 4);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(38, box.getWidth(), 4);
			return true;
		}
		return false;
	}

	public boolean check_ca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(64, box.getWidth(), 4);
			return true;
		}
		return false;
	}

	public boolean check_cb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(38, box.getWidth(), 4);
			return true;
		}
		return false;
	}

	public boolean check_da(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(64, box.getWidth(), 4);
			return true;
		}
		return false;
	}

	public boolean check_db(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(38, box.getWidth(), 4);
			return true;
		}
		return false;
	}
}
