package jp.cssj.test.unit._0217_pagebreak_table_row_group;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoHeaderFooterLineTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0217-pagebreak-table-row-group/auto-header-footer-line.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoHeaderFooterLineTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(box.getHeight());
			assertEquals(23, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
