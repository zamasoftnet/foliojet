package net.zamasoft.foliojet.layout.text.spacing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.TextAutospaceValue;
import net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind;

/**
 * Pure calculation tests for {@link TextAutospaceClasses} (Japanese spacing trim A2).
 */
public class TextAutospaceClassesTest extends TestCase {

	private static final byte BOTH = (byte) (TextAutospaceValue.ALPHA | TextAutospaceValue.NUMERIC);

	public void testClassification() {
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of('漢'));
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of('あ'));
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of('ア'));
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of('々'));
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of(0x20B9F)); // 𠮟 (supplementary plane).
		assertEquals(Kind.ALPHA, TextAutospaceClasses.of('A'));
		assertEquals(Kind.ALPHA, TextAutospaceClasses.of('z'));
		assertEquals(Kind.ALPHA, TextAutospaceClasses.of('é'));
		assertEquals(Kind.NUMERIC, TextAutospaceClasses.of('7'));
		assertEquals(Kind.OTHER, TextAutospaceClasses.of('Ａ')); // Full-width Latin letters are ineligible.
		assertEquals(Kind.PUNCTUATION, TextAutospaceClasses.of('。')); // Commas and periods are eligible only when proportionally spaced (2026-09-14).
		assertEquals(Kind.OTHER, TextAutospaceClasses.of('（'));
		assertEquals(Kind.OTHER, TextAutospaceClasses.of(' '));
	}

	/** All four directions: 漢A, A漢, 漢1, and 1漢. Selection by flags. */
	public void testGap() {
		assertEquals(0.25, TextAutospaceClasses.gapEm('漢', 'A', BOTH), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('A', '漢', BOTH), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('漢', '1', BOTH), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('1', '漢', BOTH), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('あ', 'A', BOTH), 0.0001);

		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', 'A', TextAutospaceValue.NUMERIC), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', '1', TextAutospaceValue.ALPHA), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('漢', '1', TextAutospaceValue.NUMERIC), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', 'A', (byte) 0), 0.0001);
	}

	/**
	 * Ineligible pairs: Japanese/Japanese, Latin/Latin, punctuation/space boundaries, and A1 (Latin letter and
	 * digit).
	 */
	public void testNoGap() {
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', 'あ', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('A', 'B', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('A', '1', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', '。', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('。', 'A', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', 'Ａ', BOTH), 0.0001);
	}

	public void testIdeographFirst() {
		assertTrue(TextAutospaceClasses.ideographFirst('漢'));
		assertFalse(TextAutospaceClasses.ideographFirst('A'));
	}
}
