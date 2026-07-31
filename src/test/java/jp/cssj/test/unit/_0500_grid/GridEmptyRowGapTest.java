package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G4cの空行gapテストです。grid-row:3のitemだけのGrid: 行0/1は
 * 高さ0だが行間gap(8pt)は残る(仕様のgutter挙動)——xの開始=
 * 0+8+0+8=16、総高=16+20=36。
 */
public class GridEmptyRowGapTest extends AbstractTestCase {
	public GridEmptyRowGapTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/empty-row-gap.html");
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

	/** 空行2つ分のgapだけ下(y=+16)。 */
	public boolean check_x(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 16, y, 0.1);
			return true;
		}
		return false;
	}

	/** 総高=16+20=36。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 36, y, 0.1);
			return true;
		}
		return false;
	}
}
