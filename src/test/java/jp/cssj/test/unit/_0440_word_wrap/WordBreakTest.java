package jp.cssj.test.unit._0440_word_wrap;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class WordBreakTest extends AbstractTestCase {
	public WordBreakTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0440-word-wrap/word-break.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(830, x, 1);
			assertEquals(19, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(406, x, 1);
			assertEquals(77, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(86, x, 1);
			assertEquals(211, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(67, x, 1);
			assertEquals(384, y, 1);
			return true;
		}
		return false;
	}
}

