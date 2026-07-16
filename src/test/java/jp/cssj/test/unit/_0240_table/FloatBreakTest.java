package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatBreakTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0240-table/float-break.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FloatBreakTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			assertEquals(1, pageNumber);
			System.out.println(x + "/" + y + "/" + box.getHeight());
			assertEquals(0, x, 1);
			assertEquals(0, y, 1);
			assertEquals(64, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
