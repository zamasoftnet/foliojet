package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IInlineBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.TextShapeSink;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.draw.AbsoluteRectFrameDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextClip;

public class InlineBox extends AbstractTextBox implements IInlineBox, INonReplacedBox {
	/**
	 * Draws a gray border around the box's outer edges.
	 */

	protected final InlineParams params;

	protected final InlinePos pos;

	protected final AbsoluteRectFrame frame;

	protected boolean cutHead;

	protected boolean cutTail = true;

	protected double offsetX, offsetY;

	public InlineBox(final InlineParams params, final InlinePos pos) {
		this(params, pos, params.frame, false);
	}

	private InlineBox(final InlineParams params, final InlinePos pos, final RectFrame frame, final boolean cut) {
		this.params = params;
		this.pos = pos;
		this.frame = new AbsoluteRectFrame(frame);
		this.cutHead = cut;
		assert params.fontStyle != null;
		assert params.lineBreakRules != null;
		assert params.fontManager != null;

	}

	public final BoxType getType() {
		return BoxType.INLINE;
	}

	public final Params getParams() {
		return this.params;
	}

	public final AbstractTextParams getTextParams() {
		return this.params;
	}

	public final InlineParams getInlineParams() {
		return this.params;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final InlinePos getInlinePos() {
		return this.pos;
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	/** Whether a bidi visual fragment can inherit the logical start edge. */
	public final boolean hasLineStartEdge() {
		return !this.cutHead;
	}

	/** Whether a bidi visual fragment can inherit the logical end edge. */
	public final boolean hasLineEndEdge() {
		return !this.cutTail;
	}

	/** Used only by bidi drawing fragments after determining whether they contain the logical start. */
	protected final void setFragmentCutHead(final boolean cutHead) {
		this.cutHead = cutHead;
	}

	/** Passes the effective text-decoration, including inherited values, to a bidi drawing fragment. */
	final void copyDecorationTo(final InlineBox target) {
		target.setDecoration(this.decoration);
	}

	/** Passes the relative-positioning offset after finishLayoutSelf to a bidi drawing fragment. */
	final void copyResolvedOffsetTo(final InlineBox target) {
		target.offsetX = this.offsetX;
		target.offsetY = this.offsetY;
	}

	public double getInnerWidth() {
		return this.getWidth() - this.getFrame().getFrameWidth();
	}

	public double getInnerHeight() {
		return this.getHeight() - this.getFrame().getFrameHeight();
	}

	public final boolean isContextBox() {
		return this.getInlinePos().offset != null;
	}

	public final void addAscentDescent(double ascent, double descent) {
		// Expand ascent and descent
		if (this.params.flow.isVertical()) {
			// Vertical writing (Japanese)
			if (this.params.writingModeVariant == WritingModeVariant.SIDEWAYS_CCW) {
				ascent += this.frame.getFrameLeft();
				descent += this.frame.getFrameRight();
			} else {
				ascent += this.frame.getFrameRight();
				descent += this.frame.getFrameLeft();
			}
		} else {
			// Horizontal writing
			ascent += this.frame.getFrameTop();
			descent += this.frame.getFrameBottom();
		}
		if (ascent > this.ascent) {
			this.ascent = ascent;
		}
		if (descent > this.descent) {
			this.descent = descent;
		}
		assert !LayoutUtils.isNone(this.ascent + this.descent);
	}

	public final void firstPassLayout(AbstractContainerBox cb) {
		RectFrame rframe = this.frame.frame;
		//
		// ■ Calculate padding
		//
		LayoutUtils.computePaddings(this.frame.padding, rframe.padding, 0);

		//
		// ■ Calculate margins
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, rframe.margin, 0);
	}

	public final void fixLineAxis(AbstractContainerBox containerBox) {
		this.fixLineAxis(containerBox.getBlockParams().flow.isVertical(), containerBox.getLineSize());
	}

	/**
	 * Resolves line-axis margin/padding to absolute values from the current {@code frame.frame}.
	 * Separated into a form callable without a container so bidi visual fragments (2026-09-04)
	 * can recalculate from an uncut frame.
	 */
	public final void fixLineAxis(final boolean vertical, final double lineSize) {
		RectFrame rframe = this.frame.frame;
		//
		// ■ Calculate padding
		//
		LayoutUtils.computePaddings(this.frame.padding, rframe.padding, lineSize);

		//
		// ■ Calculate margins
		//
		// Do not apply page-axis margins.
		if (vertical) {
			// Vertical writing
			double top, bottom;
			switch (rframe.margin.getTopType()) {
			case ABSOLUTE:
				top = rframe.margin.getTop();
				break;
			case RELATIVE:
				top = rframe.margin.getTop() * lineSize;
				break;
			case MIXED:
				top = rframe.margin.getTop() + rframe.margin.getTopRatio() * lineSize;
				break;
			case AUTO:
				top = 0;
				break;
			default:
				throw new IllegalStateException();
			}

			switch (rframe.margin.getBottomType()) {
			case ABSOLUTE:
				bottom = rframe.margin.getBottom();
				break;
			case RELATIVE:
				bottom = rframe.margin.getBottom() * lineSize;
				break;
			case MIXED:
				bottom = rframe.margin.getBottom() + rframe.margin.getBottomRatio() * lineSize;
				break;
			case AUTO:
				bottom = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			this.frame.margin.top = top;
			this.frame.margin.right = 0;
			this.frame.margin.bottom = bottom;
			this.frame.margin.left = 0;
		} else {
			// Horizontal writing
			double left, right;
			switch (rframe.margin.getLeftType()) {
			case ABSOLUTE:
				left = rframe.margin.getLeft();
				break;
			case RELATIVE:
				left = rframe.margin.getLeft() * lineSize;
				break;
			case MIXED:
				left = rframe.margin.getLeft() + rframe.margin.getLeftRatio() * lineSize;
				break;
			case AUTO:
				left = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (rframe.margin.getRightType()) {
			case ABSOLUTE:
				right = rframe.margin.getRight();
				break;
			case RELATIVE:
				right = rframe.margin.getRight() * lineSize;
				break;
			case MIXED:
				right = rframe.margin.getRight() + rframe.margin.getRightRatio() * lineSize;
				break;
			case AUTO:
				right = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			this.frame.margin.top = 0;
			this.frame.margin.right = right;
			this.frame.margin.bottom = 0;
			this.frame.margin.left = left;
		}
	}

	public final void finishLayoutSelf(IFramedBox containerBox) {
		InlinePos pos = this.getInlinePos();
		if (pos.offset != null) {
			//
			// ■ Calculate the relative-positioning offset
			//
			this.offsetX = LayoutUtils.computeOffsetX(pos.offset, containerBox);
			this.offsetY = LayoutUtils.computeOffsetY(pos.offset, containerBox);
		}
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity != 0) {
			if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
				final Drawer newDrawer = new Drawer(this.params, transform);
				drawer.visitDrawer(newDrawer);
				drawer = newDrawer;
			}

			if (this.frame.isVisible()) {
				final TextClip textClip = this.frame.frame.background.getBackgroundClip() == Background.TEXT
						? this.textClip(pageBox, x, y)
						: null;
				Drawable drawable = new AbsoluteRectFrameDrawable(pageBox, clip, this.params.opacity, transform,
						this.frame, this.getWidth(), this.getHeight(), textClip).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
				drawer.visitDrawable(drawable, x, y);
			}
			if (this.getTextParams().flow.isVertical()) {
				// Vertical writing
				// Top of the content
				y += this.frame.getFrameTop();
				// Baseline calculation includes borders, so do not offset by the left and right borders.
			} else {
				// Horizontal writing
				// Left of the content
				x += this.frame.getFrameLeft();
				// Baseline calculation includes borders, so do not offset by the top and bottom borders.
			}

			if (this.getInlinePos().offset != null) {
				contextX = x;
				contextY = y;
			}

			// Draw the internal text and inlines
			super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
		}
	}

	/**
	 * Walks the text at the content origin that {@link #pushDrawSteps} draws it from: shifted by the relative
	 * offset and by the frame at the inline start (2026-10-09; the walk took the frame's origin, so text in an
	 * inline with padding, a border or a relative offset was clipped out of place).
	 */
	@Override
	public void pushTextShapeSteps(PageBox pageBox, TextShapeSink sink, AffineTransform transform, double x,
			double y, java.util.Deque<TextShapeStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;
		if (this.getTextParams().flow.isVertical()) {
			y += this.frame.getFrameTop();
		} else {
			x += this.frame.getFrameLeft();
		}
		super.pushTextShapeSteps(pageBox, sink, transform, x, y, worklist);
	}

	/**
	 * The text of this inline for {@code background-clip: text}, in the space its frame is drawn in ({@code x},
	 * {@code y}: the frame's origin after the relative offset, as in {@link #pushDrawSteps}).
	 */
	private TextClip textClip(final PageBox pageBox, double x, double y) {
		if (this.getTextParams().flow.isVertical()) {
			y += this.frame.getFrameTop();
		} else {
			x += this.frame.getFrameLeft();
		}
		final TextClip textClip = new TextClip();
		final java.util.Deque<TextShapeStep> worklist = new java.util.ArrayDeque<>();
		super.pushTextShapeSteps(pageBox, TextShapeSink.backgroundClip(pageBox, textClip), new AffineTransform(), x,
				y, worklist);
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
		return textClip;
	}

	public final InlineBox splitLine(boolean cut) {
		InlineBox newInline;
		InlineParams params = this.getInlineParams();
		if (cut) {
			RectFrame previousFrame;
			RectFrame nextFrame;
			if (params.flow.isVertical()) {
				// Vertical writing
				if (params.writingModeVariant != WritingModeVariant.NORMAL
						&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
						params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP) {
					previousFrame = this.frame.frame.cut(false, true, true, true);
					nextFrame = this.frame.frame.cut(true, true, false, true);
				} else {
					previousFrame = this.frame.frame.cut(true, true, false, true);
					nextFrame = this.frame.frame.cut(false, true, true, true);
				}
			} else {
				// Horizontal writing
				previousFrame = this.frame.frame.cut(true, false, true, true);
				nextFrame = this.frame.frame.cut(true, true, true, false);
				this.frame.margin.right = 0;
			}
			this.frame.frame = previousFrame;
			newInline = new InlineBox(params, this.getInlinePos(), nextFrame, cut);
		} else {
			newInline = new InlineBox(params, this.getInlinePos());
		}
		newInline.setAssignmentAnchor(this.getAssignmentAnchor());
		return newInline;
	}

	public final void closeInline() {
		this.cutTail = false;
	}

	public final void restyle(GlyphHandler gh, boolean widow) {
		final InlineParams params = this.getInlineParams();
		if (!this.cutHead) {
			final InlineBox inlineBox = new InlineBox(params, this.getInlinePos());
			inlineBox.setAssignmentAnchor(this.getAssignmentAnchor());
			inlineBox.frame.margin = this.frame.margin;
			inlineBox.frame.padding = this.frame.padding;
			final InlineQuad quad = InlineQuad.createInlineBoxStartQuad(inlineBox);
			gh.control(quad);
		} else if (widow) {
			final RectFrame nextFrame;
			final AbsoluteInsets nextMargin;
			final AbsoluteInsets nextPadding;
			if (params.flow.isVertical()) {
				// Vertical writing
				if (params.writingModeVariant != WritingModeVariant.NORMAL
						&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
						params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP) {
					nextFrame = this.frame.frame.cut(true, true, false, true);
					nextMargin = this.frame.margin.cut(true, true, false, true);
					nextPadding = this.frame.padding.cut(true, true, false, true);
				} else {
					nextFrame = this.frame.frame.cut(false, true, true, true);
					nextMargin = this.frame.margin.cut(false, true, true, true);
					nextPadding = this.frame.padding.cut(false, true, true, true);
				}
			} else {
				// Horizontal writing
				nextFrame = params.frame.cut(true, true, true, false);
				nextMargin = this.frame.margin.cut(true, true, true, false);
				nextPadding = this.frame.padding.cut(true, true, true, false);
			}
			final InlineBox inlineBox = new InlineBox(params, this.getInlinePos(), nextFrame, true);
			inlineBox.setAssignmentAnchor(this.getAssignmentAnchor());
			inlineBox.frame.margin = nextMargin;
			inlineBox.frame.padding = nextPadding;
			final InlineQuad quad = InlineQuad.createInlineBoxStartQuad(inlineBox);
			gh.control(quad);
		}
		super.restyle(gh, widow);
		if (!this.cutTail) {
			final InlineQuad quad = InlineQuad.createInlineBoxEndQuad(this);
			gh.control(quad);
		}
	}

	public String toString() {
		return "[InlineBox]" + super.toString() + "[/InlineBox]";
	}
}
