package net.zamasoft.foliojet.layout.text.spacing;

import junit.framework.TestCase;

/**
 * Pure calculation tests for {@link JapaneseSpacingResolver} (Japanese spacing trim S0).
 * Locks down the pair table from the original implementations (OpenTypeFont.getKerning and
 * TextBuilder line-start trimming) as-is, as the baseline for S1's migration with unchanged output.
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
		assertEquals(JapaneseSpacingClass.OTHER, JapaneseSpacingClass.of(0x20B9F)); // Supplementary plane.
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

	/** After middle-dot characters, keep quarter-em spacing fixed rather than expanding it for justification. */
	public void testMiddleDotDoesNotExpandAfter() {
		assertFalse(JapaneseSpacingResolver.allowsJustificationAfter('・'));
		assertFalse(JapaneseSpacingResolver.allowsJustificationAfter('：'));
		assertFalse(JapaneseSpacingResolver.allowsJustificationAfter('；'));
		assertTrue(JapaneseSpacingResolver.allowsJustificationAfter('あ'));
	}

	/** Opening+opening: 0.5 if both are wide, 0 if either is proportional. */
	public void testOpeningPairs() {
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('「', true, '（', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('「', true, '（', false), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('「', false, '（', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('「', true, '」', true), 0.001); // Do not trim opening+closing.
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('「', true, 'あ', true), 0.001);
	}

	/** Closing+{opening|closing|comma or period}: 0.5 if both are wide. */
	public void testClosingPairs() {
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('」', true, '「', true), 0.001);
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('」', true, '）', true), 0.001);
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('」', true, '。', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('」', true, '。', false), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('」', true, 'あ', true), 0.001);
	}

	/**
	 * Comma or period + opening/closing: 0.5 if both are wide. Comma or period + comma or period: no trim.
	 */
	public void testPunctuationPairs() {
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('。', true, '「', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('。', true, '「', false), 0.001);
		assertEquals(0.5, JapaneseSpacingResolver.pairTrim('、', true, '」', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('、', true, '」', false), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('。', true, '。', true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.pairTrim('。', false, '「', true), 0.001); // The preceding character is proportional.
	}

	public void testCommaAndFullStopAreDistinguishedForJlreqReduction() {
		assertTrue(JapaneseSpacingResolver.isComma(0x3001));
		assertTrue(JapaneseSpacingResolver.isComma(0xFF0C));
		assertFalse(JapaneseSpacingResolver.isComma(0x3002));
		assertFalse(JapaneseSpacingResolver.isComma(0xFF0E));
	}

	/**
	 * Line-start trimming for horizontal/vertical writing: -0.5em only for a full-width opening bracket at line
	 * start.
	 */
	public void testLineHeadIndent() {
		assertEquals(-0.5, JapaneseSpacingResolver.lineHeadIndent('「', true, true), 0.001);
		assertEquals(-0.5, JapaneseSpacingResolver.lineHeadIndent('『', true, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.lineHeadIndent('「', false, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.lineHeadIndent('」', true, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.lineHeadIndent('あ', true, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.lineHeadIndent('「', true, false), 0.001);
	}
}
