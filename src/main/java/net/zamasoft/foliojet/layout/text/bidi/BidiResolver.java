package net.zamasoft.foliojet.layout.text.bidi;

import java.text.Bidi;

import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * Rules mapping CSS {@code direction}/{@code unicode-bidi} to input for the Unicode
 * Bidirectional Algorithm (UAX #9). Added 2026-09-04, bidi-isolation-design.md §2-2/§2-3.
 *
 * <p>
 * Determines the paragraph base direction and control characters inserted at inline boundaries
 * in the synthesized string passed to {@link java.text.Bidi}
 * (JDK 21; resolves isolates through FSI/LRI/RLI/PDI).
 * CSS-injected controls do not appear in rendering, extraction, or goldens
 * (they are used only as indices in the synthesized sequence).
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
	/** PARAGRAPH SEPARATOR (forced paragraph boundary; bidi type B) */
	public static final char PARAGRAPH_SEPARATOR = '\u2029';
	/** LEFT-TO-RIGHT MARK (strong placeholder for an atomic inline) */
	public static final char LRM = '\u200E';
	/** RIGHT-TO-LEFT MARK (strong placeholder for an atomic inline) */
	public static final char RLM = '\u200F';

	private BidiResolver() {
	}

	/**
	 * Control characters inserted into the synthesized sequence at inline start
	 * (table in css-writing-modes-3 §2.2).
	 *
	 * @param direction   the inline's {@code direction}
	 * @param unicodeBidi the inline's {@code unicode-bidi}
	 * @return control character string (empty for normal)
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
	 * Control characters inserted into the synthesized sequence at inline end
	 * (counterpart to {@link #openingControls}).
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
	 * Paragraph base direction ({@link java.text.Bidi} flags). Determined from the block's
	 * {@code direction}; only blocks with {@code unicode-bidi: plaintext} use automatic
	 * detection from the first strong character (P2/P3).
	 */
	public static int baseDirectionFlag(final byte blockDirection, final byte blockUnicodeBidi) {
		if (blockUnicodeBidi == UnicodeBidiValue.PLAINTEXT) {
			return Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
		}
		return blockDirection == AbstractTextParams.DIRECTION_RTL ? Bidi.DIRECTION_RIGHT_TO_LEFT
				: Bidi.DIRECTION_LEFT_TO_RIGHT;
	}

	/** Opening override control applied to the block's root inline. */
	public static String rootOpeningControls(final byte direction, final byte unicodeBidi) {
		if (unicodeBidi != UnicodeBidiValue.BIDI_OVERRIDE
				&& unicodeBidi != UnicodeBidiValue.ISOLATE_OVERRIDE) {
			return "";
		}
		return String.valueOf(direction == AbstractTextParams.DIRECTION_RTL ? RLO : LRO);
	}

	/** Counterpart to {@link #rootOpeningControls}. */
	public static String rootClosingControls(final byte unicodeBidi) {
		return unicodeBidi == UnicodeBidiValue.BIDI_OVERRIDE
				|| unicodeBidi == UnicodeBidiValue.ISOLATE_OVERRIDE ? String.valueOf(PDF) : "";
	}

	/** Single character used to represent a replaced atomic inline in UBA. */
	public static char atomicCharacter(final byte direction, final byte unicodeBidi) {
		if (unicodeBidi == UnicodeBidiValue.EMBED || unicodeBidi == UnicodeBidiValue.BIDI_OVERRIDE) {
			return direction == AbstractTextParams.DIRECTION_RTL ? RLM : LRM;
		}
		return OBJECT;
	}
}
