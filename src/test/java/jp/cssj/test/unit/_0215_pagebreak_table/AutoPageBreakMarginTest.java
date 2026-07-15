package jp.cssj.test.unit._0215_pagebreak_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoPageBreakMarginTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0215-pagebreak-table/auto-page-break-margin.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoPageBreakMarginTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(23.5, x, 0);
			assertEquals(0, y, 0);
			return true;
		}
		return false;
	}
}
