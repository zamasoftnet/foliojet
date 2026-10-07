package net.zamasoft.foliojet.layout.builder.impl;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * Locks down {@code text-transform} character mapping ({@link TextTransforms}) (2026-10-06; added {@code
 * full-width}).
 */
public class TextTransformsTest extends TestCase {
	private static final byte FULL_WIDTH = AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH;

	private static String apply(final byte transform, final String text) {
		final char[] ch = text.toCharArray();
		TextTransforms.apply(transform, ch, 0, ch.length);
		return new String(ch);
	}

	/**
	 * Maps ASCII characters and symbols to U+FF01–U+FF5E, space to U+3000, and ¢£¬¯¦¥₩ to U+FFE0–U+FFE6. Others
	 * are unchanged.
	 */
	public void testFullWidthMapping() {
		assertEquals("ＡＢｃ　１２！～", apply(FULL_WIDTH, "ABc 12!~"));
		assertEquals("￠￡￢￣￤￥￦", apply(FULL_WIDTH, "¢£¬¯¦¥₩"));
		assertEquals("漢字ｶﾅ２", apply(FULL_WIDTH, "漢字ｶﾅ2"));
	}

	/** Converts to full-width after uppercase/lowercase conversion. */
	public void testCaseThenFullWidth() {
		assertEquals("ＡＢ　ＣＤ", apply((byte) (AbstractTextParams.TEXT_TRANSFORM_UPPERCASE | FULL_WIDTH), "ab cd"));
		assertEquals("Ａｂ　Ｃｄ", apply((byte) (AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE | FULL_WIDTH), "ab cd"));
		assertEquals("ａｂ", apply((byte) (AbstractTextParams.TEXT_TRANSFORM_LOWERCASE | FULL_WIDTH), "AB"));
		assertEquals("AB CD", apply(AbstractTextParams.TEXT_TRANSFORM_UPPERCASE, "ab cd"));
		assertEquals("ab cd", apply(AbstractTextParams.TEXT_TRANSFORM_NONE, "ab cd"));
	}

	/** Per-character mapping (warichu) behaves the same way. */
	public void testSingleCharacter() {
		assertEquals('Ａ', TextTransforms.apply('a', (byte) (AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE | FULL_WIDTH), true));
		assertEquals('ａ', TextTransforms.apply('a', (byte) (AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE | FULL_WIDTH), false));
		assertEquals('a', TextTransforms.apply('a', AbstractTextParams.TEXT_TRANSFORM_NONE, true));
	}
}
