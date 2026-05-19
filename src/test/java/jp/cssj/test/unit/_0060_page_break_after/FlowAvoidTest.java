package jp.cssj.test.unit._0060_page_break_after;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowAvoidTest extends AbstractTestCase {
	public FlowAvoidTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0060-page-break-after/flow-avoid.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		System.out.println(pageNumber + "/" + y);
		assertEquals(2, pageNumber);
		if (box.getType() == IBox.TYPE_BLOCK) {
			assertEquals(0, y, 0);
			return true;
		}
		return false;
	}
}
