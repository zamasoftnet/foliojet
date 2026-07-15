package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatInAutoTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/float-in-auto.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FloatInAutoTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(0, x, 1);
			assertEquals(163, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(0.5, x, 1);
			assertEquals(111, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(111.5, x, 1);
			assertEquals(51, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(1, x, 1);
			assertEquals(32, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(1, x, 1);
			assertEquals(110, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
