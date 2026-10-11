package jp.cssj.test.unit._0420_inline;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class SplitTest extends AbstractTestCase {
	public SplitTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0420-inline/split.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("width: " + box.getWidth());
			// Fixed the built-in CID-keyed font width table, restoring full widths for 　、。(2026-09-11). The space at
			// the end of the line before the inline ends goes though #a's border follows, as in Chrome (2026-10-11: 420)
			assertEquals(417, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("width: " + box.getWidth());
			// Fixed the built-in CID-keyed font width table, restoring full widths for 　、。(2026-09-11). Without the
			// space at the end of the line (2026-10-11: 414)
			assertEquals(411, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("width: " + box.getWidth());
			// Without the space at the end of the line (2026-10-11: 414)
			assertEquals(411, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
