package jp.cssj.test.unit._0050_white_space;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FirstWordTest extends AbstractTestCase {
	public FirstWordTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0050-white-space/first-word.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println(x);
			System.err.println(y);
			assertEquals(105, x, 0);
			assertEquals(9, y, 1);
			return true;
		}
		return false;
	}
}
