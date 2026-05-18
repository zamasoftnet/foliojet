package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TableLimitEmptyBodyTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0240-table/table-limit-empty-body.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public TableLimitEmptyBodyTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(pageNumber, 1);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(pageNumber, 2);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		assertEquals(pageNumber, 3);
		return true;
	}
}
