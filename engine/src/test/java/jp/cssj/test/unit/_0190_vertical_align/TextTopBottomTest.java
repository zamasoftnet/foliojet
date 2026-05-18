package jp.cssj.test.unit._0190_vertical_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TextTopBottomTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0190-vertical-align/text-top-bottom.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public TextTopBottomTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println("a " + y);
			assertEquals(711, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println("b " + y);
			assertEquals(684, y, 1);
			return true;
		}
		return false;
	}
}
