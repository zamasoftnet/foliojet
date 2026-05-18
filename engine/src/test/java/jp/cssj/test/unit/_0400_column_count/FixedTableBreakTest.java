package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedTableBreakTest extends AbstractTestCase {
	public FixedTableBreakTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0400-column-count/fixed-table-break.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(206, x, 1);
			assertEquals(0, y, 1);
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}
}
