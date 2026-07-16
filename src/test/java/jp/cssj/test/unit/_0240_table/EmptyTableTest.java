package jp.cssj.test.unit._0240_table;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class EmptyTableTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0240-table/empty-table.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public EmptyTableTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.err.println("width/" + box.getWidth());
			System.err.println("height/" + box.getHeight());
			assertEquals(70, box.getWidth(), 0);
			assertEquals(50, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.err.println("width/" + box.getWidth());
			System.err.println("height/" + box.getHeight());
			assertEquals(70, box.getWidth(), 0);
			assertEquals(50, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.err.println("width/" + box.getWidth());
			System.err.println("height/" + box.getHeight());
			assertEquals(70, box.getWidth(), 0);
			assertEquals(50, box.getHeight(), 0);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.err.println("width/" + box.getWidth());
			assertEquals(100, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.err.println("width/" + box.getWidth());
			assertEquals(100, box.getWidth(), 0);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.TABLE) {
			System.err.println("width/" + box.getWidth());
			assertEquals(100, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
