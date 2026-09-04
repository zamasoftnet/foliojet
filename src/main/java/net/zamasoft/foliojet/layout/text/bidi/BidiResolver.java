package net.zamasoft.foliojet.layout.text.bidi;

import java.text.Bidi;

import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * CSS の{@code direction}/{@code unicode-bidi}を Unicode 双方向アルゴリズム
 * (UAX #9)の入力へ写す規則です(2026-09-04、bidi-isolation-design.md §2-2/§2-3)。
 *
 * <p>
 * {@link java.text.Bidi}(JDK 21。isolate の FSI/LRI/RLI/PDI まで解決する)へ渡す
 * 合成文字列に、inline の境界で挿入する制御文字と、段落の基準方向を決める。
 * CSS が注入した制御文字は描画・抽出・golden へは出さない(合成列の索引でだけ使う)。
 * </p>
 */
public final class BidiResolver {
	/** LEFT-TO-RIGHT EMBEDDING */
	public static final char LRE = '\u202A';
	/** RIGHT-TO-LEFT EMBEDDING */
	public static final char RLE = '\u202B';
	/** POP DIRECTIONAL FORMATTING */
	public static final char PDF = '\u202C';
	/** LEFT-TO-RIGHT OVERRIDE */
	public static final char LRO = '\u202D';
	/** RIGHT-TO-LEFT OVERRIDE */
	public static final char RLO = '\u202E';
	/** LEFT-TO-RIGHT ISOLATE */
	public static final char LRI = '\u2066';
	/** RIGHT-TO-LEFT ISOLATE */
	public static final char RLI = '\u2067';
	/** FIRST STRONG ISOLATE */
	public static final char FSI = '\u2068';
	/** POP DIRECTIONAL ISOLATE */
	public static final char PDI = '\u2069';
	/** OBJECT REPLACEMENT CHARACTER(atomic inline) */
	public static final char OBJECT = '\uFFFC';
	/** PARAGRAPH SEPARATOR(強制段落区切り。bidi type B) */
	public static final char PARAGRAPH_SEPARATOR = '\u2029';
	/** LEFT-TO-RIGHT MARK(atomic inline の strong 代替) */
	public static final char LRM = '\u200E';
	/** RIGHT-TO-LEFT MARK(atomic inline の strong 代替) */
	public static final char RLM = '\u200F';

	private BidiResolver() {
	}

	/**
	 * inline の開始で合成列へ入れる制御文字(css-writing-modes-3 §2.2 の表)。
	 *
	 * @param direction   inline の {@code direction}
	 * @param unicodeBidi inline の {@code unicode-bidi}
	 * @return 制御文字列(normal は空)
	 */
	public static String openingControls(final byte direction, final byte unicodeBidi) {
		final boolean rtl = direction == AbstractTextParams.DIRECTION_RTL;
		switch (unicodeBidi) {
		case UnicodeBidiValue.EMBED:
			return String.valueOf(rtl ? RLE : LRE);
		case UnicodeBidiValue.BIDI_OVERRIDE:
			return String.valueOf(rtl ? RLO : LRO);
		case UnicodeBidiValue.ISOLATE:
			return String.valueOf(rtl ? RLI : LRI);
		case UnicodeBidiValue.ISOLATE_OVERRIDE:
			return new String(new char[] { FSI, rtl ? RLO : LRO });
		case UnicodeBidiValue.PLAINTEXT:
			return String.valueOf(FSI);
		default:
			return "";
		}
	}

	/**
	 * inline の終了で合成列へ入れる制御文字({@link #openingControls}の対)。
	 */
	public static String closingControls(final byte unicodeBidi) {
		switch (unicodeBidi) {
		case UnicodeBidiValue.EMBED:
		case UnicodeBidiValue.BIDI_OVERRIDE:
			return String.valueOf(PDF);
		case UnicodeBidiValue.ISOLATE:
		case UnicodeBidiValue.PLAINTEXT:
			return String.valueOf(PDI);
		case UnicodeBidiValue.ISOLATE_OVERRIDE:
			return new String(new char[] { PDF, PDI });
		default:
			return "";
		}
	}

	/**
	 * 段落の基準方向({@link java.text.Bidi}のフラグ)。ブロックの{@code direction}から
	 * 決め、{@code unicode-bidi: plaintext} のブロックだけ先頭の強い文字による
	 * 自動判定(P2/P3)を使う。
	 */
	public static int baseDirectionFlag(final byte blockDirection, final byte blockUnicodeBidi) {
		if (blockUnicodeBidi == UnicodeBidiValue.PLAINTEXT) {
			return Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
		}
		return blockDirection == AbstractTextParams.DIRECTION_RTL ? Bidi.DIRECTION_RIGHT_TO_LEFT
				: Bidi.DIRECTION_LEFT_TO_RIGHT;
	}

	/** block の root inline に適用する override の開始制御。 */
	public static String rootOpeningControls(final byte direction, final byte unicodeBidi) {
		if (unicodeBidi != UnicodeBidiValue.BIDI_OVERRIDE
				&& unicodeBidi != UnicodeBidiValue.ISOLATE_OVERRIDE) {
			return "";
		}
		return String.valueOf(direction == AbstractTextParams.DIRECTION_RTL ? RLO : LRO);
	}

	/** {@link #rootOpeningControls} の対。 */
	public static String rootClosingControls(final byte unicodeBidi) {
		return unicodeBidi == UnicodeBidiValue.BIDI_OVERRIDE
				|| unicodeBidi == UnicodeBidiValue.ISOLATE_OVERRIDE ? String.valueOf(PDF) : "";
	}

	/** replaced atomic inline を UBA へ入れる1文字。 */
	public static char atomicCharacter(final byte direction, final byte unicodeBidi) {
		if (unicodeBidi == UnicodeBidiValue.EMBED || unicodeBidi == UnicodeBidiValue.BIDI_OVERRIDE) {
			return direction == AbstractTextParams.DIRECTION_RTL ? RLM : LRM;
		}
		return OBJECT;
	}

	/**
	 * @deprecated 文字値だけでは本文と CSS 合成を区別できない。
	 *             {@link BidiParagraphBuffer#isSyntheticControl(int)} を使う。
	 */
	@Deprecated
	public static boolean isControl(final char c) {
		return (c >= LRE && c <= RLO) || (c >= LRI && c <= PDI);
	}
}
