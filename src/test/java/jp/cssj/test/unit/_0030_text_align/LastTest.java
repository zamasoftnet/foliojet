package jp.cssj.test.unit._0030_text_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class LastTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0030-text-align/last.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public LastTest(String name) {
		super(name);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// Fixed the built-in CID-keyed font width table, restoring full widths for 　、。(2026-09-11).
			assertEquals(71, x, 1);
			// Fixed the built-in CID-keyed font width table, restoring full widths for 　、。(2026-09-11).
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(71, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(71, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// 2026-08-22: Excluded kinsoku (line-breaking rules) boundaries (atomic) from justification expansion points
			// (JLREQ 3.1.11). "ます|。" no longer expands, moving it right; the span's width
			// also returned to its unexpanded value (5 pt after line-end trimming).
			// Fixed the built-in CID-keyed font width table, restoring full widths for 　、。(2026-09-11).
			// The final 。 is fullwidth (10 pt), so x = 1+78-10 = 69.
			assertEquals(69, x, 1);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// Fixed the built-in CID-keyed font width table, restoring full widths for 　、。(2026-09-11).
			assertEquals(71, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// Fixed the built-in CID-keyed font width table, restoring full widths for 　、。(2026-09-11).
			// The last line has 6 characters = 60 pt. (78-60)/2=9, so x = 1+9+50 = 60.
			assertEquals(60, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_dd(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			// Fixed the built-in CID-keyed font width table, restoring full widths for 　、。(2026-09-11).
			// The final 、 is compressed to 2 pt, filling the line exactly. x = 1+70 = 71.
			assertEquals(71, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(x+"/"+box.getWidth());
			assertEquals(61, x, 0);
			assertEquals(10, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
