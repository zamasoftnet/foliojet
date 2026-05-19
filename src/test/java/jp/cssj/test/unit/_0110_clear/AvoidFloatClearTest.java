package jp.cssj.test.unit._0110_clear;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AvoidFloatClearTest extends AbstractTestCase {
	public AvoidFloatClearTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0110-clear/avoid-float-clear.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(x, 0, 0);
		assertEquals(1, pageNumber);
		return true;
	}
}
