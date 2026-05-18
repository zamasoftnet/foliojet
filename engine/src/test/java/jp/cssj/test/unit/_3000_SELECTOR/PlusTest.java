package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.impl.InlineBox;
import jp.cssj.test.unit.AbstractTestCase;

public class PlusTest extends AbstractTestCase {
	public PlusTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/plus.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			assertEquals(ColorValueUtils.RED, ((InlineBox) box)
					.getInlineParams().color);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			assertEquals(ColorValueUtils.BLUE, ((InlineBox) box)
					.getInlineParams().color);
			return true;
		}
		return false;
	}
}
