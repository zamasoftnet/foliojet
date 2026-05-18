package jp.cssj.test.unit._0430_direction_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.style.box.params.Border;
import net.zamasoft.foliojet.style.box.params.RectBorder;
import jp.cssj.test.unit.AbstractTestCase;

public class BorderStyleTest extends AbstractTestCase {
	public BorderStyleTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0430-direction-mode/border-style.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println("width: " + box.getWidth());
			System.err.println("height: " + box.getHeight());
			assertEquals(36, box.getWidth(), 1);
			assertEquals(36, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_REPLACED) {
			InlineReplacedBox img = (InlineReplacedBox)box;
			RectBorder border = img.getFrame().frame.border;
			System.err.println("top: " + border.getTop().style);
			System.err.println("right: " + border.getRight().style);
			System.err.println("bottom: " + border.getBottom().style);
			System.err.println("left: " + border.getLeft().style);
			assertEquals(Border.DOTTED, border.getTop().style);
			assertEquals(Border.SOLID, border.getRight().style);
			assertEquals(Border.DASHED, border.getBottom().style);
			assertEquals(Border.DOUBLE, border.getLeft().style);
			return true;
		}
		return false;
	}
}
