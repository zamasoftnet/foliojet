package jp.cssj.test.unit._0416_column_span;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowTest extends AbstractTestCase {
	public FlowTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0416-column-span/flow.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			assertEquals(156, x, 0);
			assertEquals(49, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			assertEquals(156, x, 0);
			assertEquals(192, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			assertEquals(156, x, 0);
			assertEquals(348, y, 1);
			return true;
		}
		return false;
	}
}
