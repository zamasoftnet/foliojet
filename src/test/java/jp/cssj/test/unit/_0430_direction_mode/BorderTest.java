package jp.cssj.test.unit._0430_direction_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import jp.cssj.test.unit.AbstractTestCase;

public class BorderTest extends AbstractTestCase {
	public BorderTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0430-direction-mode/border.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("width: " + box.getWidth());
			System.err.println("height: " + box.getHeight());
			assertEquals(42, box.getWidth(), 1);
			assertEquals(42, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_cc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			InlineReplacedBox img = (InlineReplacedBox)box;
			RectBorder border = img.getFrame().frame.border;
			System.err.println("top: " + border.getTop().color);
			System.err.println("right: " + border.getRight().color);
			System.err.println("bottom: " + border.getBottom().color);
			System.err.println("left: " + border.getLeft().color);
			assertEquals(ColorValueUtils.YELLOW, border.getTop().color);
			assertEquals(ColorValueUtils.RED, border.getRight().color);
			assertEquals(ColorValueUtils.BLUE, border.getBottom().color);
			assertEquals(ColorValueUtils.PINK, border.getLeft().color);
			return true;
		}
		return false;
	}
}
