package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G5c/G5eのcontent alignmentテストです。列[80pt auto]・gap10・
 * 幅300pt・高さ100pt・justify-content:center・align-content:center。
 * positionalのためauto列はstretchせずmax-content=70で止まり、
 * トラック群160ptの余白140→x offset 70。行群30ptの余白70(明示高
 * 由来)→y offset 35。#afterは明示高100の直下。
 */
public class GridContentAlignmentTest extends AbstractTestCase {
	public GridContentAlignmentTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/alignment-content.html");
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

	/** 列0開始=contentX(70)。行=contentY(35)。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 70, x, 0.1);
			assertEquals(this.baseY + 35, y, 0.1);
			return true;
		}
		return false;
	}

	/** 列1開始=70+80+10=160。auto列はstretchせず70pt。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			assertEquals(this.baseY + 35, y, 0.1);
			assertEquals(70.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** 後続ブロック=明示高100の直下(alignmentでGrid高は変わらない)。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 100, y, 0.1);
			return true;
		}
		return false;
	}
}
