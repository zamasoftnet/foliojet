package net.zamasoft.foliojet.layout.text.spacing;

import net.zamasoft.foliojet.css.value.TextAutospaceValue;

/**
 * Character classification and gap calculation for {@code text-autospace}
 * (Japanese spacing adjustment A2, 2026-07-31; consult-codex-2026-07-31-text-spacing.txt).
 * Pure calculations independent of boxes. Classification uses code points.
 *
 * <p>
 * Subset (following the intent of CSS Text 4, with deviations recorded): Japanese characters =
 * kanji (basic, extension A, compatibility, supplementary planes), kana (including extensions),
 * and 々〆〇. Latin-text letters = ASCII/Latin-1/Latin extended A/Greek/Cyrillic letters.
 * Numbers = ASCII digits only. Fullwidth alphanumerics (FF01-) are excluded because they have
 * Japanese character width. The gap is JLREQ's default 0.25 ic; since ic=em for fullwidth fonts,
 * approximate it with the Japanese-side run's font-size × 0.25
 * (measuring the advance of "水" as recommended is a future optimization).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TextAutospaceClasses {

	/** Spacing between Japanese and Latin text (em ratio). Quarter-em space from JLREQ 3.2.8. */
	public static final double GAP = 0.25;

	private TextAutospaceClasses() {
		// static
	}

	/** Character classes. */
	public enum Kind {
		IDEOGRAPH, ALPHA, NUMERIC,
		/**
		 * JLREQ cl-06/cl-07 full stops/commas. Only when proportional, add quarter-em space to Latin text/digits
		 * as for Japanese characters.
		 */
		PUNCTUATION, OTHER
	}

	/** Classifies a code point. */
	public static Kind of(final int cp) {
		// Japanese characters: kana, kanji, 々〆〇, kana extensions
		if (cp >= 0x3040 && cp <= 0x30FF || cp >= 0x31F0 && cp <= 0x31FF || cp >= 0x3005 && cp <= 0x3007
				|| cp >= 0x3400 && cp <= 0x4DBF || cp >= 0x4E00 && cp <= 0x9FFF || cp >= 0xF900 && cp <= 0xFAFF
				|| cp >= 0x20000 && cp <= 0x3FFFF) {
			return Kind.IDEOGRAPH;
		}
		// Numbers: ASCII only
		if (cp >= '0' && cp <= '9') {
			return Kind.NUMERIC;
		}
		// Latin-text letters: ASCII/Latin-1/Latin extended A/Greek/Cyrillic letters
		if (cp >= 'A' && cp <= 'Z' || cp >= 'a' && cp <= 'z') {
			return Kind.ALPHA;
		}
		if (cp >= 0x00C0 && cp <= 0x024F && Character.isLetter(cp) || cp >= 0x0370 && cp <= 0x03FF
				&& Character.isLetter(cp) || cp >= 0x0400 && cp <= 0x04FF) {
			return Kind.ALPHA;
		}
		if (JapaneseSpacingClass.of(cp) == JapaneseSpacingClass.PUNCTUATION) {
			return Kind.PUNCTUATION;
		}
		return Kind.OTHER;
	}

	/**
	 * Whether the preceding character is proportional punctuation (eligible for spacing between
	 * Japanese and Latin text). JLREQ 3.2.8 inserts this spacing between kanji/kana and Latin-text
	 * characters, excluding full stops/commas. Fullwidth full stops/commas already include half-em
	 * space after the ink, so that is fine. With proportional punctuation, such as IPA P fonts or
	 * {@code palt}, this space is absent and Latin text/digits approach the ink (around 0.2 em).
	 * Therefore, treats only proportional full stops/commas (advance at most 0.75 em,
	 * {@link JapaneseSpacingResolver#isWide}) like Japanese characters (2026-09-14; user report:
	 * "With palt, spacing between Japanese and Latin text is missing between punctuation and Latin text").
	 *
	 * @param metrics FontMetrics for the preceding character ({@code null} = unknown, ineligible)
	 */
	public static boolean proportionalPunctuation(final int prevCp,
			final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics, final int gid, final double fontSize,
			final net.zamasoft.pdfg2d.gc.font.FontStyle.Direction direction) {
		return metrics != null && gid >= 0 && of(prevCp) == Kind.PUNCTUATION
				&& !JapaneseSpacingResolver.isWide(metrics, gid, fontSize, direction);
	}

	/**
	 * Gap between adjacent pairs (em ratio; 0 = none). Do not apply to pairs separated by
	 * spaces or similar controls (the caller resets on controls).
	 *
	 * @param prevCp preceding character
	 * @param cp     following character
	 * @param flags  effective flags ({@code TextAutospaceValue.ALPHA}|{@code NUMERIC})
	 */
	public static double gapEm(final int prevCp, final int cp, final byte flags) {
		return gapEm(prevCp, cp, flags, false);
	}

	/**
	 * Adds to {@link #gapEm(int, int, byte)} the check that treats preceding proportional
	 * punctuation like a Japanese character ({@link #proportionalPunctuation}).
	 */
	public static double gapEm(final int prevCp, final int cp, final byte flags,
			final boolean prevProportionalPunctuation) {
		if (flags == 0) {
			return 0;
		}
		Kind prev = of(prevCp);
		if (prev == Kind.PUNCTUATION && prevProportionalPunctuation) {
			prev = Kind.IDEOGRAPH;
		}
		final Kind next = of(cp);
		if (prev == next) {
			return 0;
		}
		final Kind latin = prev == Kind.IDEOGRAPH ? next : next == Kind.IDEOGRAPH ? prev : Kind.OTHER;
		if (latin == Kind.ALPHA && (flags & TextAutospaceValue.ALPHA) != 0) {
			return GAP;
		}
		if (latin == Kind.NUMERIC && (flags & TextAutospaceValue.NUMERIC) != 0) {
			return GAP;
		}
		return 0;
	}

	/** True if the Japanese side of the pair (including proportional punctuation) is prev (for font-size selection). */
	public static boolean ideographFirst(final int prevCp) {
		final Kind kind = of(prevCp);
		return kind == Kind.IDEOGRAPH || kind == Kind.PUNCTUATION;
	}
}
