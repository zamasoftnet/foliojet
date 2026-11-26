package jp.cssj.test.unit._0280_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class HeightAndPagebreakTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0280-height/height-and-pagebreak.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public HeightAndPagebreakTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(pageNumber + "/" + y);
			assertEquals(1, pageNumber);
			assertEquals(9, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(pageNumber + "/" + y);
			assertEquals(2, pageNumber);
			// After the forced break the h3's margin is kept, as on the first page and in Chrome (2026-11-26: it was
			// 0 while the break waited for the h3's line, the margin left on the previous page)
			assertEquals(9, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(pageNumber + "/" + y);
			assertEquals(3, pageNumber);
			assertEquals(9, y, 1);
			return true;
		}
		return false;
	}
}
