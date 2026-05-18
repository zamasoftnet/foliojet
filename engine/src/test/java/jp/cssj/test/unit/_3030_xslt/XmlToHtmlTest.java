package jp.cssj.test.unit._3030_xslt;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class XmlToHtmlTest extends AbstractTestCase {
	public XmlToHtmlTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3030-xslt/xml-to-html.xml");
		CTISessionHelper.transcodeFile(this.session, file, "text/xml", null);
	}

	public boolean check_a1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(y);
			assertEquals(y, 0, 0);
			return true;
		}
		return false;
	}

	public boolean check_a2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(y);
			assertEquals(y, 12, 0);
			return true;
		}
		return false;
	}

	public boolean check_a3(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(y);
			assertEquals(y, 24, 0);
			return true;
		}
		return false;
	}

	public boolean check_a4(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(y);
			assertEquals(y, 36, 0);
			return true;
		}
		return false;
	}

	public boolean check_a5(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(y);
			assertEquals(y, 48, 0);
			return true;
		}
		return false;
	}
}
