package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.ParamsType;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.draw.AbsoluteRectFrameDrawable;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * ブロックボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractBlockBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public abstract class AbstractBlockBox extends AbstractContainerBox {
	private static final boolean DEBUG = false;

	protected final BlockParams params;

	public AbstractBlockBox(final BlockParams params) {
		super(params.getType() == ParamsType.TABLE ? Dimension.AUTO_DIMENSION : params.size,
				params.getType() == ParamsType.TABLE ? Dimension.ZERO_DIMENSION : params.minSize, new FlowContainer());
		this.params = params;
		final RectFrame frame;
		if (params.getType() == ParamsType.TABLE) {
			frame = RectFrame.NULL_FRAME;
		} else {
			frame = params.frame;
		}
		this.frame = new AbsoluteRectFrame(frame);
		assert this.params.fontStyle != null;
	}

	protected AbstractBlockBox(BlockParams params, Dimension size, Dimension minSize, AbsoluteRectFrame frame,
			Container container) {
		super(size, minSize, container);
		this.params = params;
		this.frame = frame;
		assert this.params.fontStyle != null;
	}

	public BoxType getType() {
		return BoxType.BLOCK;
	}

	public Params getParams() {
		return this.params;
	}

	public BlockParams getBlockParams() {
		return this.params;
	}

	public void firstPassLayout(AbstractContainerBox containerBox) {
		BlockParams containerParams = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();

		//
		// ■ パディングの計算
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ マージンの計算
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		//
		// ■ 幅と高さの計算
		//
		switch (containerParams.flow) {
		case WritingMode.TB:
			// 横書き
			this.width = LayoutUtils.computeDimensionWidth(this.size, lineSize);
			if (LayoutUtils.isNone(this.width)) {
				this.width = 0;
			}
			double maxWidth = LayoutUtils.computeDimensionWidth(params.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxWidth)) {
				this.width = Math.min(this.width, maxWidth);
			}
			double minWidth = LayoutUtils.computeDimensionWidth(this.minSize, lineSize);
			this.width = Math.max(this.width, minWidth);
			break;
		case WritingMode.RL:
		case WritingMode.LR:
			// 縦書き
			this.height = LayoutUtils.computeDimensionHeight(this.size, lineSize);
			if (LayoutUtils.isNone(this.height)) {
				this.height = 0;
			}
			double maxHeight = LayoutUtils.computeDimensionWidth(params.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxHeight)) {
				this.height = Math.min(this.height, maxHeight);
			}
			double minHeight = LayoutUtils.computeDimensionWidth(this.minSize, lineSize);
			this.height = Math.max(this.height, minHeight);
			break;
		default:
			throw new IllegalStateException();
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		// SPEC CSS 2.1 9.9.1 #1
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		if (this.params.opacity != 0f && this.frame.isVisible()) {
			final Shape textClip;
			if (this.getBlockParams().frame.background.getBackgroundClip() == Background.TEXT) {
				final GeneralPath path = new GeneralPath();
				this.textShape(pageBox, path, transform, x, y);
				textClip = path;
			}
			else {
				textClip = null;
			}
			final Drawable drawable = new AbsoluteRectFrameDrawable(pageBox, clip, this.params.opacity, transform, this.frame,
					this.getWidth(), this.getHeight(), textClip);
			drawer.visitDrawable(drawable, x, y);
		}

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		this.container.drawFlowFrames(pageBox, drawer, clip, transform, x, y);
	}
	

	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		x += this.offsetX;
		y += this.offsetY;
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);

		transform = this.transform(transform, x, y);

		visitor.visitBox(transform, this, drawer, x, y);

		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(0, 0, 1));
			drawer.visitDrawable(drawable, x, y);
		}

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);

		final boolean contextBox = this.isContextBox();
		if (contextBox) {
			contextX = x - this.frame.padding.left;
			contextY = y - this.frame.padding.top;
		}

		// Tagged PDF: open a structure element for a mappable HTML block so the
		// content drawn inside attaches to it. Zero-size markers, no-op when
		// untagged or non-PDF; deduplicated per element by PageBox.
		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);

		this.container.drawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
		this.container.drawFlows(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
		if (!contextBox) {
			clip = null;
		}
		this.container.drawAbsolutes(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);

		pageBox.endStruct(drawer, this.params.element, structCount, x, y);
	}

	protected abstract AbstractBlockBox splitPage(Dimension nextSize, Dimension nextMinSize,
			AbsoluteRectFrame nextFrame, Container container);

	protected final AbstractContainerBox splitPage(final Container container, double pageLimit, final byte flags) {
		if (pageLimit < 0) {
			pageLimit = 0;
		}
		final AbsoluteRectFrame prevFrame, nextFrame;
		final AbstractBlockBox nextBlock;
		if (this.params.flow.isVertical()) {
			// 縦書き
			if ((flags & IPageBreakableBox.FLAGS_COLUMN) != 0) {
				// 複数カラムの場合は境界を残し、高さを内容に合わせる
				prevFrame = nextFrame = this.frame;
				pageLimit = Math.max(pageLimit, this.container.getContentSize());
			} else {
				prevFrame = this.frame.cut(true, true, true, false);
				nextFrame = this.frame.cut(true, false, true, true);
			}
			final Dimension nextSize, nextMinSize;
			if (this.isSpecifiedPageSize()) {
				// 幅指定
				final double width = Math.max(0, this.width - pageLimit);
				nextSize = Dimension.create(width, this.size.getHeight(), LengthType.ABSOLUTE,
						this.size.getHeightType());
			} else {
				nextSize = this.size;
			}

			if (this.minSize.getWidthType() != LengthType.AUTO) {
				// 幅指定
				final double width = Math.max(0, Math.min(this.minSize.getWidth(), this.width) - pageLimit);
				nextMinSize = Dimension.create(width, this.minSize.getHeight(), LengthType.ABSOLUTE,
						this.minSize.getHeightType());
			} else {
				nextMinSize = this.minSize;
			}
			nextBlock = this.splitPage(nextSize, nextMinSize, nextFrame, container);
			nextBlock.height = this.height;
			this.width = pageLimit;
		} else {
			// 横書き
			if ((flags & IPageBreakableBox.FLAGS_COLUMN) != 0) {
				// 複数カラムの場合は境界を残し、高さを内容に合わせる
				prevFrame = nextFrame = this.frame;
				pageLimit = Math.max(pageLimit, this.container.getContentSize());
			} else {
				prevFrame = this.frame.cut(true, true, false, true);
				nextFrame = this.frame.cut(false, true, true, true);
			}

			final Dimension nextSize, nextMinSize;
			if (this.isSpecifiedPageSize()) {
				// 指定高さを分割
				final double height = Math.max(0, this.height - pageLimit);
				nextSize = Dimension.create(this.size.getWidth(), height, this.size.getWidthType(),
						LengthType.ABSOLUTE);
			} else {
				nextSize = this.size;
			}
			if (this.minSize.getHeightType() != LengthType.AUTO) {
				// 最小高さを分割
				final double height = Math.max(0, Math.min(this.minSize.getHeight(), this.height) - pageLimit);
				nextMinSize = Dimension.create(this.minSize.getWidth(), height, this.minSize.getWidthType(),
						LengthType.ABSOLUTE);
			} else {
				nextMinSize = this.minSize;
			}
			nextBlock = this.splitPage(nextSize, nextMinSize, nextFrame, container);
			nextBlock.width = this.width;
			this.height = pageLimit;
		}
		this.frame = prevFrame;
		return nextBlock;
	}
}
