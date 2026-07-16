package jp.cssj.test.unit._0500_ext_css;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class CSSJBreakCharactersTest extends AbstractTestCase {
	public CSSJBreakCharactersTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-ext-css/cssj-break-characters.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(6, x, 1);
			assertEquals(21, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(6, x, 1);
			assertEquals(50, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(6, x, 1);
			assertEquals(64, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			assertEquals(6, x, 1);
			assertEquals(109, y, 1);
			return true;
		}
		return false;
	}
}
