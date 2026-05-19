package jp.cssj.test.unit._0030_text_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class HalfWidthTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0030-text-align/half-width.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public HalfWidthTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			assertEquals(25, x, 0);
			assertEquals(20, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			assertEquals(25, x, 0);
			assertEquals(20, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			assertEquals(25, x, 0);
			assertEquals(20, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
