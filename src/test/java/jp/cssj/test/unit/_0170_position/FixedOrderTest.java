package jp.cssj.test.unit._0170_position;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FixedOrderTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0170-position/fixed-order.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	private int i = 0;

	public FixedOrderTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, ++i);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (i == 1) {
			assertEquals(1, pageNumber);
			assertEquals(2, ++i);
		} else if (i == 4) {
			assertEquals(2, pageNumber);
			assertEquals(5, ++i);
		} else {
			assertEquals(3, pageNumber);
			assertEquals(7, ++i);
		}
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		assertEquals(3, ++i);
		return true;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(4, ++i);
		}
		return true;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(6, ++i);
		}
		return true;
	}
}
