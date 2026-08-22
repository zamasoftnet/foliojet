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

	// 2026-08-22: 縦組の約物詰め解禁(JLREQ)で」「対が-0.5em詰まり、
	// 行の詰め込みが変わった。span#aは行1末尾(y=270)と行2頭(y=0)の
	// 2断片、span#bは1断片(y=120)になる

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
			assertEquals(120, y, 0);
			return true;
		}
		return false;
	}
}
