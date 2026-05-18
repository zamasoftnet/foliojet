package jp.cssj.test.unit._1020_P;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class MarginCopperTest extends AbstractTestCase {
	public MarginCopperTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1020-P/margin-copper.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			assertEquals(1, x, 0.001);
			assertEquals(65.8, y, 0.001);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			assertEquals(1, x, 0.001);
			assertEquals(87, y, 0.001);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			assertEquals(1, x, 0.001);
			assertEquals(108.2, y, 0.001);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			assertEquals(43, x, 0.001);
			assertEquals(142.6, y, 0.001);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			assertEquals(43, x, 0.001);
			assertEquals(163.8, y, 0.001);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			assertEquals(43, x, 0.001);
			assertEquals(185, y, 0.001);
			return true;
		}
		return false;
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			assertEquals(0, x, 0.001);
			assertEquals(218.4, y, 0.001);
			return true;
		}
		return false;
	}
}
