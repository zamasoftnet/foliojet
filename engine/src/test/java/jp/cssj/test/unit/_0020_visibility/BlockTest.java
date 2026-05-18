package jp.cssj.test.unit._0020_visibility;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BlockTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0020-visibility/block.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public BlockTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(0f, box.getParams().opacity, 0f);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(1f, box.getParams().opacity, 0f);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		assertEquals(1f, box.getParams().opacity, 0f);
		return true;
	}
}
