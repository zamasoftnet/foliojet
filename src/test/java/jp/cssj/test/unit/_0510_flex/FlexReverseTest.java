package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * row-reverse/column-reverseのテストです(Flex F5b——主軸反転。
 * row-reverse既定(=flex-start=右端): a=+160/b=+120。
 * row-reverse+flex-end(=左端): q=+0/p=+40。
 * column-reverse(高さ100pt、=flex-start=下端): basis 40pt×2=80ptが
 * 下詰めされ残余20ptが上に入る。
 * 検査hookは配置(視覚)順に呼ばれるため、基準は視覚先頭のitemで取る。
 */
public class FlexReverseTest extends AbstractTestCase {
	public FlexReverseTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN;
	private double qY = Double.NaN, wY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/row-reverse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** 視覚先頭(左端)はソース2番目のb(+120)——基準を取る。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x - 120;
			return true;
		}
		return false;
	}

	/** ソース1番目のaが右端(主軸start=右)。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			return true;
		}
		return false;
	}

	/** row-reverse+flex-end=左端: 視覚先頭はq(+0)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			this.qY = y;
			return true;
		}
		return false;
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			return true;
		}
		return false;
	}

	/**
	 * column-reverse: 主軸寸法はbasis 40pt(heightではない)。残余20ptが
	 * 下詰め(flex-start=下端)で先頭に入り、視覚先頭のwはコンテナ上端+20。
	 */
	public boolean check_w(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.wY = y;
			assertEquals(this.qY + 20 + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/** ソース1番目のvが下端(wの直下+40)。 */
	public boolean check_v(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.wY + 40, y, 0.1);
			return true;
		}
		return false;
	}
}
