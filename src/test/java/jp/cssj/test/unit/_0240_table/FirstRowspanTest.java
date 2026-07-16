package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FirstRowspanTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0240-table/first-rowspan.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FirstRowspanTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(2, pageNumber);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		assertEquals(3, pageNumber);
		return true;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return true;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		assertEquals(2, pageNumber);
		return true;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		assertEquals(3, pageNumber);
		return true;
	}
}
