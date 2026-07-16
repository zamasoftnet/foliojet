package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TextInFixedTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/text-in-fixed.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public TextInFixedTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.err.println(pageNumber);
			System.err.println(x);
			System.err.println(y);
			assertEquals(1, pageNumber);
			assertEquals(1, x, 1);
			assertEquals(181, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.err.println(pageNumber);
			System.err.println(x);
			System.err.println(y);
			assertEquals(2, pageNumber);
			assertEquals(1, x, 1);
			assertEquals(1, y, 1);
			return true;
		}
		return false;
	}
}
