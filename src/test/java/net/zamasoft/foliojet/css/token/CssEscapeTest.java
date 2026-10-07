package net.zamasoft.foliojet.css.token;

import junit.framework.TestCase;

/**
 * Fix the single definition of CSS escaping ({@link Tokens#unescape}) (2026-10-04, overall review).
 * Three copies for strings, identifiers, and selectors handled out-of-range values and whitespace
 * after escapes differently, throwing exceptions for out-of-range values.
 */
public class CssEscapeTest extends TestCase {
	public void testHexEscapes() {
		assertEquals("A", Tokens.unescape("\\41"));
		assertEquals("AB", Tokens.unescape("\\41 B"));
		// Consume exactly one whitespace separator. Tabs and line breaks also separate.
		assertEquals("A B", Tokens.unescape("\\41  B"));
		assertEquals("AB", Tokens.unescape("\\41\tB"));
		assertEquals("AB", Tokens.unescape("\\41\nB"));
		// Up to six digits
		assertEquals("\u00a91", Tokens.unescape("\\0000a91"));
		assertEquals(new String(Character.toChars(0x1F600)), Tokens.unescape("\\1F600"));
	}

	public void testInvalidCodePointsBecomeReplacementCharacters() {
		assertEquals("\ufffd", Tokens.unescape("\\0"));
		assertEquals("\ufffd", Tokens.unescape("\\110000"));
		assertEquals("\ufffd", Tokens.unescape("\\FFFFFF"));
		assertEquals("\ufffd", Tokens.unescape("\\D800"));
	}

	public void testOtherEscapesAndPlainText() {
		assertEquals("a.b", Tokens.unescape("a\\.b"));
		assertEquals("1x", Tokens.unescape("\\31 x"));
		assertEquals("plain", Tokens.unescape("plain"));
		assertEquals("end\\", Tokens.unescape("end\\"));
	}
}
