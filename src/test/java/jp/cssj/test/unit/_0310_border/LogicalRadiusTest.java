package jp.cssj.test.unit._0310_border;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectBorder.Radius;

/**
 * Flow-relative corner radii ({@code border-start-start-radius} and the like) round the physical corner that the
 * element's writing-mode and direction give them, and the later of a physical and a flow-relative declaration wins
 * (2026-10-08, css-logical-1 §4 and §6.4; before, they were aliases of the horizontal ltr corners). The two radii are
 * not swapped. Expected corners and radii are Chrome's computed styles of the same document.
 */
public class LogicalRadiusTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0310-border/logical-radius.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public LogicalRadiusTest(String name) {
		super(name);
	}

	private static RectBorder border(final IBox box) {
		return ((FlowBlockBox) box).getFrame().frame.border;
	}

	private static void assertRadius(final String what, final double hr, final double vr, final Radius radius) {
		assertEquals(what + " horizontal", hr, radius.hr, 0.01);
		assertEquals(what + " vertical", vr, radius.vr, 0.01);
	}

	/** horizontal-tb ltr: start-start is the top left. */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertRadius("top-left", 10, 20, border(box).getTopLeft());
		assertRadius("top-right", 0, 0, border(box).getTopRight());
		return true;
	}

	/** direction: rtl: start-start is the top right. */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertRadius("top-right", 10, 20, border(box).getTopRight());
		assertRadius("top-left", 0, 0, border(box).getTopLeft());
		return true;
	}

	/** vertical-rl: block-start is the right, inline-start the top: the top right, radii not swapped. */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertRadius("top-right", 10, 20, border(box).getTopRight());
		assertRadius("top-left", 0, 0, border(box).getTopLeft());
		return true;
	}

	/** vertical-lr: start-start is the top left, end-end the bottom right. */
	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertRadius("top-left", 10, 20, border(box).getTopLeft());
		assertRadius("bottom-right", 6, 6, border(box).getBottomRight());
		return true;
	}

	/** border-radius, then border-start-start-radius: the later one decides the top left. */
	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertRadius("top-left", 10, 10, border(box).getTopLeft());
		assertRadius("bottom-right", 4, 4, border(box).getBottomRight());
		return true;
	}

	/** border-start-start-radius, then border-top-left-radius: the physical one is later. */
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertRadius("top-left", 4, 4, border(box).getTopLeft());
		return true;
	}

	/** vertical-rl: end-start (block-end = left, inline-start = top) is the top left. */
	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertRadius("top-left", 8, 8, border(box).getTopLeft());
		return true;
	}
}
