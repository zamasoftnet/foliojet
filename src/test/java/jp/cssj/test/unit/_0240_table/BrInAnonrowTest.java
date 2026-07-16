package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BrInAnonrowTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0240-table/br-in-anonrow.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public BrInAnonrowTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("x/" + x);
			System.err.println("y/" + y);
			assertEquals(6, x, 0);
			assertEquals(21, y, 1);
			return true;
		}
		return false;
	}
}
