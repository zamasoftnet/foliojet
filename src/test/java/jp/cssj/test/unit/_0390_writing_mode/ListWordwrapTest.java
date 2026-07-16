package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ListWordwrapTest extends AbstractTestCase {
	public ListWordwrapTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/list-wordwrap.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box instanceof OutsideMarkerBox) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("height: " + box.getHeight());
			assertEquals(163, x, 1);
			assertEquals(25, y, 0);
			assertEquals(0, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("height: " + box.getHeight());
			assertEquals(102.6, x, 1);
			assertEquals(40, y, 1);
			assertEquals(20, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
