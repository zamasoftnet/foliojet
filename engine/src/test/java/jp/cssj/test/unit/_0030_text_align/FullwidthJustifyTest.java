package jp.cssj.test.unit._0030_text_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FullwidthJustifyTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0030-text-align/fullwidth-justify.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FullwidthJustifyTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.out.println(x);
			assertEquals(27, x, 1);
			return true;
		}
		return false;
	}
}
