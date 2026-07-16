package jp.cssj.test.unit._0410_column_width;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ColumnsInAbsoluteTest extends AbstractTestCase {
	public ColumnsInAbsoluteTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0410-column-width/columns-in-absolute.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println(box.getWidth());
			System.err.println(box.getHeight());
			assertEquals(7, x, 1);
			assertEquals(7, y, 1);
			assertEquals(32, box.getWidth(), 1);
			assertEquals(49, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
