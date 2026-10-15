package net.zamasoft.foliojet.ua;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

/** The single footnote area for a document. Independent of page names and page pseudo-classes. */
public final class FootnoteArea {
	public enum Position {
		BLOCK_END,

		/** A band at the bottom of the sheet. */
		BOTTOM,

		/** A band at the top of the sheet (headnotes, 2026-09-11). */
		TOP
	}

	/**
	 * Whether this placement reserves a band at the top or bottom of the sheet.
	 *
	 * <p>
	 * Unlike {@code BLOCK_END}, a band is reserved only once at the start of the page and does not
	 * create differences in column height. Both top and bottom bands reduce the type area in the
	 * inline direction (the sheet's vertical direction on a vertical-writing page), so their
	 * accounting follows mostly the same path.
	 * </p>
	 */
	public boolean isPageBand() {
		return this.position == Position.BOTTOM || this.position == Position.TOP;
	}

	/** Whether this is a top band (headnotes). */
	public boolean isHeadBand() {
		return this.position == Position.TOP;
	}

	/**
	 * The separator between body text and footnotes (2026-10-04, {@code border-top} on {@code @footnote};
	 * item ⑤ of TECH-20261003-004). If unspecified, this is {@code null} and the UA draws its default
	 * line (0.5 pt, black, 1/3 of the type area's inline dimension). If specified, the line spans the
	 * area's full width with the specified thickness and color. A thickness of 0 (none, etc.) draws no line.
	 */
	public record Separator(double thickness, net.zamasoft.pdfg2d.gc.paint.Color color) {
	}

	/** Thickness of the UA's default separator line in pt. */
	public static final double DEFAULT_SEPARATOR_THICKNESS = 0.5;

	/**
	 * The UA's gap between body text and notes in pt when neither {@code margin-top} nor {@code padding-top} is
	 * given; the separator sits at its center.
	 */
	public static final double DEFAULT_GAP = 6;

	public static final FootnoteArea DEFAULT = new FootnoteArea(Position.BLOCK_END, null, null, 0, null, null, null);

	public final Position position;

	/** If null, follows the page's writing direction. */
	public final WritingMode flow;

	/** The band size in pt, including the gap. Null means auto. */
	public final Double height;

	/** The minimum band size in pt. */
	public final double minHeight;

	/** The separator specification (null means the UA's default line). */
	public final Separator separator;

	/**
	 * {@code margin-top} and {@code padding-top} in pt (2026-10-15, TECH-20261015-006 ④). Null means unspecified.
	 * The area is a box whose border-top is the separator: the margin lies between body text and the line, the
	 * padding between the line and the notes.
	 */
	public final Double marginTop, paddingTop;

	private FootnoteArea(final Position position, final WritingMode flow, final Double height, final double minHeight,
			final Separator separator, final Double marginTop, final Double paddingTop) {
		this.position = java.util.Objects.requireNonNull(position);
		this.flow = flow;
		this.height = height;
		this.minHeight = minHeight;
		this.separator = separator;
		this.marginTop = marginTop;
		this.paddingTop = paddingTop;
	}

	public FootnoteArea withSeparator(final Separator separator) {
		return new FootnoteArea(this.position, this.flow, this.height, this.minHeight, separator, this.marginTop,
				this.paddingTop);
	}

	public FootnoteArea withPosition(final Position position) {
		return this.position == position ? this
				: new FootnoteArea(position, this.flow, this.height, this.minHeight, this.separator, this.marginTop,
						this.paddingTop);
	}

	public FootnoteArea withFlow(final WritingMode flow) {
		return this.flow == flow ? this
				: new FootnoteArea(this.position, flow, this.height, this.minHeight, this.separator, this.marginTop,
						this.paddingTop);
	}

	public boolean isHeightFixed() {
		return this.height != null;
	}

	public FootnoteArea withHeight(final Double height) {
		if (height != null && (!Double.isFinite(height) || height < 0)) throw new IllegalArgumentException();
		return java.util.Objects.equals(this.height, height) ? this
				: new FootnoteArea(this.position, this.flow, height, this.minHeight, this.separator, this.marginTop,
						this.paddingTop);
	}

	public FootnoteArea withMinHeight(final double minHeight) {
		if (!Double.isFinite(minHeight) || minHeight < 0) throw new IllegalArgumentException();
		return this.minHeight == minHeight ? this
				: new FootnoteArea(this.position, this.flow, this.height, minHeight, this.separator, this.marginTop,
						this.paddingTop);
	}

	public FootnoteArea withMarginTop(final double marginTop) {
		if (!Double.isFinite(marginTop) || marginTop < 0) throw new IllegalArgumentException();
		return new FootnoteArea(this.position, this.flow, this.height, this.minHeight, this.separator, marginTop,
				this.paddingTop);
	}

	public FootnoteArea withPaddingTop(final double paddingTop) {
		if (!Double.isFinite(paddingTop) || paddingTop < 0) throw new IllegalArgumentException();
		return new FootnoteArea(this.position, this.flow, this.height, this.minHeight, this.separator, this.marginTop,
				paddingTop);
	}

	private boolean isGapSpecified() {
		return this.marginTop != null || this.paddingTop != null;
	}

	private double separatorThickness() {
		return this.separator == null ? DEFAULT_SEPARATOR_THICKNESS : this.separator.thickness();
	}

	/**
	 * The gap between body text and notes in pt. Without {@code margin-top}/{@code padding-top} it is the UA's
	 * {@link #DEFAULT_GAP}; otherwise margin + separator thickness + padding, an unspecified side counting as 0
	 * (their initial value).
	 */
	public double gap() {
		if (!this.isGapSpecified()) {
			return DEFAULT_GAP;
		}
		return this.marginTopOrZero() + this.separatorThickness() + this.paddingTopOrZero();
	}

	/** Distance from the body-text side of the gap to the separator's center line in pt. */
	public double ruleFromBody() {
		return this.isGapSpecified() ? this.marginTopOrZero() + this.separatorThickness() / 2 : DEFAULT_GAP / 2;
	}

	/** Distance from the notes side of the gap to the separator's center line in pt. */
	public double ruleFromNotes() {
		return this.isGapSpecified() ? this.paddingTopOrZero() + this.separatorThickness() / 2 : DEFAULT_GAP / 2;
	}

	private double marginTopOrZero() {
		return this.marginTop == null ? 0 : this.marginTop;
	}

	private double paddingTopOrZero() {
		return this.paddingTop == null ? 0 : this.paddingTop;
	}
}
