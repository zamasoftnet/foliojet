package net.zamasoft.foliojet.layout.text.bidi;

import java.text.Bidi;

import junit.framework.TestCase;

/**
 * The preview of a paragraph broken across pages looks only at the lines added when the paragraph needs no reordering,
 * which {@link BidiParagraphLayout#staysLeftToRight} tells without asking {@link Bidi} (2026-10-31). Whatever it lets
 * through has to come out of {@link Bidi} left to right, in any company of the same; the JDK's tables can move.
 */
public class BidiLeftToRightPreviewTest extends TestCase {
	public void testNothingLetThroughNeedsReordering() {
		int passed = 0;
		for (int cp = 0; cp <= Character.MAX_CODE_POINT; ++cp) {
			if (Character.isSurrogate((char) cp) && cp <= 0xFFFF) {
				continue;
			}
			final char[] chars = Character.toChars(cp);
			if (!BidiParagraphLayout.staysLeftToRight(chars, chars.length)) {
				continue;
			}
			++passed;
			final String c = new String(chars);
			for (final String text : new String[] { c, "a" + c + "b", "1" + c + "2", "(" + c + ")" }) {
				assertTrue(Integer.toHexString(cp) + " in " + text,
						new Bidi(text, Bidi.DIRECTION_LEFT_TO_RIGHT).isLeftToRight());
			}
		}
		assertTrue(String.valueOf(passed), passed > 200_000);
	}

	public void testRightToLeftAndControlsAreNotLetThrough() {
		for (final int cp : new int[] { 0x05D0, 0x0627, 0x0661, 0x0660, 0x202A, 0x202B, 0x202C, 0x202D,
				0x202E, 0x2066, 0x2067, 0x2068, 0x2069, 0x200F, 0x10800, 0x1E800 }) {
			final char[] chars = Character.toChars(cp);
			assertFalse(Integer.toHexString(cp), BidiParagraphLayout.staysLeftToRight(chars, chars.length));
		}
	}

	public void testScansBeyondTheFirstCharacter() {
		final String text = "plain text あ and then א";
		assertFalse(BidiParagraphLayout.staysLeftToRight(text.toCharArray(), text.length()));
		assertTrue(BidiParagraphLayout.staysLeftToRight(text.toCharArray(), text.length() - 1));
	}
}
