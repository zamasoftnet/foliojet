package jp.cssj.test.unit._0380_inline_block;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class InlineBlockInTableTest extends AbstractTestCase {
	public InlineBlockInTableTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0380-inline-block/inline-block-in-table.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(84, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(119, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			System.out.println(box.getWidth());
			assertEquals(119, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
