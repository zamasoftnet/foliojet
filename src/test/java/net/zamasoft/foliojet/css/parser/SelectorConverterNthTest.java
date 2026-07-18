package net.zamasoft.foliojet.css.parser;

import junit.framework.TestCase;

/**
 * An+B構文パーサー(SelectorConverter.parseNth)の単体テスト。
 * パッケージ非公開メソッドを同一パッケージから直接検証します。
 */
public class SelectorConverterNthTest extends TestCase {
	public void testOddEven() {
		assertEquals2(2, 1, SelectorConverter.parseNth("odd"));
		assertEquals2(2, 1, SelectorConverter.parseNth("ODD"));
		assertEquals2(2, 0, SelectorConverter.parseNth("even"));
	}

	public void testPlainInteger() {
		assertEquals2(0, 5, SelectorConverter.parseNth("5"));
		assertEquals2(0, -3, SelectorConverter.parseNth("-3"));
		assertEquals2(0, 0, SelectorConverter.parseNth("0"));
	}

	public void testNAlone() {
		assertEquals2(1, 0, SelectorConverter.parseNth("n"));
		assertEquals2(1, 0, SelectorConverter.parseNth("N"));
		assertEquals2(-1, 0, SelectorConverter.parseNth("-n"));
		assertEquals2(1, 0, SelectorConverter.parseNth("+n"));
	}

	public void testAnPlusB() {
		assertEquals2(2, 1, SelectorConverter.parseNth("2n+1"));
		assertEquals2(2, -1, SelectorConverter.parseNth("2n-1"));
		assertEquals2(-1, 3, SelectorConverter.parseNth("-n+3"));
		assertEquals2(3, 0, SelectorConverter.parseNth("3n"));
		assertEquals2(3, 0, SelectorConverter.parseNth("3n+0"));
	}

	public void testWhitespaceBetweenSignAndDigits() {
		// トークナイズ由来で "+ 3" のように空白が入る場合がある
		assertEquals2(2, 3, SelectorConverter.parseNth("2n + 3"));
		assertEquals2(2, -3, SelectorConverter.parseNth("2n - 3"));
	}

	public void testUnparsable() {
		assertNull(SelectorConverter.parseNth(""));
		assertNull(SelectorConverter.parseNth("foo"));
		assertNull(SelectorConverter.parseNth(null));
	}

	private static void assertEquals2(int expectedA, int expectedB, int[] actual) {
		assertNotNull(actual);
		assertEquals("a", expectedA, actual[0]);
		assertEquals("b", expectedB, actual[1]);
	}
}
