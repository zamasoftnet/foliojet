package net.zamasoft.foliojet.layout.box.params;

/**
 * The frame of a box with {@code box-decoration-break: clone} (2026-11-15, publishing report TECH-20261115-001).
 *
 * <p>
 * A simplified clone: a fragment paints the box's whole border, the sides a break cut away inside the fragment's own
 * area ({@link #paintedBorder}); no padding is added and the layout is that of {@code slice}. A class of its own so
 * that the frames of all other boxes stay what they were.
 * </p>
 */
final class CloningRectFrame extends RectFrame {
	/** The box's whole border on a fragment that a break cut; null on the box before any cut. */
	private final RectBorder paintedBorder;

	CloningRectFrame(final RectFrame frame, final RectBorder paintedBorder) {
		super(frame.margin, frame.border, frame.background, frame.padding, frame.shadows, frame.outline);
		this.paintedBorder = paintedBorder;
	}

	@Override
	public RectFrame cloningAtBreaks() {
		return this;
	}

	@Override
	public RectFrame cutAtBreak(final boolean top, final boolean right, final boolean bottom, final boolean left) {
		return new CloningRectFrame(this.cut(top, right, bottom, left),
				this.paintedBorder != null ? this.paintedBorder : this.border);
	}

	@Override
	public RectBorder paintedBorder() {
		return this.paintedBorder;
	}
}
