package jp.cssj.test.unit._0215_pagebreak_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AutoPageBreakBeforeAlwaysTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0215-pagebreak-table/auto-page-break-before_ALWAYS.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AutoPageBreakBeforeAlwaysTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE) {
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
