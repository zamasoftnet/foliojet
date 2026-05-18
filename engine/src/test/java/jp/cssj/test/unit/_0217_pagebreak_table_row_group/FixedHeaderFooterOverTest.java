package jp.cssj.test.unit._0217_pagebreak_table_row_group;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedHeaderFooterOverTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0217-pagebreak-table-row-group/fixed-header-footer-over.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FixedHeaderFooterOverTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_ROW) {
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertTrue(pageNumber <= 2);
			return true;
		}
		return false;
	}
}
