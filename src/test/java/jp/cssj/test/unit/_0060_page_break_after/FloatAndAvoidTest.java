package jp.cssj.test.unit._0060_page_break_after;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatAndAvoidTest extends AbstractTestCase {
	public FloatAndAvoidTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0060-page-break-after/float-and-avoid.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		System.out.println(pageNumber);
		assertEquals(1, pageNumber);
		if (box.getType() == BoxType.BLOCK) {
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		System.out.println(pageNumber);
		assertEquals(2, pageNumber);
		if (box.getType() == BoxType.BLOCK) {
			return true;
		}
		return false;
	}
}
