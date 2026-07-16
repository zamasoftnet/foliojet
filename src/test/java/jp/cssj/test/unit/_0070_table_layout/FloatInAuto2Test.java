package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatInAuto2Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/float-in-auto-2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FloatInAuto2Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(193, box.getWidth(), 1);
			assertEquals(108, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(223, box.getWidth(), 1);
			assertEquals(108, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(220, box.getWidth(), 1);
			assertEquals(105, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
