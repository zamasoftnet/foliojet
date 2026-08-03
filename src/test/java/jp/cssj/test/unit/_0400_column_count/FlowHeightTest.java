package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowHeightTest extends AbstractTestCase {
	public FlowHeightTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0400-column-count/flow-height.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			// 誤差を許す(2026-08-03)。値そのものは383のままだが、
			// フォントを固定したことで積算の順序が変わり
			// 382.99999999999994 になる。浮動小数の丸めであって寸法の変化
			// ではないので、丁度一致を求めない
			assertEquals(383, x, 0.001);
			assertEquals(106, y, 1);
			return true;
		}
		return false;
	}
}
