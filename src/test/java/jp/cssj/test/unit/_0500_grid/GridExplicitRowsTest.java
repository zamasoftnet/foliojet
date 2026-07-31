package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G4cの明示行テストです。列[60,60]・gap10/5。a/b=grid-row:2
 * (行内sparse cursorで列0/列1)、c=grid-column:2(行auto→行0)、
 * d=auto(cursorが行0末尾→行1は占有済み→行2)。行高15/25/10、
 * 行開始0/20/50、総高60。
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

	/** grid-row:2(行1)の1個目: 行内cursorで列0。行開始=15+gap5=20。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/** grid-row:2の2個目: 行内cursorで列1(x=+70)。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 70, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/** grid-column:2・行auto: 行0の列1。 */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 70, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** auto: 行1は占有済み→行2列0(y=20+25+5=+50)。 */
	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 50, y, 0.1);
			return true;
		}
		return false;
	}

	/** 総高=15+5+25+5+10=60。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 60, y, 0.1);
			return true;
		}
		return false;
	}
}
