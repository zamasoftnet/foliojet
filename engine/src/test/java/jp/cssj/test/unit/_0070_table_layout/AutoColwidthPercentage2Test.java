package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoColwidthPercentage2Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/auto-colwidth-percentage-2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoColwidthPercentage2Test(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ad(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(230, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ae(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(100, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(230, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_be(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(100, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(256, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ce(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(74, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_da(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_db(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(38, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(273, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_de(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(39, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ea(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_eb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(38, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ec(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ed(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(39, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ee(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(273, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_fa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_fb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_fc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_fd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(74, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_fe(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(256, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ga(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_gb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_gc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_gd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(100, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ge(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(230, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ha(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_hb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_hc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(20, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_hd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(132, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_he(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(197, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ia(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(39, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ib(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(78, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ic(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(273, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
