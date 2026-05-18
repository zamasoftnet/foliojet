package jp.cssj.test.unit._0120_float;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatInOverflowHiddenTest extends AbstractTestCase {
	public FloatInOverflowHiddenTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0120-float/float-in-overflow-hidden.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(12, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
