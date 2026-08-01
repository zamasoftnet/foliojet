package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * wrap-reverseのテストです(Flex F5c——行の視覚順反転。論理行
 * [p,q](cross 30)/[r](20)が視覚では[r]が上、[p,q]が下になる。
 * 検査hookは配置(視覚)順のため基準はr(視覚先頭行)で取る)。
 */
public class FlexWrapReverseTest extends AbstractTestCase {
	public FlexWrapReverseTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/wrap-reverse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** 視覚先頭行は論理最終行のr(コンテナ上端)。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** 論理1行目[p,q]は下の行(+20)。 */
	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/** 後続はcross合計(20+30)の直後——行順反転は総高を変えない。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 50, y, 0.1);
			return true;
		}
		return false;
	}
}
