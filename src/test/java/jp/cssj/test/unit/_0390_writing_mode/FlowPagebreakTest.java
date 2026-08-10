package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowPagebreakTest extends AbstractTestCase {
	public FlowPagebreakTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/flow-pagebreak.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			// 直交ブロックのfit-content限度はページ内容域(2026-08-10)——
			// 旧値(x=-16, w=188)は物理ページ基準でマージンへ食い込んでいた
			assertEquals(0, x, 1);
			assertEquals(6, y, 0);
			assertEquals(171.65, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}
}
