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

	/** The first item's (#a) outside marker: the rightmost one in vertical-rl. {x, y, height}. */
	private double[] marker;

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/list-wordwrap.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertNotNull("no outside marker drawn", this.marker);
		assertEquals(163, this.marker[0], 1);
		// The bullet image is 0.7em long (ListBulletImage, 2026-10-08), so the box starts 3pt later; the dot stays at 32.
		assertEquals(28, this.marker[1], 0);
		assertEquals(0, this.marker[2], 0);
	}

	// The marker is a ::marker child style without the li's id (2026-10-08), so it is found by type.
	protected void visitAnyBox(IBox box, int pageNumber, double x, double y) {
		if (box instanceof OutsideMarkerBox && (this.marker == null || x > this.marker[0])) {
			this.marker = new double[] { x, y, box.getHeight() };
		}
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("x: " + x);
			System.out.println("y: " + y);
			System.out.println("height: " + box.getHeight());
			assertEquals(102.6, x, 1);
			assertEquals(45, y, 1);
			assertEquals(20, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
