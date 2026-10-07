package jp.cssj.test.unit._0410_column_width;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteTest extends AbstractTestCase {
	public AbsoluteTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0410-column-width/absolute.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println(box.getWidth());
			System.err.println(box.getHeight());
			// **2026-08-06: width changed from 73 to 289.98, height from 92 to the measured value**.
			// Fixed a defect where an <img> that failed to load ignored CSS width/height
			// and collapsed to 0x0 (HTMLStyle.applyBrokenImage;
			// added AltTextImage). This HTML's kappa.png originally pointed to
			// another directory in unittest (a broken reference, resolved by copying it
			// directly under files/unittest). After the fix, `img{width:50%}` correctly takes effect,
			// and the actual dimensions are reflected in the shrink-to-fit width calculation for #a
			// (an absolutely positioned block with unspecified width and column-count:2).
			// Visually confirmed (the kappa image is displayed correctly;
			// multi-column layout and floats are intact).
			assertEquals(7, x, 1);
			assertEquals(7, y, 1);
			assertEquals(289.98, box.getWidth(), 1);
			assertEquals(96.1, box.getHeight(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println(box.getWidth());
			System.err.println(box.getHeight());
			assertEquals(15, x, 1);
			assertEquals(150, y, 1);
			assertEquals(229, box.getWidth(), 1);
			assertEquals(32.8, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
