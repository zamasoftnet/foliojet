package jp.cssj.test.unit.builder;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.builder.impl.TextEventJournal;

/**
 * 境界イベント journal(M3b Phase 0)のテストです。deliveredCharEnd
 * (グリフのみで前進)と正規化イベントカーソル(control 込み)の乖離 —
 * open 段落の接合キーに前者が使えない理由 — を型のレベルで固定します。
 */
public class TextEventJournalTest extends TestCase {
	public void testCursorAdvancesOnTrailingControls() {
		final TextEventJournal j = new TextEventJournal();
		j.run(0);
		j.glyph(0, 3); // 「abc」
		// deliveredCharEnd 相当はここで 3
		j.control(3); // 末尾の空白
		j.control(4); // 改行
		// 正規化イベントの配達境界は control を含めて 5 まで進む
		assertEquals(5, j.cursor());
		assertEquals(4, j.seq());
	}

	public void testNegativeOffsetsDoNotAdvance() {
		final TextEventJournal j = new TextEventJournal();
		j.glyph(-1, 2); // 生成グリフ(ソース位置なし)
		j.control(-1); // 生成 control
		assertEquals(0, j.cursor());
		assertEquals(2, j.seq());
	}

	public void testSeqCountsEveryDeliveredEvent() {
		final TextEventJournal j = new TextEventJournal();
		j.run(0);
		j.glyph(0, 1);
		j.inline();
		j.glyph(1, 2);
		j.flush();
		assertEquals(5, j.seq());
		assertEquals(2, j.cursor());
	}
}
