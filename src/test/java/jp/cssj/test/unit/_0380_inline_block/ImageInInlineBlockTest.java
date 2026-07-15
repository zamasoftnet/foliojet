package jp.cssj.test.unit._0380_inline_block;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ImageInInlineBlockTest extends AbstractTestCase {
	public ImageInInlineBlockTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0380-inline-block/image-in-inline-block.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.err.println(box.getHeight());
			System.err.println(box.getWidth());
			assertEquals(75, box.getHeight(), 0);
			assertEquals(75, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.err.println(box.getHeight());
			System.err.println(box.getWidth());
			assertEquals(75, box.getHeight(), 0);
			assertEquals(75, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
