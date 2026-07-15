package jp.cssj.test.unit._0415_column_fill;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoTest extends AbstractTestCase {
	public AutoTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0415-column-fill/auto.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("width: " + box.getWidth());
			System.out.println("height: " + box.getHeight());
			assertEquals(300, box.getWidth(), 0);
			assertEquals(300, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("width: " + box.getWidth());
			System.out.println("height: " + box.getHeight());
			assertEquals(290, box.getWidth(), 0);
			assertEquals(290, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("width: " + box.getWidth());
			System.out.println("height: " + box.getHeight());
			assertEquals(280, box.getWidth(), 0);
			assertEquals(280, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
