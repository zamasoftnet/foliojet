package jp.cssj.test.unit._0120_float;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoWidthTest extends AbstractTestCase {
	public AutoWidthTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0120-float/auto-width.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(0, x, 0);
		assertEquals(0, y, 0);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(0, x, 0);
		assertEquals(10, y, 0);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		assertEquals(50, x, 0);
		assertEquals(20, y, 0);
		return true;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		assertEquals(50, x, 0);
		assertEquals(30, y, 0);
		return true;
	}
}
