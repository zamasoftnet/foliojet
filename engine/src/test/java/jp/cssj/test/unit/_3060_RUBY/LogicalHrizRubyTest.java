package jp.cssj.test.unit._3060_RUBY;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class LogicalHrizRubyTest extends AbstractTestCase {
	public LogicalHrizRubyTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3060-RUBY/logical-hriz-ruby.xhtml");
		CTISessionHelper.transcodeFile(this.session, file, "application/xhtml+xml", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(43, x, 1);
			assertEquals(9, y, 1);
			assertEquals(24, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(43, x, 1);
			assertEquals(9, y, 1);
			assertEquals(24, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(126, x, 1);
			assertEquals(67, y, 1);
			assertEquals(24, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(126, x, 1);
			assertEquals(67, y, 1);
			assertEquals(24, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(43, x, 1);
			assertEquals(87, y, 1);
			assertEquals(24, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(126, x, 1);
			assertEquals(145, y, 1);
			assertEquals(24, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
