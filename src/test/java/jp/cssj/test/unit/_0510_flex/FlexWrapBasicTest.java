package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * row wrapの配置テストです(Flex F2b——§9.3行分割の本配線。
 * 100ptコンテナにbasis 40pt×3→2行[p,q][r]、行cross=行内最大、
 * 後続ブロックは全行cross合計の直後)。
 */
public class FlexWrapBasicTest extends AbstractTestCase {
	public FlexWrapBasicTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/wrap-basic.html");
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

	/** 同一行の2番目(+40pt)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 2行目の先頭(x=基点、y=1行目cross 30ptの直後)。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 30, y, 0.1);
			return true;
		}
		return false;
	}

	/** 後続ブロックは全行cross合計(30+20)の直後。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 50, y, 0.1);
			return true;
		}
		return false;
	}
}
