package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BreakAvoidTest extends AbstractTestCase {
	public BreakAvoidTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0400-column-count/break-avoid.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(171, x, 0);
			assertEquals(10, y, 0);
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}
}
