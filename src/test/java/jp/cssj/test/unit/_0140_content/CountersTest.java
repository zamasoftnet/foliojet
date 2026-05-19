package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class CountersTest extends AbstractTestCase {
	public CountersTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/counters.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			StringBuffer text = new StringBuffer();
			box.getText(text);
			System.err.println(text);
			assertEquals("2.1 item", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			StringBuffer text = new StringBuffer();
			box.getText(text);
			System.err.println(text);
			assertEquals("2.3.1 item", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			StringBuffer text = new StringBuffer();
			box.getText(text);
			System.err.println(text);
			assertEquals("1 item", text.toString());
			return true;
		}
		return false;
	}
}
