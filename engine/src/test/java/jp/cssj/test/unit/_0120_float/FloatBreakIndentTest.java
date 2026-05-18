package jp.cssj.test.unit._0120_float;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatBreakIndentTest extends AbstractTestCase {
	public FloatBreakIndentTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0120-float/float-break-indent.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(box.getWidth());
			assertEquals(88, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println(x);
			assertEquals(50, x, 0);
			return true;
		}
		return false;
	}
}
