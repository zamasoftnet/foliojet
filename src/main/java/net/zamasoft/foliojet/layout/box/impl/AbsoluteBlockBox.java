package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.sizing.AbsoluteSizing;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectBorder;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * ブロックボックスの実装です
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class AbsoluteBlockBox extends AbstractBlockBox implements IAbsoluteBox {
	protected final AbsolutePos pos;

	public AbsoluteBlockBox(BlockParams params, AbsolutePos pos) {
		super(params);
		this.pos = pos;
	}

	protected AbsoluteBlockBox(BlockParams params, AbsolutePos pos, Dimension size, Dimension minSize,
			AbsoluteRectFrame frame, Container container) {
		super(params, size, minSize, frame, container);
		this.pos = pos;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbsolutePos getAbsolutePos() {
		return this.pos;
	}

	public final boolean isSpecifiedPageSize() {
		return true;
	}

	private TwoPassBlockBuilder builder;

	public final void prepareBind(TwoPassBlockBuilder builder) {
		this.builder = builder;
	}

	public final void shrinkToFit(IFramedBox containerBox, IntrinsicSizes sizes) {
		final double minLineAxis = sizes.minContent(), maxLineAxis = sizes.maxContent();
		double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();
		{
			double lineAxis;
			if (this.params.flow.isVertical()) {
				// 縦書き
				lineAxis = cHeight;
			} else {
				// 横書き
				lineAxis = cWidth;
			}

			//
			// ■ パディングの計算
			//
			LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineAxis);

			//
			// ■ マージンの計算
			//
			LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineAxis);
		}

		final Insets margin = this.frame.frame.margin;
		final AbsoluteInsets amargin = this.frame.margin;
		final AbsolutePos pos = this.getAbsolutePos();
		final WritingMode flow = this.params.flow;
		final boolean vertical = flow.isVertical();
		final double cLine = vertical ? cHeight : cWidth;
		//
		// ■ 絶対配置または固定配置の行方向幅の計算 (CSS2.1 10.3.7)
		//
		double size = LayoutUtils.computeDimensionLine(this.size, flow, cLine);
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(size)) {
			size -= this.frame.getBorderLineExtent(flow);
		}
		final AbsoluteSizing.Result result = AbsoluteSizing.resolve(new AbsoluteSizing.Input( //
				cLine, size, //
				LayoutUtils.computeDimensionLine(this.params.maxSize, flow, cLine), //
				LayoutUtils.computeDimensionLine(this.minSize, flow, cLine), //
				vertical ? LayoutUtils.computeInsetsTop(pos.location, cLine)
						: LayoutUtils.computeInsetsLeft(pos.location, cLine), //
				vertical ? LayoutUtils.computeInsetsBottom(pos.location, cLine)
						: LayoutUtils.computeInsetsRight(pos.location, cLine), //
				vertical ? amargin.top : amargin.left, //
				vertical ? amargin.bottom : amargin.right, //
				(vertical ? margin.getTopType() : margin.getLeftType()) == LengthType.AUTO, //
				(vertical ? margin.getBottomType() : margin.getRightType()) == LengthType.AUTO, //
				this.frame.getFrameLineExtent(flow), //
				minLineAxis, maxLineAxis, //
				vertical));
		// 交差軸(ページ方向)のマージン: auto は未解決(NONE)のままにする
		final double crossStart = (vertical ? margin.getLeftType() : margin.getTopType()) == LengthType.AUTO
				? LayoutUtils.NONE
				: (vertical ? amargin.left : amargin.top);
		final double crossEnd = (vertical ? margin.getRightType() : margin.getBottomType()) == LengthType.AUTO
				? LayoutUtils.NONE
				: (vertical ? amargin.right : amargin.bottom);
		assert !LayoutUtils.isNone(result.insetStart());
		if (vertical) {
			this.offsetY = result.insetStart();
			this.frame.margin.top = result.marginStart();
			this.frame.margin.bottom = result.marginEnd();
			this.frame.margin.left = crossStart;
			this.frame.margin.right = crossEnd;
			this.height = result.size();
			this.width = 0;
		} else {
			this.offsetX = result.insetStart();
			this.frame.margin.left = result.marginStart();
			this.frame.margin.right = result.marginEnd();
			this.frame.margin.top = crossStart;
			this.frame.margin.bottom = crossEnd;
			this.width = result.size();
			this.height = 0;
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final void finishLayout(final IFramedBox containerBox) {
		if (this.builder != null) {
			this.shrinkToFit(containerBox, this.builder.getIntrinsicSizes());
			final BlockBuilder absoluteBuilder = new BlockBuilder(this.builder.getPageContext(), this);
			this.builder.bind(absoluteBuilder);
			absoluteBuilder.close();
			this.builder = null;
		}

		double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();

		// 位置の計算
		final AbsolutePos pos = this.getAbsolutePos();
		//
		// ■ 絶対配置または固定配置のページ方向幅の計算 (CSS2.1 10.6.4)
		// 縦横の物理鏡像は AbsoluteSizing.resolvePage に統合(忠実移植)
		//
		final AbsoluteInsets margin = this.frame.margin;
		final AbsoluteInsets padding = this.frame.padding;
		final RectBorder border = this.frame.frame.border;
		final boolean vertical = this.params.flow.isVertical();
		final double cPage = vertical ? cWidth : cHeight;
		final AbsoluteSizing.PageResult result = AbsoluteSizing.resolvePage(new AbsoluteSizing.PageInput( //
				cPage, //
				vertical ? LayoutUtils.computeDimensionWidth(this.size, cWidth)
						: LayoutUtils.computeDimensionHeight(this.size, cHeight), //
				vertical ? LayoutUtils.computeDimensionWidth(this.params.maxSize, cWidth)
						: LayoutUtils.computeDimensionHeight(this.params.maxSize, cHeight), //
				vertical ? LayoutUtils.computeDimensionWidth(this.minSize, cWidth)
						: LayoutUtils.computeDimensionHeight(this.minSize, cHeight), //
				vertical ? LayoutUtils.computeInsetsLeft(pos.location, cWidth)
						: LayoutUtils.computeInsetsTop(pos.location, cHeight), //
				vertical ? LayoutUtils.computeInsetsRight(pos.location, cWidth)
						: LayoutUtils.computeInsetsBottom(pos.location, cHeight), //
				vertical ? margin.left : margin.top, //
				vertical ? margin.right : margin.bottom, //
				// 内容実寸(旧実装の式を忠実に維持: 縦書き側は width 相当)
				vertical ? this.getWidth() - this.frame.getFrameWidth() : this.height, //
				vertical ? border.getFrameWidth() + padding.getFrameWidth()
						: border.getFrameHeight() + padding.getFrameHeight()));

		double size = result.size();
		assert !LayoutUtils.isNone(result.insetStart());
		assert !LayoutUtils.isNone(result.marginStart());
		assert !LayoutUtils.isNone(result.marginEnd());
		assert !LayoutUtils.isNone(size);
		if (vertical) {
			assert !LayoutUtils.isNone(margin.top);
			assert !LayoutUtils.isNone(margin.bottom);
			this.offsetX = result.insetStart();
			this.frame.margin.left = result.marginStart();
			this.frame.margin.right = result.marginEnd();
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				size -= this.frame.getBorderWidth();
			}
			this.width = size;
		} else {
			assert !LayoutUtils.isNone(margin.right);
			assert !LayoutUtils.isNone(margin.left);
			this.offsetY = result.insetStart();
			this.frame.margin.top = result.marginStart();
			this.frame.margin.bottom = result.marginEnd();
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				size -= this.frame.getBorderHeight();
			}
			this.height = size;
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
		super.finishLayout(containerBox);
	}

	public final boolean isContextBox() {
		return true;
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		this.frames(pageBox, drawer, clip, transform, x, y);
		super.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
	}

	protected final AbstractBlockBox splitPage(Dimension nextSize, Dimension nextMinSize, AbsoluteRectFrame nextFrame,
			Container container) {
		return new AbsoluteBlockBox(params, this.getAbsolutePos(), nextSize, nextMinSize, nextFrame, container);
	}
}
