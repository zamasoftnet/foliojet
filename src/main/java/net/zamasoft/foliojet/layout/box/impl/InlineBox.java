package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IInlineBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.draw.AbsoluteRectFrameDrawable;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;

public class InlineBox extends AbstractTextBox implements IInlineBox, INonReplacedBox {
	/**
	 * ボックスの外辺を灰色の枠で囲みます。
	 */
	private static final boolean DEBUG = false;

	protected final InlineParams params;

	protected final InlinePos pos;

	protected final AbsoluteRectFrame frame;

	protected final boolean cutHead;

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
		// アセントディセントの拡大
		if (this.params.flow.isVertical()) {
			// 縦書き(日本)
			ascent += this.frame.getFrameRight();
			descent += this.frame.getFrameLeft();
		} else {
			// 横書き
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
		// ■ パディングの計算
		//
		LayoutUtils.computePaddings(this.frame.padding, rframe.padding, 0);

		//
		// ■ マージンの計算
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, rframe.margin, 0);
	}

	public final void fixLineAxis(AbstractContainerBox containerBox) {
		final BlockParams params = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();
		RectFrame rframe = this.frame.frame;
		//
		// ■ パディングの計算
		//
		LayoutUtils.computePaddings(this.frame.padding, rframe.padding, lineSize);

		//
		// ■ マージンの計算
		//
		// ページ方向のマージンは適用しません
		if (params.flow.isVertical()) {
			// 縦書き
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
			// 横書き
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

	public final void finishLayout(IFramedBox containerBox) {
		InlinePos pos = this.getInlinePos();
		if (pos.offset != null) {
			//
			// ■ 相対配置の位置の計算
			//
			this.offsetX = LayoutUtils.computeOffsetX(pos.offset, containerBox);
			this.offsetY = LayoutUtils.computeOffsetY(pos.offset, containerBox);
		}
		super.finishLayout(containerBox);
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), GrayColor.create(.7f));
			drawer.visitDrawable(drawable, x, y);
		}
		x += this.offsetX;
		y += this.offsetY;

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity != 0) {
			if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
				Drawer newDrawer = new Drawer(this.params.zIndexValue);
				drawer.visitDrawer(newDrawer);
				drawer = newDrawer;
			}

			if (this.frame.isVisible()) {
				Drawable drawable = new AbsoluteRectFrameDrawable(pageBox, clip, this.params.opacity, transform,
						this.frame, this.getWidth(), this.getHeight(), null); // TODO textClip
				drawer.visitDrawable(drawable, x, y);
			}
			if (this.getTextParams().flow.isVertical()) {
				// 縦書き
				// 内容の上
				y += this.frame.getFrameTop();
				// ベースラインの計算に境界が含まれているので、左右の境界分ずらさない
			} else {
				// 横書き
				// 内容の左
				x += this.frame.getFrameLeft();
				// ベースラインの計算に境界が含まれているので、上下の境界分ずらさない
			}

			if (this.getInlinePos().offset != null) {
				contextX = x;
				contextY = y;
			}

			// 内部のテキスト・インラインを描画
			super.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
		}
	}

	public final InlineBox splitLine(boolean cut) {
		InlineBox newInline;
		InlineParams params = this.getInlineParams();
		if (cut) {
			RectFrame previousFrame;
			RectFrame nextFrame;
			if (params.flow.isVertical()) {
				// 縦書き
				previousFrame = this.frame.frame.cut(true, true, false, true);
				nextFrame = this.frame.frame.cut(false, true, true, true);
			} else {
				// 横書き
				previousFrame = this.frame.frame.cut(true, false, true, true);
				nextFrame = this.frame.frame.cut(true, true, true, false);
				this.frame.margin.right = 0;
			}
			this.frame.frame = previousFrame;
			newInline = new InlineBox(params, this.getInlinePos(), nextFrame, cut);
		} else {
			newInline = new InlineBox(params, this.getInlinePos());
		}
		return newInline;
	}

	public final void closeInline() {
		this.cutTail = false;
	}

	public final void restyle(GlyphHandler gh, boolean widow) {
		final InlineParams params = this.getInlineParams();
		if (!this.cutHead) {
			final InlineBox inlineBox = new InlineBox(params, this.getInlinePos());
			inlineBox.frame.margin = this.frame.margin;
			inlineBox.frame.padding = this.frame.padding;
			final InlineQuad quad = InlineQuad.createInlineBoxStartQuad(inlineBox);
			// System.err.println(quad + "A:" + params.augmentation);
			gh.control(quad);
		} else if (widow) {
			final RectFrame nextFrame;
			final AbsoluteInsets nextMargin;
			final AbsoluteInsets nextPadding;
			if (params.flow.isVertical()) {
				// 縦書き
				nextFrame = this.frame.frame.cut(false, true, true, true);
				nextMargin = this.frame.margin.cut(false, true, true, true);
				nextPadding = this.frame.padding.cut(false, true, true, true);
			} else {
				// 横書き
				nextFrame = params.frame.cut(true, true, true, false);
				nextMargin = this.frame.margin.cut(true, true, true, false);
				nextPadding = this.frame.padding.cut(true, true, true, false);
			}
			final InlineBox inlineBox = new InlineBox(params, this.getInlinePos(), nextFrame, true);
			inlineBox.frame.margin = nextMargin;
			inlineBox.frame.padding = nextPadding;
			final InlineQuad quad = InlineQuad.createInlineBoxStartQuad(inlineBox);
			// System.err.println(quad + "B:" + params.augmentation);
			gh.control(quad);
		}
		super.restyle(gh, widow);
		if (!this.cutTail) {
			final InlineQuad quad = InlineQuad.createInlineBoxEndQuad(this);
			// System.err.println(quad + ":" + params.augmentation);
			gh.control(quad);
		}
	}

	public String toString() {
		return "[InlineBox]" + super.toString() + "[/InlineBox]";
	}
}
