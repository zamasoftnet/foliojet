package jp.cssj.test.unit._0310_border;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.style.box.params.Border;
import net.zamasoft.foliojet.style.box.params.RectBorder;
import jp.cssj.test.unit.AbstractTestCase;

public class BlockBorderTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0310-border/block-border.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public BlockBorderTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			FlowBlockBox flowBox = (FlowBlockBox) box;
			RectBorder border = flowBox.getFrame().frame.border;
			assertEquals(1, border.getLeft().width, 0);
			assertEquals(4, border.getTop().width, 0);
			assertEquals(7, border.getRight().width, 0);
			assertEquals(10, border.getBottom().width, 0);
			assertEquals(ColorValueUtils.RED, border.getLeft().color);
			assertEquals(ColorValueUtils.BLUE, border.getTop().color);
			assertEquals(ColorValueUtils.YELLOW, border.getRight().color);
			assertEquals(ColorValueUtils.GREEN, border.getBottom().color);
			assertEquals(Border.DOTTED, border.getLeft().style);
			assertEquals(Border.DOTTED, border.getTop().style);
			assertEquals(Border.DOTTED, border.getRight().style);
			assertEquals(Border.DOTTED, border.getBottom().style);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			FlowBlockBox flowBox = (FlowBlockBox) box;
			RectBorder border = flowBox.getFrame().frame.border;
			assertEquals(1, border.getLeft().width, 0);
			assertEquals(4, border.getTop().width, 0);
			assertEquals(7, border.getRight().width, 0);
			assertEquals(10, border.getBottom().width, 0);
			assertEquals(ColorValueUtils.RED, border.getLeft().color);
			assertEquals(ColorValueUtils.BLUE, border.getTop().color);
			assertEquals(ColorValueUtils.YELLOW, border.getRight().color);
			assertEquals(ColorValueUtils.GREEN, border.getBottom().color);
			assertEquals(Border.DASHED, border.getLeft().style);
			assertEquals(Border.DASHED, border.getTop().style);
			assertEquals(Border.DASHED, border.getRight().style);
			assertEquals(Border.DASHED, border.getBottom().style);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			FlowBlockBox flowBox = (FlowBlockBox) box;
			RectBorder border = flowBox.getFrame().frame.border;
			assertEquals(1, border.getLeft().width, 0);
			assertEquals(4, border.getTop().width, 0);
			assertEquals(7, border.getRight().width, 0);
			assertEquals(10, border.getBottom().width, 0);
			assertEquals(ColorValueUtils.RED, border.getLeft().color);
			assertEquals(ColorValueUtils.BLUE, border.getTop().color);
			assertEquals(ColorValueUtils.YELLOW, border.getRight().color);
			assertEquals(ColorValueUtils.GREEN, border.getBottom().color);
			assertEquals(Border.SOLID, border.getLeft().style);
			assertEquals(Border.SOLID, border.getTop().style);
			assertEquals(Border.SOLID, border.getRight().style);
			assertEquals(Border.SOLID, border.getBottom().style);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			FlowBlockBox flowBox = (FlowBlockBox) box;
			RectBorder border = flowBox.getFrame().frame.border;
			assertEquals(1, border.getLeft().width, 0);
			assertEquals(4, border.getTop().width, 0);
			assertEquals(7, border.getRight().width, 0);
			assertEquals(10, border.getBottom().width, 0);
			assertEquals(ColorValueUtils.RED, border.getLeft().color);
			assertEquals(ColorValueUtils.BLUE, border.getTop().color);
			assertEquals(ColorValueUtils.YELLOW, border.getRight().color);
			assertEquals(ColorValueUtils.GREEN, border.getBottom().color);
			assertEquals(Border.DOUBLE, border.getLeft().style);
			assertEquals(Border.DOUBLE, border.getTop().style);
			assertEquals(Border.DOUBLE, border.getRight().style);
			assertEquals(Border.DOUBLE, border.getBottom().style);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			FlowBlockBox flowBox = (FlowBlockBox) box;
			RectBorder border = flowBox.getFrame().frame.border;
			assertEquals(1, border.getLeft().width, 0);
			assertEquals(4, border.getTop().width, 0);
			assertEquals(7, border.getRight().width, 0);
			assertEquals(10, border.getBottom().width, 0);
			assertEquals(ColorValueUtils.RED, border.getLeft().color);
			assertEquals(ColorValueUtils.BLUE, border.getTop().color);
			assertEquals(ColorValueUtils.YELLOW, border.getRight().color);
			assertEquals(ColorValueUtils.GREEN, border.getBottom().color);
			assertEquals(Border.GROOVE, border.getLeft().style);
			assertEquals(Border.GROOVE, border.getTop().style);
			assertEquals(Border.GROOVE, border.getRight().style);
			assertEquals(Border.GROOVE, border.getBottom().style);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			FlowBlockBox flowBox = (FlowBlockBox) box;
			RectBorder border = flowBox.getFrame().frame.border;
			assertEquals(1, border.getLeft().width, 0);
			assertEquals(4, border.getTop().width, 0);
			assertEquals(7, border.getRight().width, 0);
			assertEquals(10, border.getBottom().width, 0);
			assertEquals(ColorValueUtils.RED, border.getLeft().color);
			assertEquals(ColorValueUtils.BLUE, border.getTop().color);
			assertEquals(ColorValueUtils.YELLOW, border.getRight().color);
			assertEquals(ColorValueUtils.GREEN, border.getBottom().color);
			assertEquals(Border.RIDGE, border.getLeft().style);
			assertEquals(Border.RIDGE, border.getTop().style);
			assertEquals(Border.RIDGE, border.getRight().style);
			assertEquals(Border.RIDGE, border.getBottom().style);
			return true;
		}
		return false;
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			FlowBlockBox flowBox = (FlowBlockBox) box;
			RectBorder border = flowBox.getFrame().frame.border;
			assertEquals(1, border.getLeft().width, 0);
			assertEquals(4, border.getTop().width, 0);
			assertEquals(7, border.getRight().width, 0);
			assertEquals(10, border.getBottom().width, 0);
			assertEquals(ColorValueUtils.RED, border.getLeft().color);
			assertEquals(ColorValueUtils.BLUE, border.getTop().color);
			assertEquals(ColorValueUtils.YELLOW, border.getRight().color);
			assertEquals(ColorValueUtils.GREEN, border.getBottom().color);
			assertEquals(Border.INSET, border.getLeft().style);
			assertEquals(Border.INSET, border.getTop().style);
			assertEquals(Border.INSET, border.getRight().style);
			assertEquals(Border.INSET, border.getBottom().style);
			return true;
		}
		return false;
	}

	public boolean check_h(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			FlowBlockBox flowBox = (FlowBlockBox) box;
			RectBorder border = flowBox.getFrame().frame.border;
			assertEquals(1, border.getLeft().width, 0);
			assertEquals(4, border.getTop().width, 0);
			assertEquals(7, border.getRight().width, 0);
			assertEquals(10, border.getBottom().width, 0);
			assertEquals(ColorValueUtils.RED, border.getLeft().color);
			assertEquals(ColorValueUtils.BLUE, border.getTop().color);
			assertEquals(ColorValueUtils.YELLOW, border.getRight().color);
			assertEquals(ColorValueUtils.GREEN, border.getBottom().color);
			assertEquals(Border.OUTSET, border.getLeft().style);
			assertEquals(Border.OUTSET, border.getTop().style);
			assertEquals(Border.OUTSET, border.getRight().style);
			assertEquals(Border.OUTSET, border.getBottom().style);
			return true;
		}
		return false;
	}
}
