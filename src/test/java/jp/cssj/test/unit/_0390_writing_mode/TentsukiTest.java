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

	// JLREQの四分アキと優先度付き行調整後の配置。2026-09-11に追い込みの容量から
	// 連続約物の詰めで取り済みの二分を差し引くようにした(それまでは」「を1emと
	// 数えて」と「を15ptずつ重ねていた)。行1「ああ!?」「ああ」「ああ」は最短でも
	// 315pt>300ptで入らないため、span#aは行1末尾(y=270)と行2頭(y=0)の2断片、
	// span#bは3頁目の行1(y=30)の1断片になる。

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
