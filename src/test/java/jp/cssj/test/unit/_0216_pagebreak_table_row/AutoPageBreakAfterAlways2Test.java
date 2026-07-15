package jp.cssj.test.unit._0216_pagebreak_table_row;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoPageBreakAfterAlways2Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0216-pagebreak-table-row/auto-page-break-after_ALWAYS-2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoPageBreakAfterAlways2Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			assertEquals(3, pageNumber);
			return true;
		}
		return false;
	}
}
