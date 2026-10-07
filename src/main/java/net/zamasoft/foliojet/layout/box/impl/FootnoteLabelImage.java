package net.zamasoft.foliojet.layout.box.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;

/**
 * An unresolved footnote-number label (footnotes F5, 2026-07-31 --
 * consult-codex-2026-07-31-footnote-f5.txt). Instead of embedding the number part of
 * {@code ::footnote-call}/{@code ::footnote-marker} as text, keeps it as an atomic inline
 * replaced element (an {@code InlineReplacedBox} holding this Image) with a {@code footnoteId}.
 * At page finalization, {@code RootBuilder} assigns numbers starting at 1 for each
 * "page where the call remains" and invokes {@link #resolve}. Glyphs are created only at drawing time.
 *
 * <p>
 * <b>Fixed slot</b>: layout width does not depend on the number of digits
 * (one digit for calls; two digits, right-aligned, for markers; based on the maximum advance of digits 0-9).
 * Because the number is not a layout input, no fixed-point calculation with widows/avoid or
 * footnote reservation occurs. Not relaying out to the exact number width required by CSS
 * is a documented intentional deviation from the specification.
 * </p>
 *
 * <p>
 * Implements {@link ReplacedBoxImage} because it has mutable state (the resolved number).
 * The freeze path ({@code ReplacedParamsTemplate}) freezes an independent {@link #duplicate}
 * at recording time and supplies another duplicate for each source replay, so resolution state
 * is not shared between live processing and replay. Replayed duplicates return to the unresolved
 * state, and traversal of the finalized tree ({@code RootBuilder}) resolves them again.
 * </p>
 */
public final class FootnoteLabelImage
		implements net.zamasoft.pdfg2d.gc.image.Image, ReplacedBoxImage,
		net.zamasoft.foliojet.layout.box.content.BaselineImage {

	private static final java.util.logging.Logger LOG = java.util.logging.Logger
			.getLogger(FootnoteLabelImage.class.getName());

	private final long footnoteId;

	/** Markers use a two-digit, right-aligned slot; calls use one digit, left-aligned (overflow extends right). */
	private final boolean marker;

	private final String prefix, suffix;

	private final FontStyle fontStyle;

	private final FontManager fontManager;

	/** Slot width for one digit (maximum advance of 0-9). */
	private final double digitAdvance;

	private final double prefixAdvance, suffixAdvance;

	private final double ascent, descent;

	/** Number assigned at page finalization. -1 means unresolved. */
	private int resolvedNumber = -1;

	public FootnoteLabelImage(final long footnoteId, final boolean marker, final String prefix, final String suffix,
			final FontStyle fontStyle, final FontManager fontManager) {
		this.footnoteId = footnoteId;
		this.marker = marker;
		this.prefix = prefix;
		this.suffix = suffix;
		this.fontStyle = fontStyle;
		this.fontManager = fontManager;
		final FontListMetrics flm = fontManager.getFontListMetrics(fontStyle);
		final FontMetrics fm = flm.getFontMetrics(0);
		this.ascent = fm.getAscent();
		this.descent = fm.getDescent();
		double digit = 0;
		for (char c = '0'; c <= '9'; ++c) {
			digit = Math.max(digit, this.measure(String.valueOf(c)));
		}
		this.digitAdvance = digit;
		this.prefixAdvance = this.measure(prefix);
		this.suffixAdvance = this.measure(suffix);
	}

	private FootnoteLabelImage(final FootnoteLabelImage source) {
		this.footnoteId = source.footnoteId;
		this.marker = source.marker;
		this.prefix = source.prefix;
		this.suffix = source.suffix;
		this.fontStyle = source.fontStyle;
		this.fontManager = source.fontManager;
		this.digitAdvance = source.digitAdvance;
		this.prefixAdvance = source.prefixAdvance;
		this.suffixAdvance = source.suffixAdvance;
		this.ascent = source.ascent;
		this.descent = source.descent;
		// Do not copy resolution state: traversal of the finalized tree resolves replayed duplicates again.
	}

	public long getFootnoteId() {
		return this.footnoteId;
	}

	public boolean isMarker() {
		return this.marker;
	}

	/**
	 * Assigns the number at page finalization (from {@code RootBuilder}).
	 *
	 * @param number page-local footnote number (starting at 1)
	 */
	public void resolve(final int number) {
		this.resolvedNumber = number;
	}

	/** Slot width: number slot (one digit for calls, two for markers) + leading/trailing literals. */
	@Override
	public double getWidth() {
		return this.prefixAdvance + this.digitAdvance * (this.marker ? 2 : 1) + this.suffixAdvance;
	}

	@Override
	public double getHeight() {
		return this.ascent + this.descent;
	}

	/**
	 * The number is text, so align its text baseline with the line baseline
	 * (2026-10-04, TECH-20261003-004 item ⑥). Placing its bottom on the baseline as an image
	 * raised it by the text descent, on top of {@code vertical-align: super}, so the default
	 * {@code ::footnote-call} floated too high.
	 */
	@Override
	public double getDescent() {
		return this.descent;
	}

	@Override
	public String getAltString() {
		return this.resolvedNumber < 0 ? "" : this.prefix + this.resolvedNumber + this.suffix;
	}

	/** Warn about missing numbering only once (avoid flooding the sweep output). */
	private static final java.util.concurrent.atomic.AtomicBoolean WARNED_UNRESOLVED =
			new java.util.concurrent.atomic.AtomicBoolean();

	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		int number = this.resolvedNumber;
		if (number < 0) {
			// **Do not fail conversion even if numbering was missed** (2026-08-02; absolute requirement:
			// no crashes or conversion failures). The call scan traverses only type-area flows,
			// floats, lines, and inlines, so calls inside table cells or absolutely positioned boxes
			// are not numbered (remaining footnote work in PLAN:
			// call scans in table and absolute-positioning contexts). Removing the number would lose content,
			// so draw a **sequential number in document order** as a fallback and warn only once.
			number = (int) (this.footnoteId + 1);
			if (WARNED_UNRESOLVED.compareAndSet(false, true)) {
				LOG.warning("footnote label was not numbered by the page scan"
						+ " (a call inside a table cell or an absolutely positioned box);"
						+ " falling back to the document order number: id=" + this.footnoteId);
			}
		}
		final String text = this.prefix + number + this.suffix;
		final TextImpl[] runs = this.shape(text);
		double advance = 0;
		for (final TextImpl run : runs) {
			advance += run.getAdvance();
		}
		// Markers are right-aligned (keeping the body text start stable even with one digit); calls are left-aligned,
		// with overflow (10 and above) extending toward inline-end.
		double x = this.marker ? this.getWidth() - advance : 0;
		final double y = this.ascent;
		for (final TextImpl run : runs) {
			gc.drawText(run, x, y);
			x += run.getAdvance();
		}
	}

	@Override
	public void setReplacedBox(final AbstractReplacedBox box, final double width, final double height) {
		// No back-reference is needed (size is a fixed slot).
	}

	@Override
	public net.zamasoft.pdfg2d.gc.image.Image duplicate() {
		return new FootnoteLabelImage(this);
	}

	private double measure(final String text) {
		double advance = 0;
		for (final TextImpl run : this.shape(text)) {
			advance += run.getAdvance();
		}
		return advance;
	}

	/** Self-contained shaping (unified on RunCollector+TrimmedRuns on 2026-08-01). */
	private TextImpl[] shape(final String text) {
		return net.zamasoft.foliojet.layout.text.spacing.TrimmedRuns.shape(this.fontManager, this.fontStyle, text, -1,
				false);
	}

}
