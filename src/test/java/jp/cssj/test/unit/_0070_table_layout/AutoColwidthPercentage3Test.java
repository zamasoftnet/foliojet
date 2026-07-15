package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoColwidthPercentage3Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/auto-colwidth-percentage-3.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoColwidthPercentage3Test(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(165, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(165, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ad(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ae(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(165, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_af(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ag(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(165, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ah(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ai(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(165, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_aj(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ak(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(165, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_al(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(159, box.getWidth(), 8);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(170, box.getWidth(), 8);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_be(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(159, box.getWidth(), 8);
			return true;
		}
		return false;
	}

	public boolean check_bf(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(170, box.getWidth(), 8);
			return true;
		}
		return false;
	}

	public boolean check_bg(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bh(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(67, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(218, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(73, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ce(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cf(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(287, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cg(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(73, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_da(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(39, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_db(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(117, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(195, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(39, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_de(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(39, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_df(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(312, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dg(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(39, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ea(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(48, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_eb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(156, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ec(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(156, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_ed(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(30, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
