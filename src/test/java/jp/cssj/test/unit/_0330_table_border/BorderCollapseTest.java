package jp.cssj.test.unit._0330_table_border;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BorderCollapseTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0330-table-border/border-collapse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public BorderCollapseTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(73.5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(73.5, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
