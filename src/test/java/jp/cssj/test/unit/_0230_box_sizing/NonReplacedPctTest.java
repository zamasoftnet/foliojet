package jp.cssj.test.unit._0230_box_sizing;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class NonReplacedPctTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0230-box-sizing/non-replaced-pct.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public NonReplacedPctTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(box.getWidth());
			System.out.println(box.getHeight());
			assertEquals(300, box.getWidth(), 1);
			assertEquals(328, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(box.getWidth());
			System.out.println(box.getHeight());
			assertEquals(250, box.getWidth(), 1);
			assertEquals(314, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(box.getWidth());
			System.out.println(box.getHeight());
			assertEquals(300, box.getWidth(), 1);
			assertEquals(280, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
