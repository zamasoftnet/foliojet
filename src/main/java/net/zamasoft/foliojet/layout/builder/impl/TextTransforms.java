package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * Character mapping for {@code text-transform} (on 2026-10-06, consolidated three copies for
 * body text, ruby, and warichu, and added {@code full-width}).
 *
 * <p>
 * Converts to full-width forms after changing case. Each mapping converts one character to one
 * character, preserving the character count (so positions in the document remain usable).
 * </p>
 */
final class TextTransforms {
	private TextTransforms() {
		// Unused
	}

	/** Transforms {@code ch[off..off+len)} in place. Counts word starts for {@code capitalize} within this range. */
	static void apply(final byte transform, final char[] ch, final int off, final int len) {
		switch (transform & AbstractTextParams.TEXT_TRANSFORM_CASE_MASK) {
		case AbstractTextParams.TEXT_TRANSFORM_LOWERCASE:
			for (int i = 0; i < len; ++i) {
				ch[i + off] = Character.toLowerCase(ch[i + off]);
			}
			break;
		case AbstractTextParams.TEXT_TRANSFORM_UPPERCASE:
			for (int i = 0; i < len; ++i) {
				ch[i + off] = Character.toUpperCase(ch[i + off]);
			}
			break;
		case AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE:
			boolean spaceBefore = true;
			for (int i = 0; i < len; ++i) {
				final char c = ch[i + off];
				if (Character.isLetter(c)) {
					if (spaceBefore) {
						ch[i + off] = Character.toUpperCase(c);
					}
					spaceBefore = false;
				} else {
					spaceBefore = true;
				}
			}
			break;
		case AbstractTextParams.TEXT_TRANSFORM_NONE:
			break;
		default:
			throw new IllegalStateException();
		}
		if ((transform & AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH) != 0) {
			for (int i = 0; i < len; ++i) {
				ch[i + off] = fullWidth(ch[i + off]);
			}
		}
	}

	/** Transforms one character (warichu; the caller supplies whether it starts a word for {@code capitalize}). */
	static char apply(final char c, final byte transform, final boolean wordStart) {
		final char cased = switch (transform & AbstractTextParams.TEXT_TRANSFORM_CASE_MASK) {
		case AbstractTextParams.TEXT_TRANSFORM_LOWERCASE -> Character.toLowerCase(c);
		case AbstractTextParams.TEXT_TRANSFORM_UPPERCASE -> Character.toUpperCase(c);
		case AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE -> wordStart ? Character.toUpperCase(c) : c;
		default -> c;
		};
		return (transform & AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH) != 0 ? fullWidth(cased) : cased;
	}

	/**
	 * Returns the full-width form if one exists (css-text-3 §2.1.1 {@code full-width}).
	 * Maps ASCII letters and symbols to U+FF01–U+FF5E, spaces to U+3000, and ¢£¬¯¦¥₩ to U+FFE0–U+FFE6.
	 * Leaves half-width kana unchanged because they require composition with voiced/semi-voiced marks.
	 */
	static char fullWidth(final char c) {
		if (c == ' ') {
			return '　';
		}
		if (c >= '!' && c <= '~') {
			return (char) (c - '!' + '！');
		}
		return switch (c) {
		case '¢' -> '￠';
		case '£' -> '￡';
		case '¬' -> '￢';
		case '¯' -> '￣';
		case '¦' -> '￤';
		case '¥' -> '￥';
		case '₩' -> '￦';
		default -> c;
		};
	}
}
