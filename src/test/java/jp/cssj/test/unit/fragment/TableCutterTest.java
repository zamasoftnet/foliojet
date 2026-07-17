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

	public void testCellFragmentStateHorizontal() {
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = new net.zamasoft.foliojet.layout.part.AbsoluteRectFrame(
				net.zamasoft.foliojet.layout.box.params.RectFrame.NULL_FRAME);
		final net.zamasoft.foliojet.layout.box.params.Dimension size = net.zamasoft.foliojet.layout.box.params.Dimension
				.create(50, 80, net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE,
						net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE);
		final net.zamasoft.foliojet.layout.box.params.Dimension auto = net.zamasoft.foliojet.layout.box.params.Dimension
				.create(0, 0, net.zamasoft.foliojet.layout.box.params.LengthType.AUTO,
						net.zamasoft.foliojet.layout.box.params.LengthType.AUTO);
		final net.zamasoft.foliojet.layout.fragment.TableCutter.CellFragmentState state = net.zamasoft.foliojet.layout.fragment.TableCutter
				.cellFragmentState(false, size, auto, frame, 100, 30);
		// 指定高さのページ方向は残量(100-30)の絶対値に分割される
		assertEquals(70, state.nextSize().getHeight(), 0.01);
		assertEquals(net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE, state.nextSize().getHeightType());
		assertEquals(50, state.nextSize().getWidth(), 0.01);
		// AUTO の最小寸法はそのまま
		assertSame(auto, state.nextMinSize());
		// 残量は負にならない
		assertEquals(0, net.zamasoft.foliojet.layout.fragment.TableCutter
				.cellFragmentState(false, size, auto, frame, 100, 120).nextSize().getHeight(), 0.01);
	}

	public void testTableFragmentFrames() {
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = new net.zamasoft.foliojet.layout.part.AbsoluteRectFrame(
				net.zamasoft.foliojet.layout.box.params.RectFrame.NULL_FRAME);
		// ヘッダ・フッタが繰り返されるならフレームは切らない(同一参照)
		final net.zamasoft.foliojet.layout.fragment.TableCutter.TableFragmentFrames repeat = net.zamasoft.foliojet.layout.fragment.TableCutter
				.tableFragmentFrames(false, true, true, frame);
		assertSame(frame, repeat.nextFrame());
		assertSame(frame, repeat.prevFrame());
	}

	public void testRowPreDecideNotPageFirst() {
		final double[] extents = { 30 };
		final boolean[] match = { true };
		final boolean[] noAvoid = { false };
		final boolean[] noCollapse = { false };
		// 切断線より下 → 全体移動
		assertSame(SplitResult.MOVE,
				TableCutter.rowPreDecide(false, false, -1, 30, false, extents, match, noAvoid, noCollapse));
		// 切断線より上(連結セルも収まる)→ 残す
		assertSame(SplitResult.KEEP,
				TableCutter.rowPreDecide(false, false, 40, 30, false, extents, match, noAvoid, noCollapse));
		// 連結セルがはみ出す → 主処理へ
		assertNull(TableCutter.rowPreDecide(false, false, 40, 30, false, new double[] { 50 }, match, noAvoid,
				noCollapse));
		// 行の avoid-inside(先頭行でない)→ 移動
		assertSame(SplitResult.MOVE,
				TableCutter.rowPreDecide(false, false, 10, 30, true, extents, match, noAvoid, noCollapse));
		// 先頭行なら avoid を無視して主処理へ
		assertNull(TableCutter.rowPreDecide(false, true, 10, 30, true, extents, match, noAvoid, noCollapse));
		// 書字方向が違うセル → 移動
		assertSame(SplitResult.MOVE, TableCutter.rowPreDecide(false, false, 10, 30, false, extents,
				new boolean[] { false }, noAvoid, noCollapse));
	}

	public void testRowPreDecidePageFirst() {
		final double[] extents = { 30 };
		final boolean[] match = { true };
		final boolean[] noAvoid = { false };
		// 収まるなら残す
		assertSame(SplitResult.KEEP,
				TableCutter.rowPreDecide(true, true, 40, 30, false, extents, match, noAvoid, new boolean[] { false }));
		// 書字方向が違えば残す(移動しない)
		assertSame(SplitResult.KEEP, TableCutter.rowPreDecide(true, true, 10, 30, false, extents,
				new boolean[] { false }, noAvoid, new boolean[] { false }));
		// 上部境界なし・高さゼロのセルがあれば分割を諦める
		assertSame(SplitResult.KEEP,
				TableCutter.rowPreDecide(true, true, 10, 30, false, extents, match, noAvoid, new boolean[] { true }));
		// それ以外は主処理(セル分割)へ
		assertNull(TableCutter.rowPreDecide(true, true, 10, 30, false, extents, match, noAvoid,
				new boolean[] { false }));
	}

	public void testFirstForceBreak() {
		final PageBreakMode a = PageBreakMode.AUTO, p = PageBreakMode.PAGE;
		// 2グループ×2行、行寸10。切断線は十分下
		final double[][] sizes = { { 10, 10 }, { 10, 10 } };
		final PageBreakMode[] ga = { a, a };
		final PageBreakMode[][] ra = { { a, a }, { a, a } };
		// 強制改ページなし → null
		assertNull(TableCutter.firstForceBreak(1000, 0, sizes, ga, ga, ra, ra));
		// グループ2の直前 → 直前グループ(0)の末尾で切る
		TableCutter.ForceBreakAt at = TableCutter.firstForceBreak(1000, 0, sizes,
				new PageBreakMode[] { a, p }, ga, ra, ra);
		assertEquals(0, at.rowGroup());
		assertEquals(-1, at.row());
		assertSame(p, at.breakMode());
		// グループ1の直後 → そのグループの末尾で切る
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, new PageBreakMode[] { p, a }, ra, ra);
		assertEquals(0, at.rowGroup());
		assertEquals(-1, at.row());
		// 行2の直前 → 行1の直後で切る
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, ga,
				new PageBreakMode[][] { { a, p }, { a, a } }, ra);
		assertEquals(0, at.rowGroup());
		assertEquals(0, at.row());
		// グループ2先頭行の直前 → グループ境界(前グループ末尾)で切る
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, ga,
				new PageBreakMode[][] { { a, a }, { p, a } }, ra);
		assertEquals(0, at.rowGroup());
		assertEquals(-1, at.row());
		// 行1の直後(グループ内に後続行あり)→ その行で切る
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, ga, ra,
				new PageBreakMode[][] { { p, a }, { a, a } });
		assertEquals(0, at.rowGroup());
		assertEquals(0, at.row());
		// グループ末尾行の直後 → グループ末尾で切る
		at = TableCutter.firstForceBreak(1000, 0, sizes, ga, ga, ra,
				new PageBreakMode[][] { { a, p }, { a, a } });
		assertEquals(0, at.rowGroup());
		assertEquals(-1, at.row());
		// 切断線を越えた行以降の指定は無視(自動改ページの領分)
		assertNull(TableCutter.firstForceBreak(15, 0, sizes, ga, ga,
				new PageBreakMode[][] { { a, a }, { p, a } }, ra));
		// 表末尾の直後指定は改ページにならない
		assertNull(TableCutter.firstForceBreak(1000, 0, sizes, ga, ga, ra,
				new PageBreakMode[][] { { a, a }, { a, p } }));
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
