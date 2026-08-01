package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * align-items/align-selfのcross整列テストです(Flex F3c——§9.6。
 * 行高40pt(tall item)に対し20pt item: center=+10、flex-end=+20、
 * align-self: flex-startのoverride=+0)。
 */
public class FlexAlignItemsTest extends AbstractTestCase {
	public FlexAlignItemsTest(String name) {
		super(name);
	}

	private double base1Y = Double.NaN, base2Y = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/align-items.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_tall1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.base1Y = y;
			return true;
		}
		return false;
	}

	public boolean check_c1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.base1Y + 10, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_tall2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.base2Y = y;
			return true;
		}
		return false;
	}

	public boolean check_e1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.base2Y + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/** align-self: flex-startがalign-items: flex-endをoverride。 */
	public boolean check_o1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.base2Y, y, 0.1);
			return true;
		}
		return false;
	}
}
