package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * justify-contentの主軸分配テストです(Flex F3b——§9.5。200ptコンテナに
 * 40pt×2 item=free 120pt: end=+120、center=+60、space-between=0/+160、
 * space-evenly=+40/+120)。
 */
public class FlexJustifyContentTest extends AbstractTestCase {
	public FlexJustifyContentTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/justify-content.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_s1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			return true;
		}
		return false;
	}

	public boolean check_e1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 120, x, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_c1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 60, x, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_c2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 100, x, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_b1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_b2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_v1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_v2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 120, x, 0.1);
			return true;
		}
		return false;
	}
}
