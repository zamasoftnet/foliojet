package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.impl.TableBox;
import net.zamasoft.foliojet.style.part.TableCollapsedBorders;
import jp.cssj.test.unit.AbstractTestCase;

public class BorderCollapseTest extends AbstractTestCase {
	public BorderCollapseTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/border-collapse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE_COLUMN) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("width: " + box.getWidth());
			assertEquals(102, x, 1);
			assertEquals(67.5, y, 0);
			assertEquals(104.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			TableBox table = (TableBox) box;
			TableCollapsedBorders borders = table.getCollapsedBorders();
			System.out.println(borders.getVBorder(0, 0).width);
			System.out.println(borders.getVBorder(0, 1).width);
			System.out.println(borders.getVBorder(0, 2).width);
			System.out.println(borders.getVBorder(0, 3).width);
			System.out.println(borders.getHBorder(0, 0).width);
			System.out.println(borders.getHBorder(0, 1).width);
			System.out.println(borders.getHBorder(0, 2).width);
			System.out.println(borders.getHBorder(0, 3).width);
			assertEquals(40, borders.getVBorder(0, 0).width, 0);
			assertEquals(10, borders.getVBorder(0, 1).width, 0);
			assertEquals(30, borders.getVBorder(0, 2).width, 0);
			assertEquals(30, borders.getVBorder(0, 3).width, 0);
			assertEquals(30, borders.getHBorder(0, 0).width, 0);
			assertEquals(20, borders.getHBorder(0, 1).width, 0);
			assertEquals(20, borders.getHBorder(0, 2).width, 0);
			assertEquals(30, borders.getHBorder(0, 3).width, 0);
			return true;
		}
		return false;
	}
}
