package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedColspanOverTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/fixed-colspan-over.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FixedColspanOverTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_CELL) {
			System.out.println(x + "/" + y + "/" + box.getWidth());
			assertEquals(153, x, 1);
			assertEquals(15, y, 1);
			assertEquals(95, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
