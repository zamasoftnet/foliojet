package jp.cssj.test.unit._0040_overflow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowCopperTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0040-overflow/flow-copper.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FlowCopperTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(0, y, 0);
			assertEquals(20, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(20, y, 0);
			assertEquals(20, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(50, x, 0);
			assertEquals(20, y, 0);
			assertEquals(20, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(50, y, 0);
			assertEquals(20, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
