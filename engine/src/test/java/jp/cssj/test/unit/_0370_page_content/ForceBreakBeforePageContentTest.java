package jp.cssj.test.unit._0370_page_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ForceBreakBeforePageContentTest extends AbstractTestCase {
	public ForceBreakBeforePageContentTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0370-page-content/force-break-before-page-content.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(pageNumber);
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(pageNumber);
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
