package jp.cssj.test.unit._0340_page_break_inside;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowAvoidTest extends AbstractTestCase {
	public FlowAvoidTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0340-page-break-inside/flow-avoid.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(y);
			assertEquals(30, y, 1);
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
