package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * gapの行分割・行間テストです(Flex F2c——100ptコンテナ、basis 40pt×3+
 * column-gap 30pt: 40+30+40=110&gt;100のため各itemが1行ずつ=3行。
 * gapなしなら[p,q][r]の2行になる構成で、gapが行分割に効くことを固定。
 * row-gap 10ptが行間に入る)。
 */
public class FlexWrapGapTest extends AbstractTestCase {
	public FlexWrapGapTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/wrap-gap.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** gap込みで折り返し(2行目=+20+row-gap 10)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 30, y, 0.1);
			return true;
		}
		return false;
	}

	/** 3行目(+60)。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 60, y, 0.1);
			return true;
		}
		return false;
	}

	/** 後続=3行(20×3)+行間gap(10×2)の直後。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 80, y, 0.1);
			return true;
		}
		return false;
	}
}
