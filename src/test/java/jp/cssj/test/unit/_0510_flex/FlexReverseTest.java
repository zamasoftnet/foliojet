package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests row-reverse/column-reverse (Flex F5b, main-axis reversal).
 * Default row-reverse (=flex-start=right edge): a=+160/b=+120.
 * row-reverse+flex-end (=left edge): q=+0/p=+40.
 * column-reverse (height 100 pt, flex-start=bottom): two basis 40 pt items = 80 pt align to the bottom,
 * leaving 20 pt at the top.
 * Inspection hooks run in placement (visual) order, so the first visual item provides the reference.
 */
public class FlexReverseTest extends AbstractTestCase {
	public FlexReverseTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN;
	private double qY = Double.NaN, wY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/row-reverse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** The first visual item (leftmost) is b, second in source order (+120); take the reference here. */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x - 120;
			return true;
		}
		return false;
	}

	/** a, first in source order, is at the right edge (main-axis start = right). */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			return true;
		}
		return false;
	}

	/** row-reverse+flex-end = left edge: q is visually first (+0). */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			this.qY = y;
			return true;
		}
		return false;
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			return true;
		}
		return false;
	}

	/**
	 * column-reverse: main-axis size is basis 40 pt (not height). Bottom alignment
	 * (flex-start=bottom) puts the remaining 20 pt at the start, so the first visual item w is at
	 * container top +20.
	 */
	public boolean check_w(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.wY = y;
			assertEquals(this.qY + 20 + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/** v, first in source order, is at the bottom (directly below w, +40). */
	public boolean check_v(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.wY + 40, y, 0.1);
			return true;
		}
		return false;
	}
}
