package jp.cssj.test.unit._0242_table_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class Rowspan3Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0242-table-height/rowspan-3.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public Rowspan3Test(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(25, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(30, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(30, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(25, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(5, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(40, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(40, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(25, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(30, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(30, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_da(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(25, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_db(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(5, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(40, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(40, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
