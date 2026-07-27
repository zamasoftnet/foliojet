package jp.cssj.test.unit._0219_pagebreak_table_inrow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * 行内(セル内)分割とvertical-alignの物理座標検査(横書き)。
 * A-3bのアラインメント物理契約(docs/history/2026-07-23-a3b-goal-narrowed.md)
 * の保護材:
 * <ul>
 * <li>行内の全セルは同一の物理分割線(ページ下端)で切られる</li>
 * <li>middle/bottom/baselineのverticalAlignは実測差から計算される</li>
 * <li>継続セルはverticalAlign=0(ページ上端)から始まり、再アラインされない</li>
 * <li>rowspanセルはcellCutPageAxisでセル始端基準に換算されて切断される</li>
 * </ul>
 * 継続内容が2ページ目以降で検査されること自体が、fixtureが実際に
 * 行内分割を踏んでいる(1ページに収まっていない)ことの検証になっている。
 * 表示リスト全体はDisplayListGoldenTestのgolden
 * (files/unittest/display-list-golden/0219-pagebreak-table-inrow_valign-split)
 * で固定されている。
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

	/** topセルの1行目: 1ページ目の行先頭(y=0)。 */
	public boolean check_ta(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("ta: " + y);
			assertEquals(1, pageNumber);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	/** 行高を決める第1セルの11行目: 2ページ目の2行目(継続はy=0開始)。 */
	public boolean check_drva(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("drva: " + y);
			assertEquals(2, pageNumber);
			assertEquals(9.6, y, 1);
			return true;
		}
		return false;
	}

	/** baselineセル(20pt)の1行目: rowAscentを決める側。1ページ目の行先頭付近。 */
	public boolean check_bl1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bl1: " + y);
			assertEquals(1, pageNumber);
			assertEquals(-0.6, y, 1);
			return true;
		}
		return false;
	}

	/** baselineセル(10pt)の1行目: verticalAlign>0で開始(bl1とベースライン一致)。 */
	public boolean check_bl2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bl2: " + y);
			assertEquals(1, pageNumber);
			assertEquals(7.7, y, 1);
			return true;
		}
		return false;
	}

	/** baselineセルの最終行: widows=2で2ページ目へ。継続はy=0開始(再アラインなし)。 */
	public boolean check_bla(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bla: " + y);
			assertEquals(2, pageNumber);
			assertEquals(9.5, y, 1);
			return true;
		}
		return false;
	}

	/** middleセルの1行目: verticalAlign=(実測行高-内容高)/2≈15.1。 */
	public boolean check_m1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("m1: " + y);
			assertEquals(1, pageNumber);
			assertEquals(15, y, 1);
			return true;
		}
		return false;
	}

	/** middleセルの最終行: 2ページ目の2行目(継続はy=0開始、再アラインなし)。 */
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
	 * bottomセルの1行目: 1ページ目の下端付近。
	 *
	 * <p>
	 * 2026-07-27に90.5→89.4へ更新した。従来はverticalAlign≈90.9をそのまま
	 * 先頭断片へ載せていたが、現在は{@code TableCellBox.split}が
	 * 「先頭の不可分単位が前断片に残る範囲」まで整列余白を詰めるので、
	 * 1行目が0.94ptだけ断片の始端側へ寄る。
	 * </p>
	 *
	 * <p>
	 * <b>この文書では読み順の逆転は起きていない</b>(文字は従来から
	 * 1ページ目にある)。整列余白の詰めが波及した副次的な位置変化であり、
	 * 方向は「自分の枠の内側へ寄る」ため退行ではない。
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

	/** bottomセルの2行目: 2ページ目先頭(継続はy=0開始、再アラインなし)。 */
	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("bb: " + y);
			assertEquals(2, pageNumber);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	/** rowspanセルの11行目: cellCutPageAxis換算で3ページ目に10行入らず9行。4ページ目の2行目。 */
	public boolean check_rsa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rsa: " + y);
			assertEquals(4, pageNumber);
			assertEquals(9.6, y, 1);
			return true;
		}
		return false;
	}

	/** 2行目bottomセルの1行目: 分割線内に1行も収まらず全内容が4ページ目先頭へ。 */
	public boolean check_rba(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rba: " + y);
			assertEquals(4, pageNumber);
			assertEquals(0, y, 1);
			return true;
		}
		return false;
	}

	/** 2行目bottomセルの2行目: 4ページ目の2行目(継続はy=0開始)。 */
	public boolean check_rbb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.out.println("rbb: " + y);
			assertEquals(4, pageNumber);
			assertEquals(9.6, y, 1);
			return true;
		}
		return false;
	}

	/** 2行目topセルの8行目: 3ページ目に6行残り、7行目以降は4ページ目(y=0開始)。 */
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
