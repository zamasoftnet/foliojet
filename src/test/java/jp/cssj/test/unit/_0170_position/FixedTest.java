package jp.cssj.test.unit._0170_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0170-position/fixed.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FixedTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK
				|| box.getType() == BoxType.REPLACED) {
			assertEquals(290, x + box.getWidth(), 0);
			assertEquals(10, y, 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK
				|| box.getType() == BoxType.REPLACED) {
			assertEquals(10, x, 0);
			assertEquals(10, y, 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK
				|| box.getType() == BoxType.REPLACED) {
			assertEquals(10, x, 0);
			assertEquals(290, y + box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK
				|| box.getType() == BoxType.REPLACED) {
			assertEquals(290, x + box.getWidth(), 0);
			assertEquals(290, y + box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
