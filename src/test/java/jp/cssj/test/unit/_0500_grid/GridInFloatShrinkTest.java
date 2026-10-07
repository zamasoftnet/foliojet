package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G3d2 test. A Grid [60pt auto] without a specified width inside a float without a specified width:
 * #p (40 pt) in slot0 occupies the fixed column; #q (30 pt) in slot1 contributes to the auto column.
 * The whole Grid's max-content=60+30=90 becomes the float's shrink-to-fit width,
 * and the auto column resolves to 90-60=30 at bind time. The second column starts at +60.
 */
public class GridInFloatShrinkTest extends AbstractTestCase {
	public GridInFloatShrinkTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/grid-in-float-shrink.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** Float width = Grid max-content=90 (propagation to shrink-to-fit). */
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(90.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 60, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}
}
