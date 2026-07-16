package jp.cssj.test.unit._0410_column_width;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowTest extends AbstractTestCase {
	public FlowTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0410-column-width/flow.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(138, x, 0);
			assertEquals(165, y, 1);
			assertEquals(52, box.getWidth(), 0);
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
