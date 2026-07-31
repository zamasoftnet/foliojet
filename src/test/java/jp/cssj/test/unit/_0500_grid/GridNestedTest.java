package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G3d1のネストGridテストです。itemはTwoPass録画のため、item内の
 * Gridは実行計画(GridEvent)としてitemの録画に保持され、itemのbindで
 * 実トラック配置になる(G3aの一時退行=G0単一列の回復)。
 * 外側[100pt 100pt]のslot0に内側[40pt 40pt]、slot1に#b。
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

	/** 内側Gridの2列目=+40(ネストGridの実トラック配置)。 */
	public boolean check_n2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 外側Gridの2列目=+100。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 100, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 総高=max(内側10, b30)=30(行高がbindの実高から来る)。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 30, y, 0.1);
			return true;
		}
		return false;
	}
}
