package net.zamasoft.foliojet.layout.part;


import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.util.BoxDecorationRenderer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.text.TextClip;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteRectFrame.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class AbsoluteRectFrame {
	public RectFrame frame;

	public AbsoluteInsets margin;

	public AbsoluteInsets padding;

	public AbsoluteRectFrame(RectFrame frame) {
		this(frame, new AbsoluteInsets(), new AbsoluteInsets());
	}

	protected AbsoluteRectFrame(RectFrame frame, AbsoluteInsets margin, AbsoluteInsets padding) {
		this.frame = frame;
		this.margin = margin;
		this.padding = padding;
	}

	public final double getFrameTop() {
		return this.margin.top + this.frame.border.getTop().width + this.padding.top;
	}

	public final double getFrameLeft() {
		return this.margin.left + this.frame.border.getLeft().width + this.padding.left;
	}

	public final double getFrameBottom() {
		return this.padding.bottom + this.frame.border.getBottom().width + this.margin.bottom;
	}

	public final double getFrameRight() {
		return this.padding.right + this.frame.border.getRight().width + this.margin.right;
	}

	public final double getFrameHeight() {
		return this.getFrameTop() + this.getFrameBottom();
	}

	public final double getFrameWidth() {
		return this.getFrameLeft() + this.getFrameRight();
	}

	/**
	 * Returns the frame width on the start side of the line direction
	 * (left in horizontal writing, top in vertical writing).
	 *
	 * @param flow writing direction that determines the axes
	 * @return frame width on the start side of the line direction
	 */
	public final double getFrameLineStart(WritingMode flow) {
		return flow.isVertical() ? this.getFrameTop() : this.getFrameLeft();
	}

	/**
	 * Returns the frame width on the end side of the line direction
	 * (right in horizontal writing, bottom in vertical writing).
	 *
	 * @param flow writing direction that determines the axes
	 * @return frame width on the end side of the line direction
	 */
	public final double getFrameLineEnd(WritingMode flow) {
		return flow.isVertical() ? this.getFrameBottom() : this.getFrameRight();
	}

	/**
	 * Returns the frame width on the start side of the page direction
	 * (top in horizontal writing, right in vertical-rl, left in vertical-lr).
	 * Page-axis positions run from the block start, and drawing puts a box's content inside its physical left and top
	 * frames, so in vertical-lr the start is the left frame (2026-10-26: this read the right frame in both vertical
	 * modes, which agreed with drawing only while the two frames were equal; a vertical-lr box cut at a page break
	 * lost its start frame on the first page and kept it on the next, where its text overlapped the next block).
	 *
	 * @param flow writing direction that determines the axes
	 * @return frame width on the start side of the page direction
	 */
	public final double getFramePageStart(WritingMode flow) {
		return switch (flow) {
		case TB -> this.getFrameTop();
		case RL -> this.getFrameRight();
		case LR -> this.getFrameLeft();
		};
	}

	/**
	 * Returns the frame width on the end side of the page direction
	 * (bottom in horizontal writing, left in vertical-rl, right in vertical-lr).
	 *
	 * @param flow writing direction that determines the axes
	 * @return frame width on the end side of the page direction
	 */
	public final double getFramePageEnd(WritingMode flow) {
		return switch (flow) {
		case TB -> this.getFrameBottom();
		case RL -> this.getFrameLeft();
		case LR -> this.getFrameRight();
		};
	}

	/**
	 * Returns the margin on the start side of the page direction (the side of {@link #getFramePageStart}).
	 *
	 * @param flow writing direction that determines the axes
	 * @return margin on the start side of the page direction
	 */
	public final double getMarginPageStart(WritingMode flow) {
		return switch (flow) {
		case TB -> this.margin.top;
		case RL -> this.margin.right;
		case LR -> this.margin.left;
		};
	}

	/**
	 * Returns the margin on the end side of the page direction (the side of {@link #getFramePageEnd}).
	 *
	 * @param flow writing direction that determines the axes
	 * @return margin on the end side of the page direction
	 */
	public final double getMarginPageEnd(WritingMode flow) {
		return switch (flow) {
		case TB -> this.margin.bottom;
		case RL -> this.margin.left;
		case LR -> this.margin.right;
		};
	}

	/**
	 * Whether the start side of the page direction has a border or padding, which keeps the box's start margin
	 * from collapsing with its first child's.
	 *
	 * @param flow writing direction that determines the axes
	 * @return whether the start side has a border or padding
	 */
	public final boolean isBorderedPageStart(WritingMode flow) {
		return switch (flow) {
		case TB -> this.padding.top > 0 || !this.frame.border.getTop().isNull();
		case RL -> this.padding.right > 0 || !this.frame.border.getRight().isNull();
		case LR -> this.padding.left > 0 || !this.frame.border.getLeft().isNull();
		};
	}

	/**
	 * Whether the end side of the page direction has a border or padding (see {@link #isBorderedPageStart}).
	 *
	 * @param flow writing direction that determines the axes
	 * @return whether the end side has a border or padding
	 */
	public final boolean isBorderedPageEnd(WritingMode flow) {
		return switch (flow) {
		case TB -> this.padding.bottom > 0 || !this.frame.border.getBottom().isNull();
		case RL -> this.padding.left > 0 || !this.frame.border.getLeft().isNull();
		case LR -> this.padding.right > 0 || !this.frame.border.getRight().isNull();
		};
	}

	/**
	 * Returns the total frame width in the line direction.
	 *
	 * @param flow writing direction that determines the axes
	 * @return frame width in the line direction
	 */
	public final double getFrameLineExtent(WritingMode flow) {
		return flow.isVertical() ? this.getFrameHeight() : this.getFrameWidth();
	}

	/**
	 * Returns the total frame width in the page direction.
	 *
	 * @param flow writing direction that determines the axes
	 * @return frame width in the page direction
	 */
	public final double getFramePageExtent(WritingMode flow) {
		return flow.isVertical() ? this.getFrameWidth() : this.getFrameHeight();
	}

	/**
	 * Returns the total border + padding width in the line direction.
	 *
	 * @param flow writing direction that determines the axes
	 * @return border + padding width in the line direction
	 */
	public final double getBorderLineExtent(WritingMode flow) {
		return flow.isVertical() ? this.getBorderHeight() : this.getBorderWidth();
	}

	/**
	 * Returns the total border + padding width in the page direction.
	 *
	 * @param flow writing direction that determines the axes
	 * @return border + padding width in the page direction
	 */
	public final double getBorderPageExtent(WritingMode flow) {
		return flow.isVertical() ? this.getBorderWidth() : this.getBorderHeight();
	}

	public final double getBorderHeight() {
		return this.frame.border.getTop().width + this.padding.top + this.frame.border.getBottom().width
				+ this.padding.bottom;
	}

	public final double getBorderWidth() {
		return this.frame.border.getLeft().width + this.padding.left + this.frame.border.getRight().width
				+ this.padding.right;
	}

	public boolean isVisible() {
		return this.frame.isVisible();
	}

	/**
	 * What a drawing of the frame paints (2026-10-09). A background clipped to the text is painted apart from
	 * the rest of the frame, with the box's content.
	 */
	public enum Part {
		/** The whole frame. */
		ALL,
		/** The frame without the background: shadows, border and outline. */
		DECORATIONS,
		/** The background alone. */
		BACKGROUND
	}

	/**
	 * Draws the frame, or part of it.
	 *
	 * @param textClip the text that clips the background ({@code background-clip: text}), or null
	 * @param part     what to draw
	 */
	public void draw(GC gc, double x, double y, double width, double height, TextClip textClip, Part part)
			throws GraphicsException {
		assert !LayoutUtils.isNone(x) : "Undefined x";
		assert !LayoutUtils.isNone(y) : "Undefined y";
		assert !LayoutUtils.isNone(width) : "Undefined width";
		assert !LayoutUtils.isNone(height) : "Undefined height";
		x += this.margin.left;
		y += this.margin.top;
		width -= this.margin.getFrameWidth();
		height -= this.margin.getFrameHeight();
		// Painting order (CSS Backgrounds 3 §7.1 / CSS UI 3 §4, 2026-08-29):
		// Outer shadows are below the background; inner shadows are above it and below borders;
		// outlines are above borders. Outlines should be above content, but this frame drawable
		// draws them just after borders (negative offsets overlapping content place them below it).
		if (part != Part.BACKGROUND) {
			BoxDecorationRenderer.drawOuterShadows(gc, this.frame, x, y, width, height);
		}
		if (part != Part.DECORATIONS) {
			this.frame.background.draw(gc, x, y, width, height, this.frame.border, this.frame.padding, textClip);
		}
		if (part != Part.BACKGROUND) {
			BoxDecorationRenderer.drawInsetShadows(gc, this.frame, x, y, width, height);
			this.frame.border.draw(gc, x, y, width, height);
			BoxDecorationRenderer.drawOutline(gc, this.frame, x, y, width, height);
		}
	}

	public AbsoluteRectFrame cut(boolean top, boolean right, boolean bottom, boolean left) {
		return new AbsoluteRectFrame(this.frame.cut(top, right, bottom, left),
				this.margin.cut(top, right, bottom, left), this.padding.cut(top, right, bottom, left));
	}

	/**
	 * This frame without its edge on the end side of the page direction: the part of a box before a break.
	 *
	 * @param flow writing direction that determines the axes
	 * @return the frame without the page-end edge
	 */
	public AbsoluteRectFrame cutPageEnd(WritingMode flow) {
		return switch (flow) {
		case TB -> this.cut(true, true, false, true);
		case RL -> this.cut(true, true, true, false);
		case LR -> this.cut(true, false, true, true);
		};
	}

	/**
	 * This frame without its edge on the start side of the page direction: the continuation of a box after a break.
	 *
	 * @param flow writing direction that determines the axes
	 * @return the frame without the page-start edge
	 */
	public AbsoluteRectFrame cutPageStart(WritingMode flow) {
		return switch (flow) {
		case TB -> this.cut(false, true, true, true);
		case RL -> this.cut(true, false, true, true);
		case LR -> this.cut(true, true, true, false);
		};
	}

	public String toString() {
		return super.toString() + "[frame=" + this.frame + ",margin=" + this.margin + ",padding=" + this.padding + "]";
	}
}
