package jp.cssj.test.unit._0040_overflow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatInHidden3Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0040-overflow/float-in-hidden3.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FloatInHidden3Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(y);
			assertEquals(48.75, y, 0);
			return true;
		}
		return false;
	}
}
