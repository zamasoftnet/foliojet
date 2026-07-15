package jp.cssj.test.unit._0040_overflow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatInHidden2Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0040-overflow/float-in-hidden2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FloatInHidden2Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println(y);
			assertEquals(105.48, y, 0);
			return true;
		}
		return false;
	}
}
