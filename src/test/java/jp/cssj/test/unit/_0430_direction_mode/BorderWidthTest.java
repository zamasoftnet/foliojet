package jp.cssj.test.unit._0430_direction_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.style.box.params.RectBorder;
import jp.cssj.test.unit.AbstractTestCase;

public class BorderWidthTest extends AbstractTestCase {
	public BorderWidthTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0430-direction-mode/border-width.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.err.println("width: " + box.getWidth());
			System.err.println("height: " + box.getHeight());
			assertEquals(52, box.getWidth(), 1);
			assertEquals(62, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_REPLACED) {
			InlineReplacedBox img = (InlineReplacedBox)box;
			RectBorder border = img.getFrame().frame.border;
			System.err.println("top: " + border.getTop().width);
			System.err.println("right: " + border.getRight().width);
			System.err.println("bottom: " + border.getBottom().width);
			System.err.println("left: " + border.getLeft().width);
			assertEquals(20, border.getTop().width, 1);
			assertEquals(5, border.getRight().width, 1);
			assertEquals(10, border.getBottom().width, 1);
			assertEquals(15, border.getLeft().width, 1);
			return true;
		}
		return false;
	}
}
