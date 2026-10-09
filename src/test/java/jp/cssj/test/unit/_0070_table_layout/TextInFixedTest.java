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

	/*
	 * The text directly in the table goes before it, on one line (foster parenting in html-balancer, 2026-10-09; Chrome:
	 * rows a and b at 130.5pt and 147pt). Until then each piece of text took a line of its own above the table, and row
	 * a was at 181pt, row b at the top of page 2.
	 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.err.println(pageNumber);
			System.err.println(x);
			System.err.println(y);
			assertEquals(1, pageNumber);
			assertEquals(1, x, 1);
			assertEquals(137, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_ROW) {
			System.err.println(pageNumber);
			System.err.println(x);
			System.err.println(y);
			assertEquals(1, pageNumber);
			assertEquals(1, x, 1);
			assertEquals(154.5, y, 1);
			return true;
		}
		return false;
	}
}
