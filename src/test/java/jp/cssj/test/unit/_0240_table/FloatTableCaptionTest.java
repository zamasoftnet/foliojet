package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatTableCaptionTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0240-table/float-table-caption.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public FloatTableCaptionTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("x: "+x);
			System.err.println("y: "+y);
			System.err.println("w: "+box.getWidth());
			System.err.println("h: "+box.getHeight());
			assertEquals(151, x, 1);
			assertEquals(0, y, 1);
			assertEquals(48, box.getWidth(), 1);
			assertEquals(32.8, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x: "+x);
			System.err.println("y: "+y);
			System.err.println("w: "+box.getWidth());
			System.err.println("h: "+box.getHeight());
			assertEquals(0, x, 1);
			assertEquals(0, y, 1);
			assertEquals(69, box.getWidth(), 1);
			assertEquals(13, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
