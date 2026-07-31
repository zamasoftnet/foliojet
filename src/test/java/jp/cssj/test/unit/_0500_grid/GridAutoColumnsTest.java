package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G3bのauto列テストです。列[80pt auto]・gap10/5・コンテナ幅300pt。
 * item内容は明示幅ブロック(min=max-contentが決定的): col1のcontribution
 * max=70だが、残余はstretchでauto列=300-80-10=210になる。行高=
 * 行内item実高の最大(行1=max(20,30)=30、行2開始=30+gap5=35)。
 */
public class GridAutoColumnsTest extends AbstractTestCase {
	public GridAutoColumnsTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/auto-columns.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** auto列(2列目)の開始=80+gap10。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 90, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 35, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 90, x, 0.1);
			assertEquals(this.baseY + 35, y, 0.1);
			return true;
		}
		return false;
	}

	/** Grid総高=30+5+25=60(親カーソル同期)。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 60, y, 0.1);
			return true;
		}
		return false;
	}
}
