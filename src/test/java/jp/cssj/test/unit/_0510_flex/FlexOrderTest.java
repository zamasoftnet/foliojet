package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * orderの視覚順テストです(Flex F5a——§5.4。ソース順a,b(-1),c(1),dが
 * 視覚順b,a,d,cになる。同値orderは録画順の安定ソート。bindはソース順の
 * まま=Tagged PDFの読み順・構造は不変——答申F5a)。
 */
public class FlexOrderTest extends AbstractTestCase {
	public FlexOrderTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/order-basic.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** order:-1のbが視覚先頭。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 同値order(0)のdはaの後(安定ソート)。 */
	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 80, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** order:1のcが視覚末尾。 */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 120, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}
}
