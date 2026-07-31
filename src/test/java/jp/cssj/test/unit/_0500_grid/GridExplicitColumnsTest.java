package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G4bの明示列配置テストです。列[60,60,60]・gap10/5・幅200pt。
 * a=1/3(列0-1、幅130)、b=-2/-1(最終列)、c=2/span2(行0が塞がって
 * いるためsparseで行1、幅130)、d=auto(cursorが行1末尾なので行2)、
 * e=1/span3(cursor列より戻るため行3、幅200)。行高25/20/15/10+gap5×3、
 * 総高=85。
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

	/** 1/3: 列0起点、幅=60+10+60=130(spanの内側gap込み)。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			assertEquals(130.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** -2/-1: 最終列(x=+140)。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 140, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 2/span2: 行0は占有済み→sparseで行1(y=+30)、列1起点(x=+70)。 */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 70, x, 0.1);
			assertEquals(this.baseY + 30, y, 0.1);
			assertEquals(130.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** auto: cursor(行1,列3)→行2列0(y=30+20+5=+55)。前行の穴は埋めない。 */
	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 55, y, 0.1);
			return true;
		}
		return false;
	}

	/** 1/span3: cursor列より戻る→行3(y=55+15+5=+75)、全幅200。 */
	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 75, y, 0.1);
			assertEquals(200.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** 総高=25+5+20+5+15+5+10=85。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 85, y, 0.1);
			return true;
		}
		return false;
	}
}
