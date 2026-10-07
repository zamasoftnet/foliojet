package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G4d row-span test. p=grid-row:1/span2 (actual height 50),
 * q/r=auto (actual heights 20/15). Starting with q/r's row heights of 20/15,
 * p's shortfall of 50-35=15 adds 7.5 to each row: row 0=27.5, row 1=22.5,
 * row 1 starts at 27.5; total height=50.
 */
public class GridRowSpanTest extends AbstractTestCase {
	public GridRowSpanTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long fallbacks = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_PLACEMENT_FALLBACKS.get();
		File file = new File("files/unittest/0500-grid/row-span-auto.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("rowSpanがフォールバックしないこと(G4dで解禁)", fallbacks,
				net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_PLACEMENT_FALLBACKS.get());
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** Second column of row 0. */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 60, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** Second column of row 1 (starts at 20+7.5=27.5). */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 60, x, 0.1);
			assertEquals(this.baseY + 27.5, y, 0.1);
			return true;
		}
		return false;
	}

	/** Total height = 27.5+22.5=50 (matches p's actual height). */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 50, y, 0.1);
			return true;
		}
		return false;
	}
}
