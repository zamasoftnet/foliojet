package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ImageInCellTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0240-table/image-in-cell.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public ImageInCellTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.err.println("width/" + box.getWidth());
			System.err.println("height/" + box.getHeight());
			assertEquals(83, box.getWidth(), 0);
			assertEquals(83, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
