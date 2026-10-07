package jp.cssj.test.unit._0400_column_count;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TableCellTest extends AbstractTestCase {
	public TableCellTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0400-column-count/table-cell.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.err.println("x: " + x);
			System.err.println("y: " + y);
			System.err.println(box.getWidth());
			System.err.println(box.getHeight());
			// Updated on 2026-07-26. The old expected values (94, 152, 50, 43)
			// came from layout that **counted column-gap repeatedly, once per column**.
			// The correct calculation counts the gap only once, regardless of the number of columns
			// (fixed cumulative multiplication in IntrinsicMeasurer).
			//
			// Cross-check of the new allocation: cell background frames are 113.33 and 475.17,
			// totaling 588.5 ≈ the 590 pt content area (600 pt − body border 5 pt × 2).
			// The column width of 36.16 pt fits three 12 pt Japanese characters, and the display list
			// indeed wraps at three characters per line.
			assertEquals(80.4, x, 1);
			assertEquals(211.8, y, 1);
			assertEquals(36.2, box.getWidth(), 1);
			assertEquals(57.6, box.getHeight(), 1);
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
			// Updated on 2026-07-26 (same reason as check_a).
			assertEquals(374.7, x, 1);
			assertEquals(145.3, y, 1);
			assertEquals(217.1, box.getWidth(), 1);
			assertEquals(14.4, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
