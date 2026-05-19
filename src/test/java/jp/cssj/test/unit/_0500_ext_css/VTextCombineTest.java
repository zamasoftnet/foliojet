package jp.cssj.test.unit._0500_ext_css;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class VTextCombineTest extends AbstractTestCase {
	public VTextCombineTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-ext-css/v-text-combine.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println("w: " + box.getWidth());
			System.err.println("h: " + box.getHeight());
			assertEquals(176, x, 0);
			assertEquals(30, y, 0);
			assertEquals(12, box.getWidth(), 1);
			assertEquals(12, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println("w: " + box.getWidth());
			System.err.println("h: " + box.getHeight());
			assertEquals(170, x, 1);
			assertEquals(90, y, 1);
			assertEquals(24, box.getWidth(), 1);
			assertEquals(12, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println("w: " + box.getWidth());
			System.err.println("h: " + box.getHeight());
			assertEquals(173, x, 1);
			assertEquals(114, y, 1);
			assertEquals(18, box.getWidth(), 1);
			assertEquals(12, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
