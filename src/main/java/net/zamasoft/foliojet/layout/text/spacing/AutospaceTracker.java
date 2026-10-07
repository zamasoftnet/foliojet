package net.zamasoft.foliojet.layout.text.spacing;

import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * Tracks adjacent pairs for {@code text-autospace} (Japanese spacing adjustment A2,
 * 2026-07-31; consult-codex-2026-07-31-text-spacing.txt A2). For each cluster, glyph consumers
 * (TextBuilder/TwoPassBlockBuilder) query the boundary gap with the preceding cluster
 * and bake the resolved gap into the preceding glyph's xadvance.
 *
 * <p>
 * Reset contract: reset on actual line breaks (newLine) and controls (TextControl such as
 * spaces, newlines, and inline replacements). Do not reset at break <b>opportunities</b>
 * (flush): a Japanese/Latin boundary is itself a break opportunity, so resetting there prevents
 * all gaps. Resetting on line breaks prevents gaps between pairs across lines
 * (consistent with JLREQ line-start/line-end processing).
 * </p>
 */
public final class AutospaceTracker {

	private byte flags;

	private TextImpl prevText;

	private int prevCodePoint = -1;

	private double prevFontSize;

	private int prevGid = -1;

	private boolean trimOff;

	/** Passes pair state to virtual measurement. Also reassigns the open run's identity to the clone. */
	public void copyFrom(final AutospaceTracker source, final TextImpl openText, final TextImpl copyText) {
		this.flags = source.flags;
		this.trimOff = source.trimOff;
		this.prevText = source.prevText == openText ? copyText : source.prevText;
		this.prevCodePoint = source.prevCodePoint;
		this.prevFontSize = source.prevFontSize;
		this.prevGid = source.prevGid;
	}

	/** Sets effective flags (follows params changes at inline boundaries). */
	public void setFlags(final byte flags) {
		this.flags = flags;
	}

	/** Sets whether punctuation trimming is disabled (text-spacing-trim: space-all; T1b). */
	public void setTrimOff(final boolean trimOff) {
		this.trimOff = trimOff;
	}

	/** Current effective flags (for recalculating reversal at split points). */
	public byte getFlags() {
		return this.flags;
	}

	/** Whether punctuation trimming is disabled (for recalculating reversal at split points). */
	public boolean isTrimOff() {
		return this.trimOff;
	}

	/**
	 * Punctuation trim with the preceding glyph (positive; 0 = none), T1a/E.
	 * Moved from the font layer. Skips pairs with nonzero GPOS in the same run.
	 * Since 2026-08-23, treats zero-width inline boundaries as the same JLREQ pair even when a
	 * decoration span or font change splits runs. Vertical writing runs are also classified by
	 * cluster Unicode code point; wide is determined using the vertical advance of the glyph
	 * after GSUB vert. xadvance is the logical inline advance, so it affects downward advance
	 * in vertical writing. Horizontal runs in tate-chu-yoko still use horizontal width.
	 *
	 * @param currentText run currently being appended to ({@code null} = first glyph of a new run)
	 */
	public double trimBefore(final char[] ch, final int coff, final int gid, final TextImpl currentText,
			final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics, final double fontSize,
			final net.zamasoft.pdfg2d.gc.font.FontStyle style) {
		if (this.trimOff || this.prevCodePoint < 0 || this.prevGid < 0 || this.prevText == null) {
			return 0;
		}
		// GPOS kerning is not defined across different runs/fonts. Continue to prioritize
		// GPOS only within the same run.
		if (this.prevText == currentText && metrics.getKerning(this.prevGid, gid) != 0) {
			return 0;
		}
		final int cp = Character.codePointAt(ch, coff);
		return JapaneseSpacingResolver.cappedPairTrim(this.prevCodePoint, this.prevText.getFontMetrics(),
				this.prevGid, this.prevFontSize, this.prevText.getFontStyle(), cp, metrics, gid, fontSize, style);
	}

	/** Gap (absolute amount) between the start of the current cluster and the preceding cluster. */
	public double gapBefore(final char[] ch, final int coff, final double fontSize) {
		if (this.flags == 0 || this.prevCodePoint < 0) {
			return 0;
		}
		final int cp = Character.codePointAt(ch, coff);
		// Add spacing between Japanese and Latin text after proportional punctuation (IPA P fonts, palt; 2026-09-14)
		final boolean proportionalPunctuation = this.prevText != null && TextAutospaceClasses
				.proportionalPunctuation(this.prevCodePoint, this.prevText.getFontMetrics(), this.prevGid,
						this.prevFontSize, this.prevText.getFontStyle().getDirection());
		final double gapEm = TextAutospaceClasses.gapEm(this.prevCodePoint, cp, this.flags, proportionalPunctuation);
		if (gapEm == 0) {
			return 0;
		}
		// Japanese-side run's font-size × 0.25 (ic approximation; see class Javadoc)
		return gapEm * (TextAutospaceClasses.ideographFirst(this.prevCodePoint) ? this.prevFontSize : fontSize);
	}

	/**
	 * Updates state after processing a cluster. Application contract: put the adjustment
	 * (gap − trim) in <b>the current glyph's xadvance</b>
	 * (= space before that glyph, as in CIDKeyedFont/ruby distribute).
	 */
	public void glyphAdded(final TextImpl text, final double fontSize, final char[] ch, final int coff,
			final byte clen, final int gid) {
		this.prevText = text;
		this.prevCodePoint = Character.codePointBefore(ch, coff + clen);
		this.prevFontSize = fontSize;
		this.prevGid = gid;
	}

	/** Discards pair state (line break or TextControl). */
	public void reset() {
		this.prevText = null;
		this.prevCodePoint = -1;
		this.prevGid = -1;
	}
}
