package jp.cssj.test.unit._0170_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class RelativeInAbsoluteTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0170-position/relative-in-absolute.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public RelativeInAbsoluteTest(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(50, x, 1);
			assertEquals(60, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ab(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(40, x, 1);
			assertEquals(70, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ac(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(15, x, 1);
			assertEquals(125, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ad(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(15, x, 1);
			assertEquals(135, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(160, x, 1);
			assertEquals(50, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(160, x, 1);
			assertEquals(70, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_bc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(135, x, 1);
			assertEquals(125, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_bd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(135, x, 1);
			assertEquals(145, y, 1);
			return true;
		}
		return false;
	}
}
