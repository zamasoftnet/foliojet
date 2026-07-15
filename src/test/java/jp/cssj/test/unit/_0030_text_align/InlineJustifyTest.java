package jp.cssj.test.unit._0030_text_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class InlineJustifyTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0030-text-align/inline-justify.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public InlineJustifyTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println(x);
			assertEquals(65.96, x, 0.01);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println(x);
			assertEquals(54.75, x, 0.01);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println(x);
			assertEquals(40.75, x, 0.01);
			return true;
		}
		return false;
	}
}
