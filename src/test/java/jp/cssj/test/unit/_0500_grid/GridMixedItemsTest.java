package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G1の匿名item・置換要素itemのテストです。source-orderのslotは
 * 匿名(text-one)=0、#a=1、img#m=2、匿名(text-two)=3。行1高=
 * max(テキスト行, 30pt)=30、行2高=max(25pt, テキスト行)=25、
 * 総高=30+rowGap10+25=65。#gの位置を原点に相対検証する
 * (末尾の匿名itemはid検証できないが、#afterの位置=総高65が
 * 行所属の正しさを裏づける)。
 */
public class GridMixedItemsTest extends AbstractTestCase {
	public GridMixedItemsTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/mixed-items.html");
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

	/** slot 1(2列目): 匿名itemのtext-oneがslot 0を占めている証拠。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 120, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** slot 2(2行目1列目): 置換要素のone-shot item。行開始=30+gap10。 */
	public boolean check_m(IBox box, int pageNumber, double x, double y) {
		assertEquals(this.baseX, x, 0.1);
		assertEquals(this.baseY + 40, y, 0.1);
		return true;
	}

	/** 後続ブロック: Grid総高=30+10+25=65だけ下。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 65, y, 0.1);
			return true;
		}
		return false;
	}
}
