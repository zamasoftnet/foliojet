package net.zamasoft.foliojet.layout.text.spacing;

import junit.framework.TestCase;

/**
 * {@link JapaneseSpacingResolver}(和文詰めS0)の純粋計算テストです。
 * 移管元(OpenTypeFont.getKerning・TextBuilder天付き)のpair表を
 * そのまま固定する——S1の出力不変移管の基準。
 */
public class JapaneseSpacingResolverTest extends TestCase {

	public void testClassification() {
		assertEquals(JapaneseSpacingClass.OPENING, JapaneseSpacingClass.of('「'));
		assertEquals(JapaneseSpacingClass.OPENING, JapaneseSpacingClass.of('（'));
		assertEquals(JapaneseSpacingClass.CLOSING, JapaneseSpacingClass.of('」'));
		assertEquals(JapaneseSpacingClass.CLOSING, JapaneseSpacingClass.of('）'));
		assertEquals(JapaneseSpacingClass.PUNCTUATION, JapaneseSpacingClass.of('。'));
		assertEquals(JapaneseSpacingClass.PUNCTUATION, JapaneseSpacingClass.of('、'));
		assertEquals(JapaneseSpacingClass.PUNCTUATION, JapaneseSpacingClass.of('，'));
		assertEquals(JapaneseSpacingClass.PUNCTUATION, JapaneseSpacingClass.of('．'));
		assertEquals(JapaneseSpacingClass.OTHER, JapaneseSpacingClass.of('あ'));
		assertEquals(JapaneseSpacingClass.OTHER, JapaneseSpacingClass.of('A'));
		assertEquals(JapaneseSpacingClass.OTHER, JapaneseSpacingClass.of(0x20B9F)); // 補助面
	}

	/** 開き+開き: 両方wideで0.5、どちらかproportionalなら0。 */
	public void testOpeningPairs() {
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('「', true, '（', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('「', true, '（', false), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('「', false, '（', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('「', true, '」', true), 0.001); // 開き+閉じは詰めない
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('「', true, 'あ', true), 0.001);
	}

	/** 閉じ+{開き|閉じ|句読点}: 両方wideで0.5。 */
	public void testClosingPairs() {
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('」', true, '「', true), 0.001);
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('」', true, '）', true), 0.001);
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('」', true, '。', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('」', true, '。', false), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('」', true, 'あ', true), 0.001);
	}

	/**
	 * 句読点+開き: 後続のwide判定なしで0.5(移管元の演算子優先順位の
	 * 癖の保存)。句読点+閉じ: wide判定あり。句読点+句読点: 詰めない。
	 */
	public void testPunctuationPairs() {
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('。', true, '「', true), 0.001);
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('。', true, '「', false), 0.001); // 癖
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('、', true, '」', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('、', true, '」', false), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('。', true, '。', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('。', false, '「', true), 0.001); // 前段がproportional
	}

	/** 縦書き天付き: 行頭の始め括弧のみ-0.5em。 */
	public void testVerticalHeadIndent() {
		assertEquals(-0.5, JapaneseSpacingResolver.verticalHeadIndent('「'), 0.001);
		assertEquals(-0.5, JapaneseSpacingResolver.verticalHeadIndent('『'), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.verticalHeadIndent('」'), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.verticalHeadIndent('あ'), 0.001);
	}
}
