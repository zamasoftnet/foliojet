package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests Flex in vertical writing (vertical-rl) (Flex F6).
 * As mapped by FlexAxes, the row main axis is the line axis (top to bottom), and the column main axis
 * is the page axis (right to left).
 * row: q is 40 pt below p (same line = same x). column: w is 40 pt left of v (same y).
 */
public class FlexVerticalTest extends AbstractTestCase {
	public FlexVerticalTest(String name) {
		super(name);
	}

	private double pX = Double.NaN, pY = Double.NaN;
	private double vX = Double.NaN, vY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/vertical-flex.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.pX = x;
			this.pY = y;
			return true;
		}
		return false;
	}

	/** Vertical-writing row: main axis = line axis (top to bottom). q is 40 pt below p, same x (same line). */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.pX, x, 0.1);
			assertEquals(this.pY + 40, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_v(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.vX = x;
			this.vY = y;
			return true;
		}
		return false;
	}

	/** Vertical-writing column: main axis = page axis (right to left). w is 40 pt left of v, same y. */
	public boolean check_w(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.vX - 40, x, 0.1);
			assertEquals(this.vY, y, 0.1);
			return true;
		}
		return false;
	}
}
