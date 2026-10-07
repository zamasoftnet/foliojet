package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TentsukiTest extends AbstractTestCase {
	public TentsukiTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0390-writing-mode/tentsuki.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	// Placement after JLREQ quarter-em spacing and prioritized line adjustment. On 2026-09-11, compression capacity
	// was reduced by the half-em already removed by consecutive punctuation compression (previously, 」「 counted as 1em,
	// overlapping 」 and 「 by 15 pt each). Line 1 「ああ!?」「ああ」「ああ」 needs at least
	// 315 pt > 300 pt and cannot fit, so span#a has two fragments: end of line 1 (y=270) and start of line 2 (y=0).
	// span#b has one fragment in line 1 of page 3 (y=30).

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("y: " + y);
			assertTrue("y=" + y, y == 270 || y == 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("y: " + y);
			assertEquals(30, y, 0);
			return true;
		}
		return false;
	}
}
