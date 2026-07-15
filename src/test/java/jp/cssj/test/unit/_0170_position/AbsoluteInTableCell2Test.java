package jp.cssj.test.unit._0170_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteInTableCell2Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0170-position/absolute-in-table-cell2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AbsoluteInTableCell2Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(9, x, 0);
			assertEquals(9, y, 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(9, x, 0);
			assertEquals(9, y, 0);
			return true;
		}
		return false;
	}
}
