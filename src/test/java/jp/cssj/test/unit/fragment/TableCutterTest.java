package jp.cssj.test.unit.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.fragment.TableCutter;

/**
 * 表切断判定(C4-T1)のテストです。TableBox / TableRowGroupBox の
 * 切断ループから純化された判定を、ボックス木なしで固定します。
 */
public class TableCutterTest extends TestCase {
	public void testKeepOrMoveAll() {
		assertSame(SplitResult.KEEP, TableCutter.keepOrMoveAll(IPageBreakableBox.FLAGS_FIRST));
		assertSame(SplitResult.MOVE, TableCutter.keepOrMoveAll((byte) 0));
	}

	public void testReserveNonBreakable() {
		// ヘッダ・フッタなし、境界が下マージンに届かない: フレーム始端のみ
		assertEquals(90, TableCutter.reserveNonBreakable(100, 120, 10, 5, 8, -1, -1), 0.01);
		// ヘッダあり: その分を予約
		assertEquals(70, TableCutter.reserveNonBreakable(100, 120, 10, 5, 8, 20, -1), 0.01);
		// フッタあり: フッタ+終端フレームを予約(マージンは見ない)
		assertEquals(65, TableCutter.reserveNonBreakable(100, 120, 10, 5, 8, -1, 20), 0.01);
		// フッタなしで境界が下マージン内(over=5 < margin=8): マージンを切る
		assertEquals(87, TableCutter.reserveNonBreakable(100, 105, 5, 5, 8, -1, -1), 0.01);
	}

	public void testGroupBreakAvoid() {
		final PageBreakMode a = PageBreakMode.AUTO, v = PageBreakMode.AVOID;
		assertFalse(TableCutter.groupBreakAvoid(a, a, a, a));
		assertTrue(TableCutter.groupBreakAvoid(v, a, a, a));
		assertTrue(TableCutter.groupBreakAvoid(a, v, a, a));
		// 境界に接する行の avoid も禁止を立てる
		assertTrue(TableCutter.groupBreakAvoid(a, a, v, a));
		assertTrue(TableCutter.groupBreakAvoid(a, a, a, v));
	}

	public void testRowBreakAvoidByPosition() {
		final PageBreakMode a = PageBreakMode.AUTO, v = PageBreakMode.AVOID;
		final boolean[] none = {};
		assertFalse(TableCutter.rowBreakAvoid(2, false, a, a, none, none, none));
		assertTrue(TableCutter.rowBreakAvoid(2, false, v, a, none, none, none));
		assertTrue(TableCutter.rowBreakAvoid(2, false, a, v, none, none, none));
	}

	public void testRowBreakAvoidByExtendedCell() {
		final PageBreakMode a = PageBreakMode.AUTO;
		// 切断可能なセルの連結は禁止を立てない
		assertFalse(TableCutter.rowBreakAvoid(2, false, a, a, new boolean[] { true }, new boolean[] { true },
				new boolean[] { true }));
		// 切断不能(avoid-inside 等)なセルの連結は禁止
		assertTrue(TableCutter.rowBreakAvoid(2, false, a, a, new boolean[] { false }, new boolean[] { true },
				new boolean[] { true }));
		// 連結が伸びていなければ禁止しない
		assertFalse(TableCutter.rowBreakAvoid(2, false, a, a, new boolean[] { false }, new boolean[] { false },
				new boolean[] { true }));
	}

	public void testRowBreakAvoidFirstRowsSpecialCase() {
		final PageBreakMode a = PageBreakMode.AUTO, v = PageBreakMode.AVOID;
		// ページ先頭の1-2行目: 書字方向が一致する連結は禁止を解除する
		// (行位置の avoid があっても上書きされる — 旧実装の挙動)
		assertFalse(TableCutter.rowBreakAvoid(1, true, v, a, new boolean[] { true }, new boolean[] { true },
				new boolean[] { true }));
		// 書字方向が違う連結は必ず禁止
		assertTrue(TableCutter.rowBreakAvoid(1, true, a, a, new boolean[] { false }, new boolean[] { true },
				new boolean[] { false }));
		// 連結がなければ行位置の avoid がそのまま残る
		assertTrue(TableCutter.rowBreakAvoid(1, true, v, a, new boolean[] { true }, new boolean[] { false },
				new boolean[] { true }));
	}

	public void testMixedFlowKeep() {
		assertFalse(TableCutter.mixedFlowKeep(new boolean[] { true, true }));
		assertTrue(TableCutter.mixedFlowKeep(new boolean[] { true, false }));
		assertFalse(TableCutter.mixedFlowKeep(new boolean[] {}));
	}

	public void testFirstRowFlags() {
		final byte first = IPageBreakableBox.FLAGS_FIRST;
		// ページ先頭でなければ変更なし
		assertEquals(0, TableCutter.firstRowFlags((byte) 0, 2, true));
		// 先頭行: FLAGS_FIRST_ROW を立てる
		assertEquals((byte) (first | IPageBreakableBox.FLAGS_FIRST_ROW),
				TableCutter.firstRowFlags(first, 0, false));
		// 2行目以降: FLAGS_FIRST を落とす。連結していれば FIRST_ROW
		assertEquals(IPageBreakableBox.FLAGS_FIRST_ROW, TableCutter.firstRowFlags(first, 1, true));
		assertEquals(0, TableCutter.firstRowFlags(first, 1, false));
	}
}
