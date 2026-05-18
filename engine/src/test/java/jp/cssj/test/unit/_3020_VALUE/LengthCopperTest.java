package jp.cssj.test.unit._3020_VALUE;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class LengthCopperTest extends AbstractTestCase {
	public LengthCopperTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3020-VALUE/length-copper.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(200.0 * 72.0 / 96.0 + 2.0, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(300, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
