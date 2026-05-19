package jp.cssj.test.unit._2010_LIMIT;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowBorderTest extends AbstractTestCase {
	public FlowBorderTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/2010-LIMIT/flow-border.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertTrue(pageNumber <= 3);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertTrue(pageNumber <= 2);
		return true;
	}
}
