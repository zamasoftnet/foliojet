package jp.cssj.test.unit._0440_word_wrap;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BreakWordTest extends AbstractTestCase {
	public BreakWordTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0440-word-wrap/break-word.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("width: " + box.getWidth());
			System.err.println("height: " + box.getHeight());
			assertEquals(94, box.getWidth(), 1);
			assertEquals(336, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("width: " + box.getWidth());
			System.err.println("height: " + box.getHeight());
			assertEquals(94, box.getWidth(), 1);
			assertEquals(365, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
