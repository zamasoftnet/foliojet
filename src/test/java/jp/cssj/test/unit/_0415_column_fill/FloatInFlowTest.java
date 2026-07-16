package jp.cssj.test.unit._0415_column_fill;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatInFlowTest extends AbstractTestCase {
	public FloatInFlowTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0415-column-fill/float-in-flow.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	int i = 0;

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("i: " + i);
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println("width: " + box.getWidth());
			System.err.println("height: " + box.getHeight());
			if (i == 0) {
				assertEquals(6, x, 1);
				assertEquals(6, y, 1);
				assertEquals(171, box.getWidth(), 1);
				assertEquals(72, box.getHeight(), 1);
			} else {
				assertEquals(201, x, 1);
				assertEquals(6, y, 1);
				assertEquals(171, box.getWidth(), 1);
				assertEquals(57, box.getHeight(), 1);
			}
			i++;
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("i: " + i);
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println("width: " + box.getWidth());
			System.err.println("height: " + box.getHeight());
			if (i == 2) {
				assertEquals(6, x, 1);
				assertEquals(140, y, 1);
				assertEquals(171, box.getWidth(), 1);
				assertEquals(114, box.getHeight(), 1);
			} else if (i == 3) {
				assertEquals(201, x, 1);
				assertEquals(140, y, 1);
				assertEquals(171, box.getWidth(), 1);
				assertEquals(57, box.getHeight(), 1);
			} else {
				assertEquals(201, x, 1);
				assertEquals(198, y, 1);
				assertEquals(171, box.getWidth(), 1);
				assertEquals(28, box.getHeight(), 1);
			}
			i++;
			return true;
		}
		return false;
	}
}
