package jp.cssj.test.unit._0219_pagebreak_table_inrow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class CellVAlignTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0219-pagebreak-table-inrow/cell-valign.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public CellVAlignTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println(y);
			assertEquals(181, y, 1);
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println(y);
			assertEquals(0, y, 1);
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
