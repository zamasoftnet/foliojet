package jp.cssj.test.unit._0415_column_fill;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class HBalanceTest extends AbstractTestCase {
	public HBalanceTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0415-column-fill/h-balance.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("width: " + box.getWidth());
			System.out.println("height: " + box.getHeight());
			assertEquals(280, box.getWidth(), 0);
			assertEquals(188, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			assertEquals(156, x, 1);
			assertEquals(155, y, 1);
			return true;
		}
		return false;
	}
}
