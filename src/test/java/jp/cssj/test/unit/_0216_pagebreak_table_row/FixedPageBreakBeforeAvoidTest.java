package jp.cssj.test.unit._0216_pagebreak_table_row;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedPageBreakBeforeAvoidTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0216-pagebreak-table-row/fixed-page-break-before_AVOID.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FixedPageBreakBeforeAvoidTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.out.println(y);
			assertEquals(30, y, 1);
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
