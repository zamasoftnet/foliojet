package jp.cssj.test.unit._0190_vertical_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ImageMiddleTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0190-vertical-align/image-middle.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public ImageMiddleTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println(y);
			assertEquals(35, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_REPLACED) {
			System.out.println(y);
			assertEquals(14, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_REPLACED) {
			System.out.println(y);
			assertEquals(71, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println(y);
			assertEquals(92, y, 1);
			return true;
		}
		return false;
	}
}
