package jp.cssj.test.unit._0219_pagebreak_table_inrow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Checks physical coordinates for intra-row (intra-cell) splitting and vertical-align (horizontal writing).
 * Protects the A-3b physical alignment contract (development record):
 * <ul>
 * <li>All cells in a row split at the same physical split line (page bottom).</li>
 * <li>middle/bottom/baseline verticalAlign is computed from measured differences.</li>
 * <li>Continuation cells start at verticalAlign=0 (page top) and are not realigned.</li>
 * <li>rowspan cells split after cellCutPageAxis converts to coordinates relative to the cell start.</li>
 * </ul>
 * Checking continuation content on page 2 and later itself verifies that the fixture actually exercises
 * intra-row splitting (it does not fit on one page).
 * The complete display list is fixed by the DisplayListGoldenTest golden
 * (files/unittest/display-list-golden/0219-pagebreak-table-inrow_valign-split).
 */
public class ValignSplitTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0219-pagebreak-table-inrow/valign-split.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public ValignSplitTest(String name) {
		super(name);
	}

	/** First line of the top cell: row start on page 1 (y=0). */
	public boolean check_ta(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ta: " + y);
			assertEquals(1, pageNumber);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	/** Line 11 of the first cell, which determines row height: second line on page 2 (continuation starts at y=0). */
	public boolean check_drva(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("drva: " + y);
			assertEquals(2, pageNumber);
			assertEquals(9.6, y, 1);
			return true;
		}
		return false;
	}

	/** First line of the baseline cell (20 pt): determines rowAscent. Near the row start on page 1. */
	public boolean check_bl1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bl1: " + y);
			assertEquals(1, pageNumber);
			assertEquals(-0.6, y, 1);
			return true;
		}
		return false;
	}

	/** First line of the baseline cell (10 pt): starts with verticalAlign>0 (baseline matches bl1). */
	public boolean check_bl2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bl2: " + y);
			assertEquals(1, pageNumber);
			assertEquals(7.7, y, 1);
			return true;
		}
		return false;
	}

	/** Last line of the baseline cell: widows=2 sends it to page 2. Continuation starts at y=0 (no realignment). */
	public boolean check_bla(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bla: " + y);
			assertEquals(2, pageNumber);
			assertEquals(9.5, y, 1);
			return true;
		}
		return false;
	}

	/** First line of the middle cell: verticalAlign=(measured row height - content height)/2≈15.1. */
	public boolean check_m1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("m1: " + y);
			assertEquals(1, pageNumber);
			assertEquals(15, y, 1);
			return true;
		}
		return false;
	}

	/** Last line of the middle cell: second line on page 2 (continuation starts at y=0, without realignment). */
	public boolean check_ma(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ma: " + y);
			assertEquals(2, pageNumber);
			assertEquals(9.5, y, 1);
			return true;
		}
		return false;
	}

	/**
	 * First line of the bottom cell: near the bottom of page 1.
	 *
	 * <p>
	 * Updated from 90.5 to 89.4 on 2026-07-27. Previously, verticalAlign≈90.9 was applied unchanged to the
	 * first fragment. Now {@code TableCellBox.split} reduces alignment space to keep the first indivisible
	 * unit in the preceding fragment, so the first line moves 0.94 pt toward the fragment's start.
	 * </p>
	 *
	 * <p>
	 * <b>No reading-order reversal occurs in this document</b> (the text was already on page 1).
	 * This position change is a side effect of reducing alignment space. It moves the content further
	 * inside its own frame, so it is not a regression.
	 * </p>
	 */
	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ba: " + y);
			assertEquals(1, pageNumber);
			assertEquals(89.4, y, 1);
			return true;
		}
		return false;
	}

	/** Second line of the bottom cell: start of page 2 (continuation starts at y=0, without realignment). */
	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bb: " + y);
			assertEquals(2, pageNumber);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	/**
	 * Line 11 of the rowspan cell: cellCutPageAxis conversion allows only 9 lines, not 10, on page 3.
	 * This is the second line on page 4.
	 */
	public boolean check_rsa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rsa: " + y);
			assertEquals(4, pageNumber);
			assertEquals(9.6, y, 1);
			return true;
		}
		return false;
	}

	/**
	 * First line of the bottom cell in row 2: no line fits before the split line,
	 * so all content moves to the start of page 4.
	 */
	public boolean check_rba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rba: " + y);
			assertEquals(4, pageNumber);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	/** Second line of the bottom cell in row 2: second line on page 4 (continuation starts at y=0). */
	public boolean check_rbb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rbb: " + y);
			assertEquals(4, pageNumber);
			assertEquals(9.6, y, 1);
			return true;
		}
		return false;
	}

	/** Line 8 of the top cell in row 2: 6 lines remain on page 3; line 7 onward moves to page 4 (starts at y=0). */
	public boolean check_rca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rca: " + y);
			assertEquals(4, pageNumber);
			assertEquals(9.6, y, 1);
			return true;
		}
		return false;
	}
}
