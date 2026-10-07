package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests column wrap (Flex F4d, limited to definite main (100 pt) and cross (200 pt) sizes).
 * Uses align-content: flex-start for tight placement, since the default normal=stretch adds free
 * space equally to column widths. Three items with basis 40 pt fit two per column
 * (40+40≦100; the third would give 40×3=120&gt;100), forming two columns.
 * Column width = explicit item width 50 pt: r is at x=+50/y=+0.
 */
public class FlexColumnWrapTest extends AbstractTestCase {
	public FlexColumnWrapTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/column-wrap.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** Second item in the same column (main axis +40 pt). */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 40, y, 0.1);
			return true;
		}
		return false;
	}

	/** Start of the second column (cross axis +50 pt = first column width; main axis +0). */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 50, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	private double w3X = Double.NaN, w3Y = Double.NaN;

	/**
	 * wrap-reverse: reversing column order puts logical column 2 (item 3) first visually (left).
	 * Cross-axis reversal makes align-items: flex-end select the start side (the column's left edge),
	 * symmetrically with row. Verifies the contract for the asymmetry resolved on 2026-08-02.
	 */
	public boolean check_w3(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.w3X = x;
			this.w3Y = y;
			assertEquals("コンテナ高(100)直下の次コンテナ左端", this.baseX, x, 0.1);
			return true;
		}
		return false;
	}

	/** Logical column 1 (items 1,2) is visual column 2 = +50. Alignment is also left (reversed flex-end). */
	public boolean check_w1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.w3X + 50, x, 0.1);
			assertEquals(this.w3Y, y, 0.1);
			return true;
		}
		return false;
	}
}
