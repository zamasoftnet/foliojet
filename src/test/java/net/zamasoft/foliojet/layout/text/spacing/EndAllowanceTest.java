package net.zamasoft.foliojet.layout.text.spacing;

import junit.framework.TestCase;

/**
 * Deterministic tests for {@link JapaneseSpacingResolver#endAllowance} (Japanese spacing trim T2/H1).
 * Corpus fonts have half-width punctuation advances (correctly excluded by the wide gate), so whether
 * this fires in real documents depends on the environment; these tests are the authoritative verification.
 */
public class EndAllowanceTest extends TestCase {

	/** Line-end trim (T2): if overflow≤0.5em, fit the character by reducing it to half-width. */
	public void testEndTrim() {
		// Closing bracket, overflow 3 pt≤6 pt → trim 6 pt.
		assertEquals(6.0, JapaneseSpacingResolver.endAllowance('」', true, false, false, 12, 12, 3), 0.001);
		// Commas and periods are also eligible.
		assertEquals(6.0, JapaneseSpacingResolver.endAllowance('。', true, false, false, 12, 12, 6), 0.001);
		// Overflow>0.5em cannot trim (if hanging is also disabled, 0 = push to the next line).
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('」', true, false, false, 12, 12, 7), 0.001);
		// space-all disables trimming.
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('」', true, true, false, 12, 12, 3), 0.001);
	}

	/** Hanging (H1): even if trim fails, allow-end lets commas and periods hang by up to the full advance. */
	public void testHang() {
		// Overflow 7 pt: trim(6) fails → hang(12) succeeds.
		assertEquals(12.0, JapaneseSpacingResolver.endAllowance('、', true, false, true, 12, 12, 7), 0.001);
		// Priority: if overflow≤trim, trimming wins (no hanging).
		assertEquals(6.0, JapaneseSpacingResolver.endAllowance('、', true, false, true, 12, 12, 5), 0.001);
		// Closing brackets cannot hang (only commas and periods can).
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('」', true, false, true, 12, 12, 7), 0.001);
		// Overflow>advance also prevents hanging.
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('、', true, false, true, 12, 12, 13), 0.001);
		// space-all still allows hanging (only trimming is disabled).
		assertEquals(12.0, JapaneseSpacingResolver.endAllowance('、', true, true, true, 12, 12, 7), 0.001);
	}

	/** Vertical writing also uses the caller-supplied vertical advance as the hanging amount. */
	public void testVerticalAdvanceForHang() {
		// Does not fit with 0.5em (6 pt), but fits with 10 pt from vmtx.
		assertEquals(10.0, JapaneseSpacingResolver.endAllowance('、', true, false, true, 10, 12, 8), 0.001);
	}

	/** Ineligible: half-width punctuation and non-punctuation. */
	public void testExcluded() {
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('、', false, false, true, 6, 12, 3), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('あ', true, false, true, 12, 12, 3), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('「', true, false, true, 12, 12, 3), 0.001);
	}

	/** Unconditional line-end trimming amount for trim-both. */
	public void testEndTrimAmount() {
		assertEquals(6.0, JapaneseSpacingResolver.endTrim('」', true, 12), 0.001);
		assertEquals(6.0, JapaneseSpacingResolver.endTrim('。', true, 12), 0.001);
		assertEquals(3.0, JapaneseSpacingResolver.endTrim('・', true, 12), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.endTrim('「', true, 12), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.endTrim('」', false, 12), 0.001);
	}

	/** Hanging amount for first/force-end. */
	public void testUnconditionalHangs() {
		assertEquals(-6.0, JapaneseSpacingResolver.firstHang('「', true, 12, 12, true), 0.001);
		assertEquals(-12.0, JapaneseSpacingResolver.firstHang('「', true, 12, 12, false), 0.001);
		assertEquals(-12.0, JapaneseSpacingResolver.firstHang('\u3000', true, 12, 12, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.firstHang('あ', true, 12, 12, true), 0.001);
		assertEquals(12.0, JapaneseSpacingResolver.forceEndHang('。', 12), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.forceEndHang('」', 12), 0.001);
	}
}
