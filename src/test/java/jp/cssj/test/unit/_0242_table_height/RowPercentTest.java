package jp.cssj.test.unit._0242_table_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class RowPercentTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0242-table-height/row-percent.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public RowPercentTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE) {
			System.out.println(box.getHeight());
			assertEquals(200, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(20, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(47, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(122, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE) {
			System.out.println(box.getHeight());
			assertEquals(200, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(20, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(47, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(122, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
