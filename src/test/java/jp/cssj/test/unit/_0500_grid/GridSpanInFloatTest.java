package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G4d span contribution test. Grid [40pt auto] inside a float without a specified width:
 * distributing the shortfall of a span2 item (min=max=100) gives the auto column a base of 60,
 * and Grid max-content=100 propagates to the float's shrink-to-fit width.
 * At bind time, the auto column also resolves to 60 (a width=100, b at x=+40).
 */
public class GridSpanInFloatTest extends AbstractTestCase {
	public GridSpanInFloatTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/explicit-placement-in-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** Float width = Grid max-content=100 (propagation includes span shortfall distribution). */
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(100.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** Span item: width = 40+60=100. */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			assertEquals(100.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}

	/** The auto column (60 pt after shortfall distribution) starts at +40, in the row after the spanning row. */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 40, x, 0.1);
			assertEquals(this.baseY + 10, y, 0.1);
			assertEquals(60.0, box.getWidth(), 0.1);
			return true;
		}
		return false;
	}
}
