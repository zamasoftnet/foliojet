package net.zamasoft.foliojet.css.style;

import junit.framework.TestCase;

/**
 * Segment(スタイルイベント窓)の刈り込みのテストです(M6a)。
 * スタイル参照は窓機構に無関係のため null で代用します。
 */
public class SegmentTest extends TestCase {
	public void testTrimKeepsOpenElements() {
		final Segment segment = new Segment();
		segment.startStyle(null); // html
		segment.startStyle(null); // body
		segment.startStyle(null); // p (閉じる)
		segment.characters(0, "hello".toCharArray(), 0, 5);
		segment.endStyle(null); // /p
		segment.startStyle(null); // div (開いたまま)
		segment.characters(5, "world".toCharArray(), 0, 5);
		assertEquals(7, segment.size());
		assertEquals(3, segment.getDepth());

		segment.trimToOpenElements();
		// html, body, div の Start だけが残る
		assertEquals(3, segment.size());
		assertEquals(3, segment.getDepth());
	}

	public void testTrimAcrossWindows() {
		final Segment segment = new Segment();
		segment.startStyle(null); // body
		segment.startStyle(null); // div
		segment.trimToOpenElements();
		assertEquals(2, segment.size());

		// 前の窓で開いた要素を次の窓で閉じる
		segment.endStyle(null); // /div
		segment.characters(0, "x".toCharArray(), 0, 1);
		segment.trimToOpenElements();
		assertEquals(1, segment.size());
		assertEquals(1, segment.getDepth());
	}

	public void testTrimEmpty() {
		final Segment segment = new Segment();
		segment.trimToOpenElements();
		assertEquals(0, segment.size());
		assertEquals(0, segment.getDepth());
	}
}
