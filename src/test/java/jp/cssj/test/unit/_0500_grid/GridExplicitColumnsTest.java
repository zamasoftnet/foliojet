package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G4b explicit column placement test. Columns [60,60,60], gap 10/5, width 200 pt.
 * a=1/3 (columns 0-1, width 130), b=-2/-1 (last column), c=2/span2 (row 0 is occupied, so sparse
 * placement uses row 1, width 130), d=auto (cursor is at the end of row 1, so row 2),
 * e=1/span3 (moves back from the cursor column, so row 3, width 200).
 * Row heights 25/20/15/10 + gap 5×3; total height = 85.
 */
public class GridExplicitColumnsTest extends AbstractTestCase {
	public GridExplicitColumnsTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long fallbacks = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_PLACEMENT_FALLBACKS.get();
		File file = new File("files/unittest/0500-grid/explicit-columns.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("明示配置がフォールバックしないこと", fallbacks,
				net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_PLACEMENT_FALLBACKS.get());
	}

	/** 1/3: starts at column 0; width = 60+10+60=130 (including the gap inside the span). */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			assertEquals(130.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** -2/-1: last column (x=+140). */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 140, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 2/span2: row 0 is occupied → sparse placement uses row 1 (y=+30), starting at column 1 (x=+70). */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 70, x, 0.1);
			assertEquals(this.baseY + 30, y, 0.1);
			assertEquals(130.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** auto: cursor (row 1, column 3) → row 2, column 0 (y=30+20+5=+55). Does not fill holes in earlier rows. */
	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 55, y, 0.1);
			return true;
		}
		return false;
	}

	/** 1/span3: moves back from the cursor column → row 3 (y=55+15+5=+75), full width 200. */
	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 75, y, 0.1);
			assertEquals(200.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** Total height = 25+5+20+5+15+5+10=85. */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 85, y, 0.1);
			return true;
		}
		return false;
	}
}
