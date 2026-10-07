package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Verifies that {@code string-set: name content();} (capturing the element's own rendered text)
 * is correctly reflected in {@code string()} across pages.
 * Since {@code content()} is not resolved until draw time, when the element's box is finalized,
 * references from subsequent elements on the same page are outside this test's scope
 * ({@code StringSetCounterTest} exhaustively tests cases resolved solely at build time).
 */
public class StringSetTest extends AbstractTestCase {
	public StringSetTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/string-set.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_fa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("Alpha", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_fb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("Beta", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_fc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("H2:Gamma", text.toString());
			return true;
		}
		return false;
	}
}
