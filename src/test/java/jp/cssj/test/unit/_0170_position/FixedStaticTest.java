package jp.cssj.test.unit._0170_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedStaticTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0170-position/fixed-static.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FixedStaticTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(y);
			assertEquals(14, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(y);
			assertEquals(29, y, 1);
			return true;
		}
		return false;
	}
}
