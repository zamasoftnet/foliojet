package jp.cssj.test.unit._0430_direction_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class PaddingTest extends AbstractTestCase {
	public PaddingTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0430-direction-mode/padding.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("width: " + box.getWidth());
			System.err.println("height: " + box.getHeight());
			assertEquals(145, box.getWidth(), 1);
			assertEquals(202, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(70, x, 1);
			assertEquals(4, y, 1);
			return true;
		}
		return false;
	}
}
