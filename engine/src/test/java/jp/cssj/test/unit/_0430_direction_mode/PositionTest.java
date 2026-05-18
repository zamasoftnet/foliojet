package jp.cssj.test.unit._0430_direction_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class PositionTest extends AbstractTestCase {
	public PositionTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0430-direction-mode/position.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_ca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(67, x, 1);
			assertEquals(59, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_cb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(43, x, 1);
			assertEquals(-9, y, 1);
			return true;
		}
		return false;
	}
}
