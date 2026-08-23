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
		assertEquals(JapaneseSpacingClass.MIDDLE_DOT, JapaneseSpacingClass.of('・'));
		assertEquals(JapaneseSpacingClass.MIDDLE_DOT, JapaneseSpacingClass.of('：'));
		assertEquals(JapaneseSpacingClass.MIDDLE_DOT, JapaneseSpacingClass.of('；'));
		assertEquals(JapaneseSpacingClass.OTHER, JapaneseSpacingClass.of('あ'));
		assertEquals(JapaneseSpacingClass.OTHER, JapaneseSpacingClass.of('A'));
		assertEquals(JapaneseSpacingClass.OTHER, JapaneseSpacingClass.of(0x20B9F)); // 補助面
	}

	public void testAllJlreqBracketClasses() {
		final String opening = "‘“（〔［｛〈《「『【⦅〘〖«〝";
		for (int i = 0; i < opening.length(); ++i) {
			assertEquals("opening U+" + Integer.toHexString(opening.charAt(i)), JapaneseSpacingClass.OPENING,
					JapaneseSpacingClass.of(opening.charAt(i)));
		}
		final String closing = "’”）〕］｝〉》」』】⦆〙〗»〟";
		for (int i = 0; i < closing.length(); ++i) {
			assertEquals("closing U+" + Integer.toHexString(closing.charAt(i)), JapaneseSpacingClass.CLOSING,
					JapaneseSpacingClass.of(closing.charAt(i)));
		}
	}

	/** 中点類の後ろはjustifyで伸ばさず、四分アキを固定する。 */
	public void testMiddleDotDoesNotExpandAfter() {
		assertFalse(JapaneseSpacingResolver.allowsJustificationAfter('・'));
		assertFalse(JapaneseSpacingResolver.allowsJustificationAfter('：'));
		assertFalse(JapaneseSpacingResolver.allowsJustificationAfter('；'));
		assertTrue(JapaneseSpacingResolver.allowsJustificationAfter('あ'));
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
	 * 句読点+開き/閉じ: 両方wideなら0.5。句読点+句読点: 詰めない。
	 */
	public void testPunctuationPairs() {
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('。', true, '「', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('。', true, '「', false), 0.001);
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('、', true, '」', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('、', true, '」', false), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('。', true, '。', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('。', false, '「', true), 0.001); // 前段がproportional
	}

	public void testCommaAndFullStopAreDistinguishedForJlreqReduction() {
		assertTrue(JapaneseSpacingResolver.isComma(0x3001));
		assertTrue(JapaneseSpacingResolver.isComma(0xFF0C));
		assertFalse(JapaneseSpacingResolver.isComma(0x3002));
		assertFalse(JapaneseSpacingResolver.isComma(0xFF0E));
	}

	/** 横書き・縦書き共通の天付き: 行頭の全角相当の始め括弧のみ-0.5em。 */
	public void testLineHeadIndent() {
		assertEquals(-0.5, JapaneseSpacingResolver.lineHeadIndent('「', true, true), 0.001);
		assertEquals(-0.5, JapaneseSpacingResolver.lineHeadIndent('『', true, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.lineHeadIndent('「', false, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.lineHeadIndent('」', true, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.lineHeadIndent('あ', true, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.lineHeadIndent('「', true, false), 0.001);
	}
}
