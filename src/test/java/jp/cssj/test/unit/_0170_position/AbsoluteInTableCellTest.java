package jp.cssj.test.unit._0170_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteInTableCellTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0170-position/absolute-in-table-cell.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AbsoluteInTableCellTest(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(270, x, 1);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(0, x, 1);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(0, x, 1);
			assertEquals(280, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ad(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(270, x, 1);
			assertEquals(280, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(x + "/" + y);
			assertEquals(280, x, 1);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(x + "/" + y);
			assertEquals(0, x, 1);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(x + "/" + y);
			assertEquals(0, x, 1);
			assertEquals(280, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_bd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(x + "/" + y);
			assertEquals(280, x, 1);
			assertEquals(280, y, 1);
			return true;
		}
		return false;
	}
}
