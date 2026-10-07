package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G1 anonymous-item and replaced-element-item test. Source-order slots:
 * anonymous (text-one)=0, #a=1, img#m=2, anonymous (text-two)=3.
 * Row 1 height=max(text line, 30 pt)=30; row 2 height=max(25 pt, text line)=25;
 * total height=30+rowGap 10+25=65. Checks coordinates relative to #g's position
 * (the final anonymous item cannot be checked by id, but #after's position at total height 65
 * confirms correct row membership).
 */
public class GridMixedItemsTest extends AbstractTestCase {
	public GridMixedItemsTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/mixed-items.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** Slot 1 (second column): proves that the anonymous text-one item occupies slot 0. */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 120, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** Slot 2 (row 2, column 1): a one-shot replaced-element item. Row start = 30+gap 10. */
	public boolean check_m(IBox box, int pageNumber, double x, double y) {
		assertEquals(this.baseX, x, 0.1);
		assertEquals(this.baseY + 40, y, 0.1);
		return true;
	}

	/** Following block: lower by total Grid height = 30+10+25=65. */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 65, y, 0.1);
			return true;
		}
		return false;
	}
}
