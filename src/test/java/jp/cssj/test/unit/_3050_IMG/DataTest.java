package jp.cssj.test.unit._3050_IMG;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class DataTest extends AbstractTestCase {
	public DataTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3050-IMG/data.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.err.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(120, box.getWidth(), 0);
			assertEquals(15, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.err.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(120, box.getWidth(), 0);
			assertEquals(15, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
