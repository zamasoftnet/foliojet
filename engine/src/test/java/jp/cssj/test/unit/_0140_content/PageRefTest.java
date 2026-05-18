package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class PageRefTest extends AbstractTestCase {
	public PageRefTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/page-ref.html");
		this.session.property("processing.page-references", "true");
		this.session.property("processing.pass-count", "3");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			StringBuffer text = new StringBuffer();
			box.getText(text);
			assertEquals("4,8", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			StringBuffer text = new StringBuffer();
			box.getText(text);
			assertEquals("4,8", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			StringBuffer text = new StringBuffer();
			box.getText(text);
			assertEquals("6", text.toString());
			return true;
		}
		return false;
	}
}
