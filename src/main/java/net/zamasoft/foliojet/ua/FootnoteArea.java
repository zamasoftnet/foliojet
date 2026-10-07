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

	public static final FootnoteArea DEFAULT = new FootnoteArea(Position.BLOCK_END, null, null, 0, null);

	public final Position position;

	/** If null, follows the page's writing direction. */
	public final WritingMode flow;

	/** The band size in pt, including the gap. Null means auto. */
	public final Double height;

	/** The minimum band size in pt. */
	public final double minHeight;

	/** The separator specification (null means the UA's default line). */
	public final Separator separator;

	private FootnoteArea(final Position position, final WritingMode flow, final Double height, final double minHeight,
			final Separator separator) {
		this.position = java.util.Objects.requireNonNull(position);
		this.flow = flow;
		this.height = height;
		this.minHeight = minHeight;
		this.separator = separator;
	}

	public FootnoteArea withSeparator(final Separator separator) {
		return new FootnoteArea(this.position, this.flow, this.height, this.minHeight, separator);
	}

	public FootnoteArea withPosition(final Position position) {
		return this.position == position ? this : new FootnoteArea(position, this.flow, this.height, this.minHeight, this.separator);
	}

	public FootnoteArea withFlow(final WritingMode flow) {
		return this.flow == flow ? this : new FootnoteArea(this.position, flow, this.height, this.minHeight, this.separator);
	}

	public boolean isHeightFixed() {
		return this.height != null;
	}

	public FootnoteArea withHeight(final Double height) {
		if (height != null && (!Double.isFinite(height) || height < 0)) throw new IllegalArgumentException();
		return java.util.Objects.equals(this.height, height) ? this
				: new FootnoteArea(this.position, this.flow, height, this.minHeight, this.separator);
	}

	public FootnoteArea withMinHeight(final double minHeight) {
		if (!Double.isFinite(minHeight) || minHeight < 0) throw new IllegalArgumentException();
		return this.minHeight == minHeight ? this : new FootnoteArea(this.position, this.flow, this.height, minHeight, this.separator);
	}
}
