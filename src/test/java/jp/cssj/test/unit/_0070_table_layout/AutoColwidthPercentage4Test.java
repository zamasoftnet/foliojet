package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoColwidthPercentage4Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/auto-colwidth-percentage-4.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoColwidthPercentage4Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(50, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(50, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(131, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(190, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(190, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(800, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(190, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_h(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(155, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
