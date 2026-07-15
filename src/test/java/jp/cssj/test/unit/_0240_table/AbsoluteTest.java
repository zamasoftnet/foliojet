package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0240-table/absolute.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AbsoluteTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			assertEquals(0, x, 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			assertEquals(300, x + box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
