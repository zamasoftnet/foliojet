package jp.cssj.test.unit._0090_page_break_before;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ClearByFlowTest extends AbstractTestCase {
	public ClearByFlowTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0090-page-break-before/clear-by-flow.html");
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
		assertEquals(3, pageNumber);
		return true;
	}
}
