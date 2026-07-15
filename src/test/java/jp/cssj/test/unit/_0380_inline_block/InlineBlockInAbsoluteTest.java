package jp.cssj.test.unit._0380_inline_block;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class InlineBlockInAbsoluteTest extends AbstractTestCase {
	public InlineBlockInAbsoluteTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0380-inline-block/inline-block-in-absolute.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println(x);
			System.err.println(y);
			System.err.println(box.getWidth());
			assertEquals(133, x, 1);
			assertEquals(6, y, 1);
			assertEquals(60, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println(x);
			System.err.println(y);
			System.err.println(box.getWidth());
			assertEquals(201, x, 1);
			assertEquals(15, y, 1);
			assertEquals(60, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
