package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests wrap-reverse (Flex F5c, reversing visual line order).
 * Logical lines [p,q] (cross size 30) / [r] (20) become visual lines with [r] above [p,q].
 * Inspection hooks run in placement (visual) order, so r (the first visual line) provides the reference.
 */
public class FlexWrapReverseTest extends AbstractTestCase {
	public FlexWrapReverseTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/wrap-reverse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** The first visual line is r from the last logical line (at the container top). */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** Logical line 1 [p,q] is the lower line (+20). */
	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/**
	 * Following content sits immediately after the cross-size sum (20+30); reversing line order does not
	 * change total height.
	 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 50, y, 0.1);
			return true;
		}
		return false;
	}
}
