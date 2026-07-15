package jp.cssj.test.unit._0260_margin;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowCollapse2Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0260-margin/flow-collapse2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FlowCollapse2Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(y);
			assertEquals(30, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(y);
			assertEquals(30, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(y);
			assertEquals(82, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(y);
			assertEquals(127, y, 1);
			return true;
		}
		return false;
	}
}
