package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G1のトラック配置テストです(consult-codex-2026-07-31-grid-g1.txt
 * §4)。固定2列(100pt 100pt、gap 20/10)へ4item(高さ30/50/20/40)を
 * source-orderで置く。#aを原点とした相対座標で列開始(0/120)・行開始
 * (0/60=50+rowGap10)・Grid総高(100=50+10+40)を検証する。
 */
public class GridFixedLayoutTest extends AbstractTestCase {
	public GridFixedLayoutTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/fixed-2x2.html");
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

	/** 2列目: 列開始=100+columnGap20。同一行なのでyは#aと同じ。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 120, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 2行目1列目: 行開始=行1高max(30,50)+rowGap10=60。 */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 60, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 120, x, 0.1);
			assertEquals(this.baseY + 60, y, 0.1);
			return true;
		}
		return false;
	}

	/** 後続ブロック: Grid総高=50+10+40=100だけ下(親カーソル同期の検証)。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 100, y, 0.1);
			return true;
		}
		return false;
	}
}
