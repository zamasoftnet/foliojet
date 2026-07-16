package jp.cssj.test.unit._0260_margin;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowCollapseTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0260-margin/flow-collapse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FlowCollapseTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(0, y, 0);
			assertEquals(6, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(6, y, 0);
			assertEquals(6, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(12, y, 0);
			assertEquals(6, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
