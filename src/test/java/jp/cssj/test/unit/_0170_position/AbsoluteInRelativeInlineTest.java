package jp.cssj.test.unit._0170_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteInRelativeInlineTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0170-position/absolute-in-relative-inline.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AbsoluteInRelativeInlineTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(30, x, 0);
			assertEquals(5, y, 0);
			assertEquals(38, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(31, x, 0);
			assertEquals(5, y, 0);
			assertEquals(38, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
