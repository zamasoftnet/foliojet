package jp.cssj.test.unit._0280_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ImageInCellTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0280-height/image-in-cell.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public ImageInCellTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_REPLACED) {
			System.out.println(box.getWidth() + "/" + box.getHeight());
			assertEquals(2, pageNumber);
			assertEquals(75, box.getWidth(), 0);
			assertEquals(75, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
