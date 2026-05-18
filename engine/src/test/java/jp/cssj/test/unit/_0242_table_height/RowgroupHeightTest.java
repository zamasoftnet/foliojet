package jp.cssj.test.unit._0242_table_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class RowgroupHeightTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0242-table-height/rowgroup-height.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public RowgroupHeightTest(String name) {
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
			assertEquals(39, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(29, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(9, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ad(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(39, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ae(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(23, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_af(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(17, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ag(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(5, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ah(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(23, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE) {
			System.out.println(box.getHeight());
			assertEquals(260, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(50, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(37, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(12, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(50, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_be(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(33, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bf(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(25, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bg(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(8, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bh(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(box.getHeight());
			assertEquals(33, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
