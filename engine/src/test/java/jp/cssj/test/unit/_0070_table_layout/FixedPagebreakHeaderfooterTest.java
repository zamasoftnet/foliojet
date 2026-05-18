package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedPagebreakHeaderfooterTest extends AbstractTestCase {
	public FixedPagebreakHeaderfooterTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/fixed-pagebreak-headerfooter.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(2, pageNumber);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		assertEquals(3, pageNumber);
		return true;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(x + "/" + y);
			assertEquals(3, x, 0);
			assertEquals(3, y, 0);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(x + "/" + y);
			assertEquals(3, x, 0);
			assertEquals(57, y, 0);
			return true;
		}
		return false;
	}
}
