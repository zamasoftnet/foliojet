package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * {@code text-transform} の字の写しです(2026-10-06 に本文・ルビ・割注の 3 つの写しを集め、{@code full-width} を加えた)。
 *
 * <p>
 * 大文字・小文字の変換のあとに全角化する。どれも 1 字を 1 字へ写すので、字数は変わらない(文書の中の位置が
 * そのまま使える)。
 * </p>
 */
final class TextTransforms {
	private TextTransforms() {
		// 使わない
	}

	/** {@code ch[off..off+len)} をその場で変えます。{@code capitalize} の語頭はこの範囲の中で数える。 */
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

	/** 1 字を変えます(割注。{@code capitalize} の語頭かは呼び出し側が渡す)。 */
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
	 * 全角の形があればそれを返します(css-text-3 §2.1.1 {@code full-width})。ASCII の字と記号は U+FF01〜U+FF5E へ、
	 * 空白は U+3000 へ、¢£¬¯¦¥₩ は U+FFE0〜U+FFE6 へ。半角カナは濁点・半濁点の合成が絡むので変えない。
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
