package jp.cssj.test.unit._0217_pagebreak_table_row_group;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoPageBreakBeforeAvoidTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0217-pagebreak-table-row-group/auto-page-break-before_AVOID.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoPageBreakBeforeAvoidTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			System.out.println(y);
			assertEquals(30, y, 1);
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
