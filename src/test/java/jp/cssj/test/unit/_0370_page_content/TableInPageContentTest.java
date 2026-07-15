package jp.cssj.test.unit._0370_page_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TableInPageContentTest extends AbstractTestCase {
	public TableInPageContentTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0370-page-content/table-in-page-content.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(x + "/" + y);
			assertEquals(0, x, .1);
			assertEquals(73.5, y, .1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(x + "/" + y);
			assertEquals(0.75, x, .1);
			assertEquals(74.25, y, .1);
			return true;
		}
		return false;
	}
}
