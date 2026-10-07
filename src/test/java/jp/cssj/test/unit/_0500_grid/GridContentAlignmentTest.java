package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G5c/G5e content alignment test. Columns [80pt auto], gap 10, width 300 pt, height 100 pt,
 * justify-content:center, align-content:center.
 * With positional alignment, the auto column does not stretch and stops at max-content=70.
 * The 160 pt track group leaves 140 pt of space → x offset 70. The 30 pt row group leaves 70 pt
 * of space (from the explicit height) → y offset 35. #after sits directly below the explicit height of 100.
 */
public class GridContentAlignmentTest extends AbstractTestCase {
	public GridContentAlignmentTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/alignment-content.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** Column 0 starts at contentX(70). Row is at contentY(35). */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 70, x, 0.1);
			assertEquals(this.baseY + 35, y, 0.1);
			return true;
		}
		return false;
	}

	/** Column 1 starts at 70+80+10=160. The auto column stays 70 pt without stretching. */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			assertEquals(this.baseY + 35, y, 0.1);
			assertEquals(70.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** Following block sits directly below the explicit height of 100 (alignment does not change Grid height). */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 100, y, 0.1);
			return true;
		}
		return false;
	}
}
