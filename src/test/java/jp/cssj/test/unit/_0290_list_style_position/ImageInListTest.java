package jp.cssj.test.unit._0290_list_style_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ImageInListTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0290-list-style-position/image-in-list.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public ImageInListTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(20, x, 0);
			assertEquals(107, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(50, x, 0);
			assertEquals(92, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(40, x, 0);
			assertEquals(107, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(70, x, 0);
			assertEquals(92, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(40, x, 0);
			assertEquals(107, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(70, x, 0);
			assertEquals(92, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(80, x, 0);
			assertEquals(107, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_h(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(110, x, 0);
			assertEquals(92, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
