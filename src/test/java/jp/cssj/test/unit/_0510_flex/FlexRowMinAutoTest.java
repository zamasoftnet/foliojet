package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * min-size:auto(自動最小サイズ§4.5)の統合テストです(Flex F1e)。
 */
public class FlexRowMinAutoTest extends AbstractTestCase {
	public FlexRowMinAutoTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/row-min-auto.html");
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

	/**
	 * 自動最小サイズ(§4.5)の床: pはbasis 10ptだがmin-content(50ptの
	 * inline-block)が床になり、shrink方向不一致(base<hypothetical)で
	 * 事前freeze→50pt。qはshrink 0で100pt。
	 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 50, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}
}
