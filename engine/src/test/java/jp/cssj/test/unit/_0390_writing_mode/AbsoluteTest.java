package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteTest extends AbstractTestCase {
	public AbsoluteTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0390-writing-mode/absolute.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(515, x, 0);
			assertEquals(5, y, 0);
			assertEquals(90, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(485, x, 0);
			assertEquals(235, y, 0);
			assertEquals(120, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(25, x, 0);
			assertEquals(5, y, 0);
			assertEquals(490, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(35, x, 0);
			assertEquals(15, y, 0);
			assertEquals(50, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(35, x, 0);
			assertEquals(25, y, 1);
			assertEquals(470, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(35, x, 0);
			assertEquals(65, y, 1);
			assertEquals(30, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(0, x, 0);
			assertEquals(0, y, 0);
			assertEquals(610, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
