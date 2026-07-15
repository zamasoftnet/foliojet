package jp.cssj.test.unit._1000_TABLE;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ImageInCellTest extends AbstractTestCase {
	public ImageInCellTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1000-TABLE/image-in-cell.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(159.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(159.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.out.println(box.getWidth());
			assertEquals(159.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}
	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(box.getWidth());
			assertEquals(37.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}
	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(box.getWidth());
			assertEquals(37.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(box.getWidth());
			assertEquals(37.5, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
