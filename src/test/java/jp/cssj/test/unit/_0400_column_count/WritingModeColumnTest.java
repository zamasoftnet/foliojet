package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class WritingModeColumnTest extends AbstractTestCase {
	public WritingModeColumnTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0400-column-count/writing-mode-column.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			System.out.println("height: " + box.getHeight());
			assertEquals(6, x, 0);
			assertEquals(0, y, 1);
			assertEquals(425, box.getWidth(), 1);
			assertEquals(425, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
