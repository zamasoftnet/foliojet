package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G4c explicit row test. Columns [60,60], gap 10/5. a/b=grid-row:2
 * (the row's sparse cursor places them in column 0/column 1), c=grid-column:2 (auto row → row 0),
 * d=auto (cursor at the end of row 0 → row 1 is occupied → row 2).
 * Row heights 15/25/10; row starts 0/20/50; total height 60.
 */
public class GridExplicitRowsTest extends AbstractTestCase {
	public GridExplicitRowsTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long fallbacks = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_PLACEMENT_FALLBACKS.get();
		File file = new File("files/unittest/0500-grid/explicit-rows-sparse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("明示配置がフォールバックしないこと", fallbacks,
				net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_PLACEMENT_FALLBACKS.get());
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** First item with grid-row:2 (row 1): row cursor selects column 0. Row starts at 15+gap 5=20. */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/** Second item with grid-row:2: row cursor selects column 1 (x=+70). */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 70, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/** grid-column:2, auto row: column 1 in row 0. */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 70, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** auto: row 1 is occupied → row 2, column 0 (y=20+25+5=+50). */
	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 50, y, 0.1);
			return true;
		}
		return false;
	}

	/** Total height = 15+5+25+5+10=60. */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 60, y, 0.1);
			return true;
		}
		return false;
	}
}
