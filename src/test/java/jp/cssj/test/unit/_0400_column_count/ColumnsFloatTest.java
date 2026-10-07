package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class ColumnsFloatTest extends AbstractTestCase {
	public ColumnsFloatTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0400-column-count/columns-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		System.out.println("pageNumber: " + pageNumber);
		assertEquals(214, x, 1);
		assertEquals(28, y, 1);
		assertEquals(1, pageNumber);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		System.out.println("pageNumber: " + pageNumber);
		// 2026-07-25: Removing the balancing probe returned the first multi-column layout's height
		// to ColumnBalancer's one-shot calculation (without convergence to measured minimum capacity),
		// so the second multi-column layout (b) is again placed at the start of page 2.
		//
		// **2026-08-06: y changed from 28 to 72.09**. Fixed a defect where an <img> that failed to load
		// ignored CSS width/height and collapsed to 0x0
		// (HTMLStyle.applyBrokenImage; added AltTextImage). The circle.svg referenced by this HTML
		// was never present in unittest (a broken reference). After the fix,
		// `img { width: 20mm }` correctly takes effect, giving the floated image actual dimensions
		// and moving the wrapping position in the multi-column layout downward.
		// Only the position changed; multi-column layout and floats are intact (visually confirmed).
		assertEquals(214, x, 1);
		assertEquals(72.09, y, 1);
		assertEquals(2, pageNumber);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		System.out.println("pageNumber: " + pageNumber);
		// 2026-07-25: Same as above (in the same multi-column layout as b, moved to page 2).
		// 2026-08-06: y changed for the same reason as check_b (see comment).
		assertEquals(28, x, 1);
		assertEquals(72.09, y, 1);
		assertEquals(2, pageNumber);
		return true;
	}
}
