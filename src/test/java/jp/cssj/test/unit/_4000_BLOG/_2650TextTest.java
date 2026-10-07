package jp.cssj.test.unit._4000_BLOG;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class _2650TextTest extends AbstractTestCase {
	public _2650TextTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/4000-BLOG/2650-text.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		// Unifying whitespace/blank-line metrics (2026-08-09, pdfg2d) reduced line pitch,
		// moving the image that barely sat at the end of page 1 to page 2.
		assertEquals(2, pageNumber);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(2, pageNumber);
		return true;
	}
}
