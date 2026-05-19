package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class MarginBreakTest extends AbstractTestCase {
	public MarginBreakTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0400-column-count/margin-break.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(38, x, 1);
			assertEquals(360, y, 1);
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(323, x, 1);
			assertEquals(359, y, 1);
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(90, x, 1);
			assertEquals(25, y, 1);
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
