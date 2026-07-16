package jp.cssj.test.unit._3060_RUBY;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class VertRubyTest extends AbstractTestCase {
	public VertRubyTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3060-RUBY/vert-ruby.xhtml");
		CTISessionHelper.transcodeFile(this.session, file, "application/xhtml+xml", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(190, x, 1);
			assertEquals(44, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(195, x, 1);
			assertEquals(44, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(132, x, 1);
			assertEquals(128, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(137, x, 1);
			assertEquals(128, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(118, x, 1);
			assertEquals(44, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(59, x, 1);
			assertEquals(128, y, 1);
			assertEquals(24, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
