package jp.cssj.test.unit._0280_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BlockHeightTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0280-height/block-height.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public BlockHeightTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			assertEquals(3, pageNumber);
			System.out.println(y);
			assertEquals(4, y, 1);
			return true;
		}
		return false;
	}
}
