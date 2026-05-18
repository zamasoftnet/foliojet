package jp.cssj.test.unit._0100_widows_orphans;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ImageLineTest extends AbstractTestCase {
	public ImageLineTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0100-widows-orphans/image-line.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(2, pageNumber);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		assertEquals(2, pageNumber);
		return true;
	}
}
