package jp.cssj.test.unit._0030_text_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class LastTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0030-text-align/last.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public LastTest(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(73, x, 1);
			assertEquals(5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(71, x, 0);
			assertEquals(5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(71, x, 0);
			assertEquals(5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(71, x, 1);
			assertEquals(7, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(72.5, x, 0);
			assertEquals(5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(62.5, x, 0);
			assertEquals(5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(74, x, 0);
			assertEquals(5, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(61, x, 0);
			assertEquals(5, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
