package jp.cssj.test.unit._0330_table_border;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BorderPaddingCopperTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0330-table-border/border-padding-copper.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public BorderPaddingCopperTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(x + "/" + y);
			assertEquals(22, x, 1);
			assertEquals(22, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(x + "/" + y);
			assertEquals(22, x, 1);
			assertEquals(97, y, 1);
			return true;
		}
		return false;
	}
}
