package jp.cssj.test.unit._0242_table_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class TableCellRowspanHeightTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0242-table-height/table-cell-rowspan-height.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public TableCellRowspanHeightTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(1, pageNumber);
			return true;
		}
		return false;
	}

	/**
	 * Text in the cell in row 2 (height:200pt, one line of content).
	 *
	 * <p>
	 * Updated from page 4 to page 3 on 2026-07-27. With the default {@code vertical-align} (middle), this
	 * cell's content is centered exactly at the page 3/4 boundary. Previously, no line remained in the first
	 * fragment: <b>only the frame appeared on page 3, while the text appeared on page 4</b>
	 * (the same mechanism as reading-order reversal, detected by invariant 7).
	 * Now {@code TableCellBox.split} reduces alignment space to keep the first indivisible unit in the
	 * preceding fragment, so the text appears on page 3 with its frame.
	 * </p>
	 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(3, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(5, pageNumber);
			return true;
		}
		return false;
	}
}
