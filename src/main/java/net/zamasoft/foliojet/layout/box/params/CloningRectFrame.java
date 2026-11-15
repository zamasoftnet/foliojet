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

	/**
	 * The cut sides where this fragment goes on into the next fragment of the same box ({@link #joinedAt}); they are
	 * not in {@link #paintedBorder}.
	 */
	private final boolean joinedTop, joinedRight, joinedBottom, joinedLeft;

	CloningRectFrame(final RectFrame frame, final RectBorder paintedBorder) {
		this(frame, paintedBorder, false, false, false, false);
	}

	private CloningRectFrame(final RectFrame frame, final RectBorder paintedBorder, final boolean joinedTop,
			final boolean joinedRight, final boolean joinedBottom, final boolean joinedLeft) {
		super(frame.margin, frame.border, frame.background, frame.padding, frame.shadows, frame.outline);
		this.paintedBorder = paintedBorder;
		this.joinedTop = joinedTop;
		this.joinedRight = joinedRight;
		this.joinedBottom = joinedBottom;
		this.joinedLeft = joinedLeft;
	}

	@Override
	public RectFrame cloningAtBreaks() {
		return this;
	}

	@Override
	public RectFrame cutAtBreak(final boolean top, final boolean right, final boolean bottom, final boolean left) {
		// A side joined before stays out of the painted border: a new cut elsewhere does not bring it back
		return new CloningRectFrame(this.cut(top, right, bottom, left),
				this.paintedBorder != null ? this.paintedBorder : this.border, this.joinedTop, this.joinedRight,
				this.joinedBottom, this.joinedLeft);
	}

	@Override
	public RectFrame joinedAt(final boolean top, final boolean right, final boolean bottom, final boolean left) {
		if (this.paintedBorder == null) {
			return this;
		}
		return new CloningRectFrame(this, this.paintedBorder.cut(!top, !right, !bottom, !left), this.joinedTop || top,
				this.joinedRight || right, this.joinedBottom || bottom, this.joinedLeft || left);
	}

	@Override
	public boolean isJoined(final boolean top, final boolean right, final boolean bottom, final boolean left) {
		return top && this.joinedTop || right && this.joinedRight || bottom && this.joinedBottom
				|| left && this.joinedLeft;
	}

	@Override
	public RectBorder paintedBorder() {
		return this.paintedBorder;
	}
}
