package jp.cssj.test.unit._0120_float;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatBreakAlwaysTest extends AbstractTestCase {
	public FloatBreakAlwaysTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0120-float/float-break-always.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_REPLACED) {
			System.out.println(pageNumber);
			assertEquals(7, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(pageNumber);
			assertEquals(13, pageNumber);
			return true;
		}
		return false;
	}
}
