package net.zamasoft.foliojet.layout.text.spacing;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * Japanese spacing resolver (Japanese spacing adjustment S0, 2026-07-31;
 * consult-codex-2026-07-31-text-spacing.txt). Calculates trimming without changing box or font state.
 * S1 moves punctuation trimming from OpenTypeFont.getKerning and flush line-start alignment
 * for vertical writing from TextBuilder here (unchanged output is the acceptance criterion).
 *
 * <p>
 * Rules use <b>em ratios</b> (the caller multiplies by font-size). Glyph bounds (ink) measurements
 * and {@code cappedPairTrim} return absolute amounts already scaled by font-size.
 * The caller evaluates and passes "wide" using the original "font-unit width &gt;750/1000" check
 * (whether punctuation has fullwidth-equivalent width).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class JapaneseSpacingResolver {

	private JapaneseSpacingResolver() {
		// static
	}

	/** Trim between consecutive punctuation marks (em ratio). */
	public static final double PAIR_TRIM = 0.5;

	/** Leading ink edge. Returns distance from the pen scaled by font-size; NaN if unmeasurable. */
	public static double inkStart(final FontMetrics metrics, final int gid, final double fontSize,
			final FontStyle style) {
		return inkEdge(metrics, gid, fontSize, style, false);
	}

	/** Trailing ink edge. Preserves signed overflow as well. */
	public static double inkEnd(final FontMetrics metrics, final int gid, final double fontSize,
			final FontStyle style) {
		return inkEdge(metrics, gid, fontSize, style, true);
	}

	private static double inkEdge(final FontMetrics metrics, final int gid, final double fontSize,
			final FontStyle style, final boolean end) {
		if (!(metrics instanceof FontMetricsImpl m) || !(m.getFont() instanceof ShapedFont font)) {
			return Double.NaN;
		}
		final var bounds = font.getGlyphBounds(gid);
		if (bounds == null) {
			return Double.NaN;
		}
		final var source = font.getFontSource();
		// Use the glyph's x axis even when a horizontal font is rotated sideways in a vertical line.
		final boolean vertical = source.getDirection() == FontStyle.Direction.TB;
		final double scale = fontSize / 1000.0;
		final double edge = vertical
				? font.getVerticalOrigin(gid) + (end ? bounds.maxY() : bounds.minY())
				: font.getPlacementAdjustment(gid, style.getFeatures()) + (end ? bounds.maxX() : bounds.minX());
		double expansion = 0;
		if (style.getWeight().w >= 500 && source.getWeight().w < 500 && style.getSynthesisWeight()) {
			// Same stroke width as FontUtils.drawText. Ink expands by half that width on each side.
			expansion = fontSize / switch (style.getWeight()) {
				case W_500 -> 28.0;
				case W_600 -> 24.0;
				case W_700 -> 20.0;
				case W_800 -> 16.0;
				case W_900 -> 12.0;
				default -> throw new IllegalStateException();
			} / 2.0;
		}
		if (style.getStyle() != FontStyle.Style.NORMAL && !source.isItalic() && style.getSynthesisStyle()) {
			// Synthetic italic expands sides by shear geometry. Horizontally x'=x−0.25y (y downward), so the top (y<0)
			// pushes the trailing edge right and the bottom (y>0) pushes the leading edge left. Vertically y'=y+0.25x,
			// so the right (x>0) pushes the trailing edge down and the left (x<0) pushes the leading edge up. Uniform
			// expansion needlessly reduces autospace compression capacity and changes line allocation (2026-09-12).
			if (vertical) {
				expansion += 0.25 * scale * Math.max(0, end ? bounds.maxX() : -bounds.minX());
			} else {
				expansion += 0.25 * scale * Math.max(0, end ? -bounds.minY() : bounds.maxY());
			}
		}
		return edge * scale + (end ? expansion : -expansion);
	}

	/** Ink gap for a given pen-to-pen distance between two characters. Does not clamp each side to zero. */
	public static double inkGap(final FontMetrics prevMetrics, final int prevGid, final double prevSize,
			final FontStyle prevStyle, final FontMetrics metrics, final int gid, final double fontSize,
			final FontStyle style, final double penDistance) {
		return penDistance + inkStart(metrics, gid, fontSize, style)
				- inkEnd(prevMetrics, prevGid, prevSize, prevStyle);
	}

	/**
	 * Trim between consecutive punctuation marks, capped by the ink gap (absolute amount).
	 * Excludes letter spacing and xadvance so insertion, reversal at splits, and intrinsic-size
	 * measurement can recalculate from identical inputs.
	 * The caller must exclude pairs with nonzero kerning in the same run.
	 */
	public static double cappedPairTrim(final int prevCp, final FontMetrics prevMetrics, final int prevGid,
			final double prevSize, final FontStyle prevStyle, final int cp, final FontMetrics metrics,
			final int gid, final double fontSize, final FontStyle style) {
		final double nominal = pairTrim(prevCp, isWide(prevMetrics, prevGid, prevSize, prevStyle.getDirection()),
				cp, isWide(metrics, gid, fontSize, style.getDirection())) * fontSize;
		if (nominal == 0) {
			return 0;
		}
		final double gap = inkGap(prevMetrics, prevGid, prevSize, prevStyle, metrics, gid, fontSize, style,
				prevMetrics.getAdvance(prevGid));
		return Double.isNaN(gap) ? nominal : Math.min(nominal, Math.max(0, gap));
	}

	/**
	 * Trim between two consecutive characters (em ratio; 0 = no trim).
	 * Uses the same table as the original implementation (OpenTypeFont.getKerning):
	 * <ul>
	 * <li>opening + opening (both wide): 0.5</li>
	 * <li>closing + {opening|closing|full stop/comma} (both wide): 0.5</li>
	 * <li>full stop/comma (wide) + opening (wide): 0.5</li>
	 * <li>full stop/comma (wide) + closing (wide): 0.5</li>
	 * </ul>
	 * Do not apply to pairs with nonzero GPOS kerning (caller contract;
	 * the original implementation gives GPOS priority).
	 */
	public static double pairTrim(final int prevCodePoint, final boolean prevWide, final int codePoint,
			final boolean wide) {
		final JapaneseSpacingClass prev = JapaneseSpacingClass.of(prevCodePoint);
		if (prev == JapaneseSpacingClass.OTHER || !prevWide) {
			return 0;
		}
		final JapaneseSpacingClass next = JapaneseSpacingClass.of(codePoint);
		switch (prev) {
		case OPENING:
			return next == JapaneseSpacingClass.OPENING && wide ? PAIR_TRIM : 0;
		case CLOSING:
			return next != JapaneseSpacingClass.OTHER && wide ? PAIR_TRIM : 0;
		case PUNCTUATION:
			if (next == JapaneseSpacingClass.OPENING) {
				// JLREQ half-em spacing assumes fullwidth punctuation cells. If fallback or similar handling
				// makes the following character proportional, do not subtract a fixed 0.5 em.
				return wide ? PAIR_TRIM : 0;
			}
			return next == JapaneseSpacingClass.CLOSING && wide ? PAIR_TRIM : 0;
		case MIDDLE_DOT:
			// JLREQ 3.1.5: a middle dot followed by an opening bracket needs quarter-em space after the middle dot
			// (internal quarter + bracket half = 3/4, reduced to a quarter by -0.5 em. Closing bracket + middle dot is
			// already covered by next!=OTHER on the CLOSING side)
			return next == JapaneseSpacingClass.OPENING && wide ? PAIR_TRIM : 0;
		default:
			return 0;
		}
	}

	/**
	 * Flush line-start indent (em ratio, negative). Regardless of writing direction, returns -0.5 em
	 * when the first visible text at line start begins with a fullwidth-equivalent opening bracket
	 * (originally vertical-only handling in TextBuilder).
	 * Applies flush alignment only for CSS Text 4 {@code text-spacing-trim: trim-start};
	 * {@code normal} and {@code space-all} retain the half-em line-start space offered by JLREQ.
	 * Returns zero for proportional punctuation and all other characters.
	 */
	public static double lineHeadIndent(final int firstCodePoint, final boolean wide, final boolean trimStart) {
		return trimStart && wide && JapaneseSpacingClass.of(firstCodePoint) == JapaneseSpacingClass.OPENING ? -PAIR_TRIM
				: 0;
	}

	/**
	 * Amount hung outside the start of the first formatted line by {@code hanging-punctuation:first}.
	 * For fullwidth opening brackets already reduced to halfwidth by text-spacing, hangs 0.5 em;
	 * for other eligible glyphs, hangs their entire actual advance.
	 */
	public static double firstHang(final int codePoint, final boolean wide, final double advance,
			final double fontSize, final boolean trimmedStart) {
		if (codePoint == 0x3000) {
			return -advance;
		}
		final int type = Character.getType(codePoint);
		final boolean quoteOrBracket = type == Character.START_PUNCTUATION
				|| type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION
				|| codePoint == 0x27 || codePoint == 0x22;
		if (!quoteOrBracket) {
			return 0;
		}
		if (trimmedStart && wide && JapaneseSpacingClass.of(codePoint) == JapaneseSpacingClass.OPENING) {
			return -PAIR_TRIM * fontSize;
		}
		return -advance;
	}

	/**
	 * Returns whether justification may expand the space immediately after this character.
	 *
	 * <p>For middle dots (cl-05) in JLREQ 3.1.5, the trailing quarter-em space included in the glyph
	 * should normally be removed, leaving solid spacing; it is not an unrestricted expansion point
	 * for line adjustment. Line-start kinsoku (line-breaking rules) alone protects "before the middle dot"
	 * but not "after the middle dot," so both justify count/apply use this check.</p>
	 */
	public static boolean allowsJustificationAfter(final int codePoint) {
		return JapaneseSpacingClass.of(codePoint) != JapaneseSpacingClass.MIDDLE_DOT;
	}

	/** JLREQ cl-07 (commas). Used to distinguish their compression priority from cl-06 (full stops). */
	public static boolean isComma(final int codePoint) {
		return codePoint == 0x3001 || codePoint == 0xFF0C;
	}

	/**
	 * Allowance for line-end compression (T2)/hanging (H1)
	 * (Japanese spacing adjustment; pure function for consult-codex-2026-07-31-text-spacing.txt T2/H1).
	 * If the last glyph is eligible punctuation, returns the option that fits the line in priority
	 * order (trim → hang). Returns zero if ineligible or neither fits (use conventional push-out).
	 *
	 * @param codePoint line-end code point
	 * @param wide      whether fullwidth-equivalent ({@link #isWide})
	 * @param trimOff   text-spacing-trim: space-all
	 * @param hangEnd   hanging-punctuation: allow-end
	 * @param advance   advance of the line-end glyph (hanging amount)
	 * @param fontSize  font-size of the line-end run (trim amount = 0.5 em)
	 * @param overflow  excess over line width (lineAxis-maxLineAxis; call when positive)
	 */
	public static double endAllowance(final int codePoint, final boolean wide, final boolean trimOff,
			final boolean hangEnd, final double advance, final double fontSize, final double overflow) {
		final JapaneseSpacingClass cls = JapaneseSpacingClass.of(codePoint);
		// (1) Line-end trim: trim if reducing to halfwidth makes it fit (for middle dots, JIS X 4051 requires
		// "quarter-em space before a line-end middle dot, solid after," so only a quarter = 0.25 em)
		if (!trimOff) {
			final double trim = endTrim(codePoint, wide, fontSize);
			if (overflow <= trim) {
				return trim;
			}
		}
		// (2) Hanging: full stops/commas only, using the glyph's entire advance
		if (wide && hangEnd && cls == JapaneseSpacingClass.PUNCTUATION && overflow <= advance) {
			return advance;
		}
		return 0;
	}

	/**
	 * Amount removed to reduce fullwidth line-end punctuation to halfwidth.
	 * Trims half an em for closing brackets/full stops/commas, and a quarter for middle dots,
	 * following JLREQ line-end placement.
	 */
	public static double endTrim(final int codePoint, final boolean wide, final double fontSize) {
		if (!wide) {
			return 0;
		}
		final JapaneseSpacingClass cls = JapaneseSpacingClass.of(codePoint);
		if (cls == JapaneseSpacingClass.CLOSING || cls == JapaneseSpacingClass.PUNCTUATION) {
			return PAIR_TRIM * fontSize;
		}
		if (cls == JapaneseSpacingClass.MIDDLE_DOT) {
			return PAIR_TRIM / 2 * fontSize;
		}
		return 0;
	}

	/** Advance of JLREQ full stops/commas always hung by {@code force-end}. */
	public static double forceEndHang(final int codePoint, final double advance) {
		return JapaneseSpacingClass.of(codePoint) == JapaneseSpacingClass.PUNCTUATION ? advance : 0;
	}

	/**
	 * Wide check (metrics conversion: 750/1000 font units ⇔ 0.75 × font-size).
	 * For horizontal writing, reads advance with {@code getAdvance}
	 * (including GPOS advance adjustments such as {@code palt} in font-feature-settings).
	 * {@code getWidth} reads only hmtx, so it misclassifies "、" reduced to halfwidth by palt as
	 * fullwidth, applying fixed half-em trimming and spacing assumptions (2026-09-14).
	 */
	public static boolean isWide(final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics, final int gid,
			final double fontSize) {
		return metrics.getAdvance(gid) > fontSize * 0.75;
	}

	/**
	 * Wide check based on inline advance in the layout direction. Horizontal writing continues to
	 * use horizontal width; vertical writing uses the glyph's vertical advance after GSUB vert.
	 *
	 * @param direction run's layout direction
	 */
	public static boolean isWide(final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics, final int gid,
			final double fontSize, final net.zamasoft.pdfg2d.gc.font.FontStyle.Direction direction) {
		if (direction == net.zamasoft.pdfg2d.gc.font.FontStyle.Direction.TB) {
			return metrics.getAdvance(gid) > fontSize * 0.75;
		}
		return isWide(metrics, gid, fontSize);
	}

	/**
	 * Applies punctuation trimming through xadvance to all adjacent pairs in an assembled run.
	 * T1a: replacement for trimming removed from the font layer, for custom appendGlyph loops
	 * (RubyUnitBox, FootnoteLabelImage, etc.). Skips pairs with nonzero GPOS kerning,
	 * preserving the original priority.
	 */
	public static void applyRunTrims(final net.zamasoft.pdfg2d.gc.text.TextImpl text) {
		final int glyphCount = text.getGlyphCount();
		if (glyphCount < 2) {
			return;
		}
		final FontStyle style = text.getFontStyle();
		final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics = text.getFontMetrics();
		final double fontSize = text.getFontStyle().getSize();
		final char[] chars = text.getChars();
		final byte[] clusterLengths = text.getClusterLengths();
		final int[] gids = text.getGlyphIds();
		int charIndex = clusterLengths[0];
		int prevCp = Character.codePointBefore(chars, charIndex);
		for (int i = 1; i < glyphCount; ++i) {
			final int cp = Character.codePointAt(chars, charIndex);
			if (metrics.getKerning(gids[i - 1], gids[i]) == 0) {
				final double trim = cappedPairTrim(prevCp, metrics, gids[i - 1], fontSize, style,
						cp, metrics, gids[i], fontSize, style);
				if (trim > 0) {
					// xadvance[i] = space before glyph i (negative = trim)
					text.addXAdvance(i, -trim);
				}
			}
			charIndex += clusterLengths[i];
			prevCp = Character.codePointBefore(chars, charIndex);
		}
	}
}
