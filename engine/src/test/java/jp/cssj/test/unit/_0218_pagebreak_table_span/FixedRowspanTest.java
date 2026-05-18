package jp.cssj.test.unit._0218_pagebreak_table_span;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedRowspanTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0218-pagebreak-table-span/fixed-rowspan.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FixedRowspanTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.err.println(box.getHeight());
			assertEquals(162, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
