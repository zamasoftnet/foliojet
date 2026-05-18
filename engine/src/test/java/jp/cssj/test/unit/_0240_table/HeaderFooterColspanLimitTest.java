package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class HeaderFooterColspanLimitTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0240-table/header-footer-colspan-limit.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public HeaderFooterColspanLimitTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertTrue(pageNumber < 20);
		return true;
	}
}
