package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class NameTest extends AbstractTestCase {
	public NameTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/name.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(x+"/"+y);
			assertEquals(36,x, 1);
			assertEquals(13,y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println(x+"/"+y);
			assertEquals(36,x, 1);
			assertEquals(28,y, 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println(x+"/"+y);
			assertEquals(36,x, 1);
			assertEquals(43,y, 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println(x+"/"+y);
			assertEquals(75,x, 1);
			assertEquals(43,y, 1);
			return true;
		}
		return false;
	}
}
