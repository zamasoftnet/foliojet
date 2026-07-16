package jp.cssj.test.unit._0215_pagebreak_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class CenterAlignTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0215-pagebreak-table/center-align.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public CenterAlignTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(x);
			System.out.println(box.getWidth());
			assertEquals(117.25, x, 0);
			assertEquals(65.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
