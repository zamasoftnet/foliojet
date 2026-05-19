package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedColwidthCellwidthTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/fixed-colwidth-cellwidth.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FixedColwidthCellwidthTest(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(0, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(101.5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(51.5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ad(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(0, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_ae(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(113, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_af(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(63, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ag(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(69, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ah(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(49, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ai(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(69, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(0, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(101.5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(51.5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(0, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_be(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(113, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bf(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(63, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bg(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(69, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bh(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(49, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bi(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(69, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
