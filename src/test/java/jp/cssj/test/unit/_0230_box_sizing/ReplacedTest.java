package jp.cssj.test.unit._0230_box_sizing;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ReplacedTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0230-box-sizing/replaced.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public ReplacedTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(box.getWidth());
			System.out.println(box.getHeight());
			assertEquals(300, box.getWidth(), 1);
			assertEquals(300, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(box.getWidth());
			System.out.println(box.getHeight());
			assertEquals(300, box.getWidth(), 1);
			assertEquals(300, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(box.getWidth());
			System.out.println(box.getHeight());
			assertEquals(250, box.getWidth(), 1);
			assertEquals(300, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
