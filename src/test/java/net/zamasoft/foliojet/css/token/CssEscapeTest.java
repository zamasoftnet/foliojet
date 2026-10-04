package net.zamasoft.foliojet.css.token;

import junit.framework.TestCase;

/**
 * CSS のエスケープの唯一の定義({@link Tokens#unescape})を固定します(2026-10-04、全体レビュー。文字列・識別子・
 * セレクタの 3 つの写しが範囲外の値とエスケープの後ろの空白を別々に扱い、範囲外では例外になっていた)。
 */
public class CssEscapeTest extends TestCase {
	public void testHexEscapes() {
		assertEquals("A", Tokens.unescape("\\41"));
		assertEquals("AB", Tokens.unescape("\\41 B"));
		// 区切りの空白は 1 つだけ消費する。タブ・改行も区切り
		assertEquals("A B", Tokens.unescape("\\41  B"));
		assertEquals("AB", Tokens.unescape("\\41\tB"));
		assertEquals("AB", Tokens.unescape("\\41\nB"));
		// 6 桁まで
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
