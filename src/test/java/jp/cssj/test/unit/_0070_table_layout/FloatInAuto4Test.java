package jp.cssj.test.unit._0070_table_layout;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatInAuto4Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0070-table-layout/float-in-auto-4.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FloatInAuto4Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(376.0, box.getWidth(), 0);
			assertEquals(124.5, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(414.5, box.getWidth(), 0);
			assertEquals(94.5, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(276.0, box.getWidth(), 0);
			assertEquals(154.5, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(216.0, box.getWidth(), 0);
			assertEquals(104.5, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
