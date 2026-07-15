package jp.cssj.test.unit._0190_vertical_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedCellVerticalAlignTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0190-vertical-align/fixed-cell-vertical-align.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FixedCellVerticalAlignTest(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("aa " + y);
			assertEquals(46, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println("ab " + y);
			assertEquals(33, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ac " + y);
			assertEquals(60, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ad(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ad " + y);
			assertEquals(44, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ba " + y);
			assertEquals(109, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println("bb " + y);
			assertEquals(110, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bc " + y);
			assertEquals(137, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_bd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bd " + y);
			assertEquals(109, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ca " + y);
			assertEquals(236, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_cb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println("cb " + y);
			assertEquals(233, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("cc " + y);
			assertEquals(260, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_cd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("cd " + y);
			assertEquals(244, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_da(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("da " + y);
			assertEquals(364, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_db(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println("db " + y);
			assertEquals(357, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_dc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("dc " + y);
			assertEquals(384, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_dd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("dd " + y);
			assertEquals(380, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ea(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ea " + y);
			assertEquals(437, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_eb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println("eb " + y);
			assertEquals(410, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ec(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ec " + y);
			assertEquals(437, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ed(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ed " + y);
			assertEquals(433, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_fa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("fa " + y);
			assertEquals(537, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_fb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println("fb " + y);
			assertEquals(510, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_fc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("fc " + y);
			assertEquals(537, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_fd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("fd " + y);
			assertEquals(537, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ga(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ga " + y);
			assertEquals(637, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_gb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println("gb " + y);
			assertEquals(610, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_gc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("gc " + y);
			assertEquals(637, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_gd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("gd " + y);
			assertEquals(637, y, 1);
			return true;
		}
		return false;
	}
}
