package jp.cssj.test.unit._0219_pagebreak_table_inrow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * 行内(セル内)分割とvertical-alignの物理座標検査(縦書きtb-rl)。
 * ページ軸は水平(右から左)で、ページ幅100pt・1行の幅10pt。
 * ValignSplitTest(横書き)のミラー。継続セルはverticalAlign=0
 * (=ページ右端x=90が行先頭)から始まり、再アラインされない。
 * 表示リスト全体はDisplayListGoldenTestのgolden
 * (files/unittest/display-list-golden/0219-pagebreak-table-inrow_valign-split-vert)
 * で固定されている。
 *
 * <p>
 * 横書きとの既知の非対称(現状動作の記録): 表B(rowspanあり)では
 * rowspanセルがcellCutPageAxis換算で3ページ目に10行保持される一方、
 * 2行目の非rowspanセルは横書きと違い3ページ目に一切内容を残さず、
 * 2行目の内容全部が4ページ目へ送られる。
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

	/** topセルの1行目: 1ページ目の行先頭(右端x=90)。 */
	public boolean check_ta(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ta: " + x);
			assertEquals(1, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/** 行の水平寸法を決める第1セルの11行目: 2ページ目の先頭(継続はx=90開始)。 */
	public boolean check_drva(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("drva: " + x);
			assertEquals(2, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/** baselineセル(20pt)の1行目: rowAscentを決める側(x=80..100)。 */
	public boolean check_bl1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bl1: " + x);
			assertEquals(1, pageNumber);
			assertEquals(80, x, 1);
			return true;
		}
		return false;
	}

	/** baselineセル(10pt)の1行目: verticalAlign=5で開始(bl1と中心線一致)。 */
	public boolean check_bl2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bl2: " + x);
			assertEquals(1, pageNumber);
			assertEquals(85, x, 1);
			return true;
		}
		return false;
	}

	/** baselineセルの最終行: widows=2で2ページ目へ。継続の2行目(x=80)。 */
	public boolean check_bla(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bla: " + x);
			assertEquals(2, pageNumber);
			assertEquals(80, x, 1);
			return true;
		}
		return false;
	}

	/** middleセルの1行目: verticalAlign=(120-90)/2=15(x=75)。 */
	public boolean check_m1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("m1: " + x);
			assertEquals(1, pageNumber);
			assertEquals(75, x, 1);
			return true;
		}
		return false;
	}

	/** middleセルの最終行: 2ページ目の2行目(継続は右端開始、再アラインなし)。 */
	public boolean check_ma(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ma: " + x);
			assertEquals(2, pageNumber);
			assertEquals(80, x, 1);
			return true;
		}
		return false;
	}

	/** bottomセルの1行目: verticalAlign=90でページ左端にちょうど収まる(x=0)。 */
	public boolean check_ba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ba: " + x);
			assertEquals(1, pageNumber);
			assertEquals(0, x, 1);
			return true;
		}
		return false;
	}

	/** bottomセルの2行目: 2ページ目先頭(継続はx=90開始、再アラインなし)。 */
	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bb: " + x);
			assertEquals(2, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/** rowspanセルの11行目: cellCutPageAxis換算で3ページ目にちょうど10行(100pt)、11行目は4ページ目先頭(x=90)。 */
	public boolean check_rsa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rsa: " + x);
			assertEquals(4, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/** 2行目bottomセルの1行目: 2行目全体が4ページ目へ。継続はx=90開始(再アラインなし)。 */
	public boolean check_rba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rba: " + x);
			assertEquals(4, pageNumber);
			assertEquals(90, x, 1);
			return true;
		}
		return false;
	}

	/** 2行目bottomセルの2行目: 4ページ目の2行目(x=80)。 */
	public boolean check_rbb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rbb: " + x);
			assertEquals(4, pageNumber);
			assertEquals(80, x, 1);
			return true;
		}
		return false;
	}

	/** 2行目topセルの8行目: 2行目全体が4ページ目へ送られるため4ページ目(x=20)。 */
	public boolean check_rca(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rca: " + x);
			assertEquals(4, pageNumber);
			assertEquals(20, x, 1);
			return true;
		}
		return false;
	}
}
