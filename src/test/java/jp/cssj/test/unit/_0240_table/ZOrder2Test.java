package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ZOrder2Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0240-table/z-order-2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public ZOrder2Test(String name) {
		super(name);
	}

	int index;

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			++index;
			System.out.println("a/" + index);
			assertEquals(2, index);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			++index;
			System.out.println("b/" + index);
			assertEquals(1, index);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			++index;
			System.out.println("c/" + index);
			assertEquals(3, index);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			++index;
			System.out.println("d/" + index);
			assertEquals(4, index);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			++index;
			System.out.println("e/" + index);
			assertEquals(5, index);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			++index;
			System.out.println("f/" + index);
			assertEquals(6, index);
			return true;
		}
		return false;
	}
}
