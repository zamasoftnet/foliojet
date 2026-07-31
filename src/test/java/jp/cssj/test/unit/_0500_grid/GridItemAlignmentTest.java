package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G5b/G5dのitem alignmentテストです。列[100,100]・
 * justify-items:center・align-items:start。#a(max-content 40)は
 * 中央=x+30・上詰め。#bはjustify-self:end(x=100+60)・
 * align-self:center(行高40に対し実高10→y+15)。selfのoverrideと
 * fit-content幅(40)を固定する。
 */
public class GridItemAlignmentTest extends AbstractTestCase {
	public GridItemAlignmentTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/alignment-items.html");
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

	/** justify-items:center → fit-content幅40、x=+30。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 30, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			assertEquals(40.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** justify-self:end(x=100+60)・align-self:center(y=+15)。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			assertEquals(this.baseY + 15, y, 0.1);
			assertEquals(40.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** 行高=40(alignmentはtrack/行sizingを変えない)。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 40, y, 0.1);
			return true;
		}
		return false;
	}
}
