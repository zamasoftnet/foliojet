package net.zamasoft.foliojet.layout.builder.impl;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/** {@code text-transform} の字の写し({@link TextTransforms})を固定します(2026-10-06、{@code full-width} を加えた)。 */
public class TextTransformsTest extends TestCase {
	private static final byte FULL_WIDTH = AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH;

	private static String apply(final byte transform, final String text) {
		final char[] ch = text.toCharArray();
		TextTransforms.apply(transform, ch, 0, ch.length);
		return new String(ch);
	}

	/** ASCII の字と記号は U+FF01〜U+FF5E、空白は U+3000、¢£¬¯¦¥₩ は U+FFE0〜U+FFE6。ほかの字は変えない。 */
	public void testFullWidthMapping() {
		assertEquals("ＡＢｃ　１２！～", apply(FULL_WIDTH, "ABc 12!~"));
		assertEquals("￠￡￢￣￤￥￦", apply(FULL_WIDTH, "¢£¬¯¦¥₩"));
		assertEquals("漢字ｶﾅ２", apply(FULL_WIDTH, "漢字ｶﾅ2"));
	}

	/** 大文字・小文字の変換のあとに全角化する。 */
	public void testCaseThenFullWidth() {
		assertEquals("ＡＢ　ＣＤ", apply((byte) (AbstractTextParams.TEXT_TRANSFORM_UPPERCASE | FULL_WIDTH), "ab cd"));
		assertEquals("Ａｂ　Ｃｄ", apply((byte) (AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE | FULL_WIDTH), "ab cd"));
		assertEquals("ａｂ", apply((byte) (AbstractTextParams.TEXT_TRANSFORM_LOWERCASE | FULL_WIDTH), "AB"));
		assertEquals("AB CD", apply(AbstractTextParams.TEXT_TRANSFORM_UPPERCASE, "ab cd"));
		assertEquals("ab cd", apply(AbstractTextParams.TEXT_TRANSFORM_NONE, "ab cd"));
	}

	/** 1 字ずつの写し(割注)も同じ。 */
	public void testSingleCharacter() {
		assertEquals('Ａ', TextTransforms.apply('a', (byte) (AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE | FULL_WIDTH), true));
		assertEquals('ａ', TextTransforms.apply('a', (byte) (AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE | FULL_WIDTH), false));
		assertEquals('a', TextTransforms.apply('a', AbstractTextParams.TEXT_TRANSFORM_NONE, true));
	}
}
