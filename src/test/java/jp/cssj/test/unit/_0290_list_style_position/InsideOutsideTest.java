package jp.cssj.test.unit._0290_list_style_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class InsideOutsideTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0290-list-style-position/inside-outside.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public InsideOutsideTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(30, x, 0);
			assertEquals(7, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(50, x, 0);
			assertEquals(7, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + box.getWidth());
			assertEquals(30, x, 0);
			assertEquals(7, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
