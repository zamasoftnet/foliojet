package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class NestTest extends AbstractTestCase {
	public NestTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0400-column-count/nest.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(105, x, 0);
			assertEquals(9, y, 1);
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("pageNumber: " + pageNumber);
			assertEquals(105, x, 0);
			assertEquals(19, y, 1);
			assertEquals(2, pageNumber);
			return true;
		}
		return false;
	}
}
