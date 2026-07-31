package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G4dのspan contributionテストです。幅なしfloat内のGrid
 * [40pt auto]: span2のitem(min=max=100)の不足分配でauto列の
 * base=60になり、Grid max-content=100がfloatのshrink-to-fit幅へ
 * 伝播する。bindでもauto列=60で確定(a幅=100、bはx=+40)。
 */
public class GridSpanInFloatTest extends AbstractTestCase {
	public GridSpanInFloatTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/explicit-placement-in-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** floatの幅=Grid max-content=100(span不足分配込みの伝播)。 */
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(100.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** span item: 幅=40+60=100。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			assertEquals(100.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** auto列(不足分配後60pt)の開始=+40、span行の次の行。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			assertEquals(this.baseY + 10, y, 0.1);
			assertEquals(60.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}
}
