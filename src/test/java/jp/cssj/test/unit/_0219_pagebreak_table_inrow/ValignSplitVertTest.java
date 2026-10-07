package jp.cssj.test.unit._0219_pagebreak_table_inrow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Checks physical coordinates for intra-row (intra-cell) splitting and vertical-align (tb-rl vertical writing).
 * The page axis is horizontal (right to left), with a page width of 100 pt and a line width of 10 pt.
 * Mirrors ValignSplitTest (horizontal writing). Continuation cells start at verticalAlign=0
 * (the line starts at the page's right edge, x=90), without realignment.
 * The complete display list is fixed by the DisplayListGoldenTest golden
 * (files/unittest/display-list-golden/0219-pagebreak-table-inrow_valign-split-vert).
 *
 * <p>
 * Known asymmetry with horizontal writing (record of current behavior): in table B (with rowspan),
 * cellCutPageAxis conversion keeps 10 lines of the rowspan cell on page 3. Unlike horizontal writing,
 * the non-rowspan cells in row 2 leave no content on page 3, and all content of row 2 moves to page 4.
 * </p>
 */
public class ValignSplitVertTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0219-pagebreak-table-inrow/valign-split-vert.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public ValignSplitVertTest(String name) {
		super(name);
	}

	/** First line of the top cell: row start on page 1 (right edge, x=90). */
	public boolean check_ta(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ta: " + x);
			assertEquals(1, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/** Line 11 of the first cell, which determines the row's horizontal size: start of page 2 (starts at x=90). */
	public boolean check_drva(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("drva: " + x);
			assertEquals(2, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/** First line of the baseline cell (20 pt): determines rowAscent (x=80..100). */
	public boolean check_bl1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bl1: " + x);
			assertEquals(1, pageNumber);
			assertEquals(80, x, 1);
			return true;
		}
		return false;
	}

	/** First line of the baseline cell (10 pt): starts at verticalAlign=5 (centerline matches bl1). */
	public boolean check_bl2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bl2: " + x);
			assertEquals(1, pageNumber);
			assertEquals(85, x, 1);
			return true;
		}
		return false;
	}

	/** Last line of the baseline cell: widows=2 sends it to page 2. Second continuation line (x=80). */
	public boolean check_bla(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bla: " + x);
			assertEquals(2, pageNumber);
			assertEquals(80, x, 1);
			return true;
		}
		return false;
	}

	/** First line of the middle cell: verticalAlign=(120-90)/2=15 (x=75). */
	public boolean check_m1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("m1: " + x);
			assertEquals(1, pageNumber);
			assertEquals(75, x, 1);
			return true;
		}
		return false;
	}

	/** Last line of the middle cell: second line on page 2 (continuation starts at the right edge, no realignment). */
	public boolean check_ma(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ma: " + x);
			assertEquals(2, pageNumber);
			assertEquals(80, x, 1);
			return true;
		}
		return false;
	}

	/** First line of the bottom cell: verticalAlign=90 fits exactly at the page's left edge (x=0). */
	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ba: " + x);
			assertEquals(1, pageNumber);
			assertEquals(0, x, 1);
			return true;
		}
		return false;
	}

	/** Second line of the bottom cell: start of page 2 (continuation starts at x=90, without realignment). */
	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bb: " + x);
			assertEquals(2, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/**
	 * Line 11 of the rowspan cell: cellCutPageAxis conversion fits exactly 10 lines (100 pt) on page 3.
	 * Line 11 starts page 4 (x=90).
	 */
	public boolean check_rsa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rsa: " + x);
			assertEquals(4, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/**
	 * First line of the bottom cell in row 2: all of row 2 moves to page 4.
	 * Continuation starts at x=90 (no realignment).
	 */
	public boolean check_rba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rba: " + x);
			assertEquals(4, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/** Second line of the bottom cell in row 2: second line on page 4 (x=80). */
	public boolean check_rbb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rbb: " + x);
			assertEquals(4, pageNumber);
			assertEquals(80, x, 1);
			return true;
		}
		return false;
	}

	/**
	 * Line 8 of the top cell in row 2: on 2026-09-04, an axis mix-up for vertical writing in the rowspan
	 * path was fixed. As in horizontal writing, the lines that fit (タ〜ニ) now remain on page 3,
	 * so line 8 (ヌ) starts the continuation on page 4 (x=90).
	 */
	public boolean check_rca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rca: " + x);
			assertEquals(4, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}
}
