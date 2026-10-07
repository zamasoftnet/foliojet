package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests gaps in line breaking and between lines (Flex F2c).
 * A 100 pt container with three basis 40 pt items and column-gap 30pt gives 40+30+40=110&gt;100,
 * so each item occupies its own line: three lines. Without gaps, this configuration forms two lines,
 * [p,q][r], so it verifies that gaps affect line breaking. row-gap 10pt separates the lines.
 */
public class FlexWrapGapTest extends AbstractTestCase {
	public FlexWrapGapTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/wrap-gap.html");
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

	/** Wrap including the gap (second line = +20+row-gap 10). */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 30, y, 0.1);
			return true;
		}
		return false;
	}

	/** Third line (+60). */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 60, y, 0.1);
			return true;
		}
		return false;
	}

	/** Following content sits immediately after three lines (20×3) + inter-line gaps (10×2). */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 80, y, 0.1);
			return true;
		}
		return false;
	}
}
