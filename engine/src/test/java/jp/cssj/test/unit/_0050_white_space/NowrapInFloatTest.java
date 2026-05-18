package jp.cssj.test.unit._0050_white_space;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class NowrapInFloatTest extends AbstractTestCase {
	public NowrapInFloatTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0050-white-space/nowrap-in-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(0, x, 0);
			assertEquals(0, y, 0);
			assertEquals(200, box.getWidth(), 0);
			assertEquals(10, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			assertEquals(40, x, 0);
			assertEquals(0, y, 1);
			assertEquals(120, box.getWidth(), 0);
			assertEquals(10, box.getHeight(), 2);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(0, x, 0);
			assertEquals(10, y, 0);
			assertEquals(50, box.getWidth(), 0);
			assertEquals(30, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			assertEquals(0, x, 0);
			assertEquals(20, y, 1);
			assertEquals(120, box.getWidth(), 0);
			assertEquals(10, box.getHeight(), 2);
			return true;
		}
		return false;
	}
}
