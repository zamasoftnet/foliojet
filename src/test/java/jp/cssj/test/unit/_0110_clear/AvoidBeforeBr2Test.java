package jp.cssj.test.unit._0110_clear;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AvoidBeforeBr2Test extends AbstractTestCase {
	public AvoidBeforeBr2Test(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0110-clear/avoid-before-br-2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(2, pageNumber);
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
		assertEquals(3, pageNumber);
		return true;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		assertEquals(4, pageNumber);
		return true;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		assertEquals(4, pageNumber);
		return true;
	}
}
