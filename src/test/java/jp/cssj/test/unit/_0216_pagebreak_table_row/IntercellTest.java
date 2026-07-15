package jp.cssj.test.unit._0216_pagebreak_table_row;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class IntercellTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0216-pagebreak-table-row/intercell.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public IntercellTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println(y);
			assertEquals(20, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println(y);
			assertEquals(20, y, 1);
			return true;
		}
		return false;
	}
}
