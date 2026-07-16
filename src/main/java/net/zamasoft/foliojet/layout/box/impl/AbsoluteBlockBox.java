package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.sizing.Sizing;

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

		Insets margin = this.frame.frame.margin;
		AbsoluteInsets amargin = this.frame.margin;
		double marginLeft, marginRight, marginTop, marginBottom;

		AbsolutePos pos = this.getAbsolutePos();
		//
		// ■ 絶対配置または固定配置の行方向幅の計算
		//
		switch (this.params.flow) {
		case WritingMode.TB: {
			// 横書き
			double width = LayoutUtils.computeDimensionWidth(this.size, cWidth);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(width)) {
				width -= this.frame.getBorderWidth();
			}
			marginLeft = marginRight = 0;
			double left = 0;
			for (int state = 0; state < 2; ++state) {
				left = LayoutUtils.computeInsetsLeft(pos.location, cWidth);
				double right = LayoutUtils.computeInsetsRight(pos.location, cWidth);
				if (!LayoutUtils.isNone(left) && !LayoutUtils.isNone(right) && !LayoutUtils.isNone(width)) {
					marginLeft = margin.getLeftType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.left;
					marginRight = margin.getRightType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.right;
					if (LayoutUtils.isNone(marginLeft) && LayoutUtils.isNone(marginRight)) {
						marginLeft = marginRight = (cWidth - left - right - width - this.frame.getFrameWidth()) / 2.0;
					}
					if (LayoutUtils.isNone(marginLeft) && !LayoutUtils.isNone(marginRight)) {
						marginLeft = cWidth - left - right - width - this.frame.getFrameWidth();
					}
					if (!LayoutUtils.isNone(marginLeft) && LayoutUtils.isNone(marginRight)) {
						marginRight = cWidth - left - right - width - this.frame.getFrameWidth();
					} else {
						// 制限しすぎ
						right = 0;
						// right = lineWidth - left - width - marginLeft
						// - marginRight - aframe.getFrameWidth();
					}
				} else {
					marginLeft = amargin.left;
					marginRight = amargin.right;
					if (LayoutUtils.isNone(width)) {
						if (!LayoutUtils.isNone(left) && !LayoutUtils.isNone(right)) {
							width = cWidth - left - right - this.frame.getFrameWidth();
						} else {
							width = maxLineAxis;
							double limitWidth = cWidth - this.frame.getFrameWidth();
							if (LayoutUtils.isNone(left) && LayoutUtils.isNone(right)) {
								width = Sizing.fitContent(minLineAxis, width, limitWidth);
								left = right = 0;
							} else if (LayoutUtils.isNone(left)) {
								width = Sizing.fitContent(minLineAxis, width, limitWidth - right);
								left = cWidth - right - width - this.frame.getFrameWidth();
							} else {
								width = Sizing.fitContent(minLineAxis, width, limitWidth - left);
								right = cWidth - left - width - this.frame.getFrameWidth();
							}
						}
					} else {
						if (LayoutUtils.isNone(right)) {
							if (LayoutUtils.isNone(left)) {
								left = 0;
							}
							right = cWidth - left - width - this.frame.getFrameWidth();
						} else {
							left = cWidth - right - width - this.frame.getFrameWidth();
						}
					}
				}
				switch (state) {
				case 0:
					double maxWidth = LayoutUtils.computeDimensionWidth(this.params.maxSize, cWidth);
					if (!LayoutUtils.isNone(maxWidth) && width > maxWidth) {
						width = maxWidth;
						continue;
					}
					state = 1;
				case 1:
					double minWidth = LayoutUtils.computeDimensionWidth(this.minSize, cWidth);
					if (width < minWidth) {
						width = minWidth;
						continue;
					}
					state = 2;
					break;
				}
			}
			marginTop = margin.getTopType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.top;
			marginBottom = margin.getBottomType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.bottom;
			assert !LayoutUtils.isNone(left);
			this.offsetX = left;
			this.frame.margin.top = marginTop;
			this.frame.margin.right = marginRight;
			this.frame.margin.bottom = marginBottom;
			this.frame.margin.left = marginLeft;
			this.width = width;
			this.height = 0;
		}
			break;
		case WritingMode.RL:
		case WritingMode.LR: {
			// 縦書き
			double top = 0;// TODO test box-sizing
			double height = LayoutUtils.computeDimensionHeight(this.size, cHeight);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(height)) {
				height -= this.frame.getBorderHeight();
			}
			marginTop = marginBottom = 0;
			for (int state = 0; state < 2; ++state) {
				top = LayoutUtils.computeInsetsTop(pos.location, cHeight);
				double bottom = LayoutUtils.computeInsetsBottom(pos.location, cHeight);
				if (!LayoutUtils.isNone(top) && !LayoutUtils.isNone(bottom) && !LayoutUtils.isNone(height)) {
					marginTop = margin.getTopType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.top;
					marginBottom = margin.getBottomType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.bottom;
					if (LayoutUtils.isNone(marginTop) && LayoutUtils.isNone(marginBottom)) {
						marginTop = marginBottom = (cHeight - top - bottom - height - this.frame.getFrameHeight())
								/ 2.0;
					}
					if (LayoutUtils.isNone(marginTop) && !LayoutUtils.isNone(marginBottom)) {
						marginTop = cHeight - top - bottom - height - this.frame.getFrameHeight();
					}
					if (!LayoutUtils.isNone(marginTop) && LayoutUtils.isNone(marginBottom)) {
						marginBottom = cHeight - top - bottom - height - this.frame.getFrameHeight();
					} else {
						// 制限しすぎ
						bottom = 0;
						// bottom = lineWidth - top - height - marginTop
						// - marginBottom - aframe.getFrameHeight();
					}
				} else {
					marginTop = amargin.top;
					marginBottom = amargin.bottom;
					if (LayoutUtils.isNone(height)) {
						if (!LayoutUtils.isNone(top) && !LayoutUtils.isNone(bottom)) {
							height = cHeight - top - bottom - this.frame.getFrameHeight();
						} else {
							height = maxLineAxis;
							double limitHeight = cHeight - this.frame.getFrameHeight();
							if (LayoutUtils.isNone(top) && LayoutUtils.isNone(bottom)) {
								height = Sizing.fitContent(minLineAxis, height, limitHeight);
								top = bottom = 0;
							} else if (LayoutUtils.isNone(top)) {
								height = Sizing.fitContent(minLineAxis - bottom, height, limitHeight);
								top = cHeight - bottom - height - this.frame.getFrameHeight();
							} else {
								height = Sizing.fitContent(minLineAxis - top, height, limitHeight);
								bottom = cHeight - top - height - this.frame.getFrameHeight();
							}
						}
					} else {
						if (LayoutUtils.isNone(bottom)) {
							if (LayoutUtils.isNone(top)) {
								top = 0;
							}
							bottom = cHeight - top - height - this.frame.getFrameHeight();
						} else {
							top = cHeight - bottom - height - this.frame.getFrameHeight();
						}
					}
				}
				switch (state) {
				case 0:
					double maxHeight = LayoutUtils.computeDimensionHeight(this.params.maxSize, cHeight);
					if (!LayoutUtils.isNone(maxHeight) && height > maxHeight) {
						height = maxHeight;
						continue;
					}
					state = 1;
				case 1:
					double minHeight = LayoutUtils.computeDimensionHeight(this.minSize, cHeight);
					if (height < minHeight) {
						height = minHeight;
						continue;
					}
					state = 2;
					break;
				}
			}
			marginLeft = margin.getLeftType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.left;
			marginRight = margin.getRightType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.right;
			assert !LayoutUtils.isNone(top);
			this.offsetY = top;
			this.frame.margin.top = marginTop;
			this.frame.margin.right = marginRight;
			this.frame.margin.bottom = marginBottom;
			this.frame.margin.left = marginLeft;
			this.height = height;
			this.width = 0;
		}
			break;
		default:
			throw new IllegalStateException();
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
		// ■ 絶対配置または固定配置のページ方向幅の計算
		//
		AbsoluteInsets margin = this.frame.margin;
		AbsoluteInsets padding = this.frame.padding;
		RectBorder border = this.frame.frame.border;
		switch (this.params.flow) {
		case WritingMode.TB:
			// 横書き
			double height = LayoutUtils.computeDimensionHeight(this.size, cHeight);
			double marginTop = 0;
			double marginBottom = 0;
			double top = 0;
			for (int state = 0; state < 2; ++state) {
				marginTop = margin.top;
				marginBottom = margin.bottom;
				top = LayoutUtils.computeInsetsTop(pos.location, cHeight);
				double bottom = LayoutUtils.computeInsetsBottom(pos.location, cHeight);
				if (!LayoutUtils.isNone(top) && !LayoutUtils.isNone(bottom) && !LayoutUtils.isNone(height)) {
					if (LayoutUtils.isNone(marginTop) && LayoutUtils.isNone(marginBottom)) {
						marginTop = marginBottom = (cHeight - top - bottom - height - border.getFrameHeight()
								- padding.getFrameHeight()) / 2.0;
					}
					if (LayoutUtils.isNone(marginTop) && !LayoutUtils.isNone(marginBottom)) {
						marginTop = cHeight - top - bottom - height - marginBottom - border.getFrameHeight()
								- padding.getFrameHeight();
					}
					if (!LayoutUtils.isNone(marginTop) && LayoutUtils.isNone(marginBottom)) {
						marginBottom = cHeight - top - bottom - height - marginTop - border.getFrameHeight()
								- padding.getFrameHeight();
					} else {
						// 制限しすぎ
						bottom = 0;
						// bottom = pageHeight - top - height - marginTop
						// - marginBottom - padding.getFrameHeight();
					}
				} else {
					if (LayoutUtils.isNone(marginTop)) {
						marginTop = 0;
					}
					if (LayoutUtils.isNone(marginBottom)) {
						marginBottom = 0;
					}
					double contentSize = this.height;
					if (LayoutUtils.isNone(height)) {
						if (LayoutUtils.isNone(top) && LayoutUtils.isNone(bottom)) {
							top = 0;
							bottom = 0;
							height = contentSize;
						} else if (LayoutUtils.isNone(top) && !LayoutUtils.isNone(bottom)) {
							height = contentSize;
							top = cHeight - bottom - height - marginTop - marginBottom - border.getFrameHeight()
									- padding.getFrameHeight();
						} else if (!LayoutUtils.isNone(top) && LayoutUtils.isNone(bottom)) {
							height = contentSize;
							bottom = cHeight - top - height - marginTop - marginBottom - border.getFrameHeight()
									- padding.getFrameHeight();
						} else {
							height = cHeight - top - bottom - marginTop - marginBottom - border.getFrameHeight()
									- padding.getFrameHeight();
						}
					} else {
						if (LayoutUtils.isNone(bottom)) {
							if (LayoutUtils.isNone(top)) {
								top = 0;
							}
							bottom = cHeight - top - height - marginTop - marginBottom - border.getFrameHeight()
									- padding.getFrameHeight();
						} else {
							top = cHeight - bottom - height - marginTop - marginBottom - border.getFrameHeight()
									- padding.getFrameHeight();
						}
					}
				}
				switch (state) {
				case 0:
					double maxHeight = LayoutUtils.computeDimensionHeight(this.params.maxSize, cHeight);
					if (!LayoutUtils.isNone(maxHeight) && height > maxHeight) {
						height = maxHeight;
						continue;
					}
					state = 1;
				case 1:
					double minHeight = LayoutUtils.computeDimensionHeight(this.minSize, cHeight);
					if (height < minHeight) {
						height = minHeight;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !LayoutUtils.isNone(top);
			this.offsetY = top;
			assert !LayoutUtils.isNone(margin.right);
			assert !LayoutUtils.isNone(margin.left);
			assert !LayoutUtils.isNone(marginTop);
			assert !LayoutUtils.isNone(marginBottom);
			this.frame.margin.top = marginTop;
			this.frame.margin.bottom = marginBottom;
			assert !LayoutUtils.isNone(height);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				height -= this.frame.getBorderHeight();
			}
			this.height = height;
			break;

		case WritingMode.RL:
		case WritingMode.LR:
			// 縦書き
			double marginLeft = 0;
			double marginRight = 0;
			double left = 0;
			double width = LayoutUtils.computeDimensionWidth(this.size, cWidth);
			for (int state = 0; state < 2; ++state) {
				marginLeft = margin.left;
				marginRight = margin.right;
				left = LayoutUtils.computeInsetsLeft(pos.location, cWidth);
				double right = LayoutUtils.computeInsetsRight(pos.location, cWidth);
				if (!LayoutUtils.isNone(left) && !LayoutUtils.isNone(right) && !LayoutUtils.isNone(width)) {
					if (LayoutUtils.isNone(marginLeft) && LayoutUtils.isNone(marginRight)) {
						marginLeft = marginRight = (cWidth - left - right - width - border.getFrameWidth()
								- padding.getFrameWidth()) / 2.0;
					}
					if (LayoutUtils.isNone(marginLeft) && !LayoutUtils.isNone(marginRight)) {
						marginLeft = cWidth - left - right - width - marginRight - border.getFrameWidth()
								- padding.getFrameWidth();
					}
					if (!LayoutUtils.isNone(marginLeft) && LayoutUtils.isNone(marginRight)) {
						marginRight = cWidth - left - right - width - marginLeft - border.getFrameWidth()
								- padding.getFrameWidth();
					} else {
						// 制限しすぎ
						right = 0;
						// right = pageHeight - left - width - marginLeft
						// - marginRight - padding.getFrameWidth();
					}
				} else {
					if (LayoutUtils.isNone(marginLeft)) {
						marginLeft = 0;
					}
					if (LayoutUtils.isNone(marginRight)) {
						marginRight = 0;
					}
					double contentSize = this.getWidth() - this.frame.getFrameWidth();
					if (LayoutUtils.isNone(width)) {
						if (LayoutUtils.isNone(left) && LayoutUtils.isNone(right)) {
							left = 0;
							right = 0;
							width = contentSize;
						} else if (LayoutUtils.isNone(left) && !LayoutUtils.isNone(right)) {
							width = contentSize;
							left = cWidth - right - width - marginLeft - marginRight - border.getFrameWidth()
									- padding.getFrameWidth();
						} else if (!LayoutUtils.isNone(left) && LayoutUtils.isNone(right)) {
							width = contentSize;
							right = cWidth - left - width - marginLeft - marginRight - border.getFrameWidth()
									- padding.getFrameWidth();
						} else {
							width = cWidth - left - right - marginLeft - marginRight - border.getFrameWidth()
									- padding.getFrameWidth();
						}
					} else {
						if (LayoutUtils.isNone(left)) {
							if (LayoutUtils.isNone(right)) {
								right = 0;
							}
							left = cWidth - right - width - marginLeft - marginRight - border.getFrameWidth()
									- padding.getFrameWidth();
						} else {
							right = cWidth - left - width - marginLeft - marginRight - border.getFrameWidth()
									- padding.getFrameWidth();
						}
					}
				}
				switch (state) {
				case 0:
					double maxWidth = LayoutUtils.computeDimensionWidth(params.maxSize, cWidth);
					if (!LayoutUtils.isNone(maxWidth) && width > maxWidth) {
						width = maxWidth;
						continue;
					}
					state = 1;
				case 1:
					double minWidth = LayoutUtils.computeDimensionWidth(this.minSize, cWidth);
					if (width < minWidth) {
						width = minWidth;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !LayoutUtils.isNone(left);
			this.offsetX = left;
			assert !LayoutUtils.isNone(margin.top);
			assert !LayoutUtils.isNone(margin.bottom);
			assert !LayoutUtils.isNone(marginRight);
			assert !LayoutUtils.isNone(marginLeft);
			this.frame.margin.right = marginRight;
			this.frame.margin.left = marginLeft;
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				width -= this.frame.getBorderWidth();
			}
			this.width = width;
			break;
		default:
			throw new IllegalStateException();
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
