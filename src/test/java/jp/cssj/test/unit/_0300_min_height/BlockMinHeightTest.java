package jp.cssj.test.unit._0300_min_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BlockMinHeightTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0300-min-height/block-min-height.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public BlockMinHeightTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println(y);
			assertEquals(3, pageNumber, 0);
			assertEquals(4.5, y, 1);
			return true;
		}
		return false;
	}
}
