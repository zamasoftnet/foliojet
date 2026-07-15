package jp.cssj.test.unit._0230_box_sizing;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TableCellCollapseTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0230-box-sizing/table-cell-collapse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public TableCellCollapseTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			System.out.println(box.getHeight());
			assertEquals(200, box.getWidth(), 1);
			assertEquals(200, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getWidth());
			System.out.println(box.getHeight());
			assertEquals(200, box.getWidth(), 1);
			assertEquals(200, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
