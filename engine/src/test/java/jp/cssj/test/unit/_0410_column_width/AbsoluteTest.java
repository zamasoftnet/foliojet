package jp.cssj.test.unit._0410_column_width;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteTest extends AbstractTestCase {
	public AbsoluteTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0410-column-width/absolute.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println(box.getWidth());
			System.err.println(box.getHeight());
			assertEquals(7, x, 1);
			assertEquals(7, y, 1);
			assertEquals(73, box.getWidth(), 1);
			assertEquals(92, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println(box.getWidth());
			System.err.println(box.getHeight());
			assertEquals(15, x, 1);
			assertEquals(150, y, 1);
			assertEquals(229, box.getWidth(), 1);
			assertEquals(34, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
