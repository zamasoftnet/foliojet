package jp.cssj.test.unit._0050_white_space;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class WrapFullWidthSpacesCopperTest extends AbstractTestCase {
	public WrapFullWidthSpacesCopperTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0050-white-space/wrap-full-width-spaces-copper.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(50, box.getWidth(), 0);
			assertEquals(30, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(50, box.getWidth(), 0);
			assertEquals(30, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			// Fixed the built-in CID-keyed font width table, restoring the full width (10 pt) of 　;
			// the following fragment changed from 60 to 110 (ten 　 characters add 50) (2026-09-11).
			assertTrue(20 == box.getWidth() || 110 == box.getWidth());
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			assertEquals(111.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			assertEquals(111.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
