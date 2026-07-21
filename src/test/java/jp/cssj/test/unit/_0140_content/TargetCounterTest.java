package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TargetCounterTest extends AbstractTestCase {
	public TargetCounterTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/target-counter.html");
		this.session.property("processing.page-references", "true");
		this.session.property("processing.pass-count", "3");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("1", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("X", text.toString());
			return true;
		}
		return false;
	}
}
