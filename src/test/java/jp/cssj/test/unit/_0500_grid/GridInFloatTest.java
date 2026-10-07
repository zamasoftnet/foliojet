package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Reconstructs a Grid inside a float without a specified width from its range,
 * and checks placement coordinates and recipe replay activation.
 */
public class GridInFloatTest extends AbstractTestCase {
	public GridInFloatTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long replaysBefore = net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory.GRID_REPLAYS.get();
		File file = new File("files/unittest/0500-grid/grid-in-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertTrue("範囲再生がGridBoxを再構築すること",
				net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory.GRID_REPLAYS.get() > replaysBefore);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** G3d1: actual track placement also applies inside a float (second column = +60). */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 60, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** Row 2, column 1. */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}
}
