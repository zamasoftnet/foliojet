package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G3d1 nested Grid test. Items use TwoPass recording, so a Grid inside an item is retained
 * as an execution plan (GridEvent) in the item's recording. Binding the item performs actual track
 * placement (recovering from G3a's temporary regression to G0's single column).
 * The outer [100pt 100pt] contains the inner [40pt 40pt] in slot0 and #b in slot1.
 */
public class GridNestedTest extends AbstractTestCase {
	public GridNestedTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/nested-grid.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_n1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** Second column of the inner Grid = +40 (actual track placement for a nested Grid). */
	public boolean check_n2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** Second column of the outer Grid = +100. */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 100, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** Total height = max(inner 10, b 30)=30 (row height comes from actual height at bind time). */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 30, y, 0.1);
			return true;
		}
		return false;
	}
}
