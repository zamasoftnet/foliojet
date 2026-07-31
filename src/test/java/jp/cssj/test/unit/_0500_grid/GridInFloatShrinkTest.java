package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G3d2のテストです。幅なしfloat内の幅なしGrid[60pt auto]:
 * slot0の#p(40pt)はfixed列、slot1の#q(30pt)がauto列のcontribution。
 * Grid全体のmax-content=60+30=90がfloatのshrink-to-fit幅になり、
 * auto列はbind時に90-60=30で解決される。2列目開始=+60。
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

	/** floatの幅=Grid max-content=90(shrink-to-fitへの伝播)。 */
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
