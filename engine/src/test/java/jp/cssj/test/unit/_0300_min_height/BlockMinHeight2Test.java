package jp.cssj.test.unit._0300_min_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BlockMinHeight2Test extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0300-min-height/block-min-height2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public BlockMinHeight2Test(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(box.getHeight());
			assertEquals(104, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			System.out.println(box.getHeight());
			assertEquals(102, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
