package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.impl.TextBlockBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ClassCopperTest extends AbstractTestCase {
	public ClassCopperTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/class-copper.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			assertEquals(ColorValueUtils.RED, ((TextBlockBox) box)
					.getBlockParams().color);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TEXT_BLOCK) {
			assertEquals(ColorValueUtils.BLACK, ((TextBlockBox) box)
					.getBlockParams().color);
			return true;
		}
		return false;
	}
}
