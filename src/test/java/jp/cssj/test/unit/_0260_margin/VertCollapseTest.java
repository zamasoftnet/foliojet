package jp.cssj.test.unit._0260_margin;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class VertCollapseTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0260-margin/vert-collapse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public VertCollapseTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.err.println("x=" + x);
			assertEquals(331, x, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.err.println("x=" + x);
			assertEquals(221, x, 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x=" + x);
			assertEquals(128, x, 1);
			return true;
		}
		return false;
	}
}
