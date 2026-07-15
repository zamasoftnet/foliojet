package jp.cssj.test.unit._1000_TABLE;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class RulesAllTest extends AbstractTestCase {
	public RulesAllTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1000-TABLE/rules-all.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(72, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
