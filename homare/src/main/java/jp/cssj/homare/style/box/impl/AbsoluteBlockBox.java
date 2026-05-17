package jp.cssj.homare.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import jp.cssj.homare.style.box.AbstractBlockBox;
import jp.cssj.homare.style.box.IAbsoluteBox;
import jp.cssj.homare.style.box.IFramedBox;
import jp.cssj.homare.style.box.content.Container;
import jp.cssj.homare.style.box.params.AbsolutePos;
import jp.cssj.homare.style.box.params.AbstractTextParams;
import jp.cssj.homare.style.box.params.BlockParams;
import jp.cssj.homare.style.box.params.Dimension;
import jp.cssj.homare.style.box.params.Insets;
import jp.cssj.homare.style.box.params.Params;
import jp.cssj.homare.style.box.params.Pos;
import jp.cssj.homare.style.box.params.RectBorder;
import jp.cssj.homare.style.box.params.Types;
import jp.cssj.homare.style.builder.impl.BlockBuilder;
import jp.cssj.homare.style.builder.impl.TwoPassBlockBuilder;
import jp.cssj.homare.style.draw.Drawer;
import jp.cssj.homare.style.part.AbsoluteInsets;
import jp.cssj.homare.style.part.AbsoluteRectFrame;
import jp.cssj.homare.style.util.StyleUtils;
import jp.cssj.homare.style.visitor.Visitor;

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

	public final void shrinkToFit(IFramedBox containerBox, double minLineAxis, double maxLineAxis) {
		double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();
		{
			double lineAxis;
			if (StyleUtils.isVertical(this.params.flow)) {
				// 縦書き
				lineAxis = cHeight;
			} else {
				// 横書き
				lineAxis = cWidth;
			}

			//
			// ■ パディングの計算
			//
			StyleUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineAxis);

			//
			// ■ マージンの計算
			//
			StyleUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineAxis);
		}

		Insets margin = this.frame.frame.margin;
		AbsoluteInsets amargin = this.frame.margin;
		double marginLeft, marginRight, marginTop, marginBottom;

		AbsolutePos pos = this.getAbsolutePos();
		//
		// ■ 絶対配置または固定配置の行方向幅の計算
		//
		switch (this.params.flow) {
		case AbstractTextParams.FLOW_TB: {
			// 横書き
			double width = StyleUtils.computeDimensionWidth(this.size, cWidth);
			if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX && !StyleUtils.isNone(width)) {
				width -= this.frame.getBorderWidth();
			}
			marginLeft = marginRight = 0;
			double left = 0;
			for (int state = 0; state < 2; ++state) {
				left = StyleUtils.computeInsetsLeft(pos.location, cWidth);
				double right = StyleUtils.computeInsetsRight(pos.location, cWidth);
				if (!StyleUtils.isNone(left) && !StyleUtils.isNone(right) && !StyleUtils.isNone(width)) {
					marginLeft = margin.getLeftType() == Insets.TYPE_AUTO ? StyleUtils.NONE : amargin.left;
					marginRight = margin.getRightType() == Insets.TYPE_AUTO ? StyleUtils.NONE : amargin.right;
					if (StyleUtils.isNone(marginLeft) && StyleUtils.isNone(marginRight)) {
						marginLeft = marginRight = (cWidth - left - right - width - this.frame.getFrameWidth()) / 2.0;
					}
					if (StyleUtils.isNone(marginLeft) && !StyleUtils.isNone(marginRight)) {
						marginLeft = cWidth - left - right - width - this.frame.getFrameWidth();
					}
					if (!StyleUtils.isNone(marginLeft) && StyleUtils.isNone(marginRight)) {
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
					if (StyleUtils.isNone(width)) {
						if (!StyleUtils.isNone(left) && !StyleUtils.isNone(right)) {
							width = cWidth - left - right - this.frame.getFrameWidth();
						} else {
							width = maxLineAxis;
							double limitWidth = cWidth - this.frame.getFrameWidth();
							if (StyleUtils.isNone(left) && StyleUtils.isNone(right)) {
								width = Math.max(minLineAxis, Math.min(limitWidth, width));
								left = right = 0;
							} else if (StyleUtils.isNone(left)) {
								width = Math.max(minLineAxis, Math.min(limitWidth - right, width));
								left = cWidth - right - width - this.frame.getFrameWidth();
							} else {
								width = Math.max(minLineAxis, Math.min(limitWidth - left, width));
								right = cWidth - left - width - this.frame.getFrameWidth();
							}
						}
					} else {
						if (StyleUtils.isNone(right)) {
							if (StyleUtils.isNone(left)) {
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
					double maxWidth = StyleUtils.computeDimensionWidth(this.params.maxSize, cWidth);
					if (!StyleUtils.isNone(maxWidth) && width > maxWidth) {
						width = maxWidth;
						continue;
					}
					state = 1;
				case 1:
					double minWidth = StyleUtils.computeDimensionWidth(this.minSize, cWidth);
					if (width < minWidth) {
						width = minWidth;
						continue;
					}
					state = 2;
					break;
				}
			}
			marginTop = margin.getTopType() == Insets.TYPE_AUTO ? StyleUtils.NONE : amargin.top;
			marginBottom = margin.getBottomType() == Insets.TYPE_AUTO ? StyleUtils.NONE : amargin.bottom;
			assert !StyleUtils.isNone(left);
			this.offsetX = left;
			this.frame.margin.top = marginTop;
			this.frame.margin.right = marginRight;
			this.frame.margin.bottom = marginBottom;
			this.frame.margin.left = marginLeft;
			this.width = width;
			this.height = 0;
		}
			break;
		case AbstractTextParams.FLOW_RL:
		case AbstractTextParams.FLOW_LR: {
			// 縦書き
			double top = 0;// TODO test box-sizing
			double height = StyleUtils.computeDimensionHeight(this.size, cHeight);
			if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX && !StyleUtils.isNone(height)) {
				height -= this.frame.getBorderHeight();
			}
			marginTop = marginBottom = 0;
			for (int state = 0; state < 2; ++state) {
				top = StyleUtils.computeInsetsTop(pos.location, cHeight);
				double bottom = StyleUtils.computeInsetsBottom(pos.location, cHeight);
				if (!StyleUtils.isNone(top) && !StyleUtils.isNone(bottom) && !StyleUtils.isNone(height)) {
					marginTop = margin.getTopType() == Insets.TYPE_AUTO ? StyleUtils.NONE : amargin.top;
					marginBottom = margin.getBottomType() == Insets.TYPE_AUTO ? StyleUtils.NONE : amargin.bottom;
					if (StyleUtils.isNone(marginTop) && StyleUtils.isNone(marginBottom)) {
						marginTop = marginBottom = (cHeight - top - bottom - height - this.frame.getFrameHeight())
								/ 2.0;
					}
					if (StyleUtils.isNone(marginTop) && !StyleUtils.isNone(marginBottom)) {
						marginTop = cHeight - top - bottom - height - this.frame.getFrameHeight();
					}
					if (!StyleUtils.isNone(marginTop) && StyleUtils.isNone(marginBottom)) {
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
					if (StyleUtils.isNone(height)) {
						if (!StyleUtils.isNone(top) && !StyleUtils.isNone(bottom)) {
							height = cHeight - top - bottom - this.frame.getFrameHeight();
						} else {
							height = maxLineAxis;
							double limitHeight = cHeight - this.frame.getFrameHeight();
							if (StyleUtils.isNone(top) && StyleUtils.isNone(bottom)) {
								height = Math.max(minLineAxis, Math.min(limitHeight, height));
								top = bottom = 0;
							} else if (StyleUtils.isNone(top)) {
								height = Math.max(minLineAxis - bottom, Math.min(limitHeight, height));
								top = cHeight - bottom - height - this.frame.getFrameHeight();
							} else {
								height = Math.max(minLineAxis - top, Math.min(limitHeight, height));
								bottom = cHeight - top - height - this.frame.getFrameHeight();
							}
						}
					} else {
						if (StyleUtils.isNone(bottom)) {
							if (StyleUtils.isNone(top)) {
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
					double maxHeight = StyleUtils.computeDimensionHeight(this.params.maxSize, cHeight);
					if (!StyleUtils.isNone(maxHeight) && height > maxHeight) {
						height = maxHeight;
						continue;
					}
					state = 1;
				case 1:
					double minHeight = StyleUtils.computeDimensionHeight(this.minSize, cHeight);
					if (height < minHeight) {
						height = minHeight;
						continue;
					}
					state = 2;
					break;
				}
			}
			marginLeft = margin.getLeftType() == Insets.TYPE_AUTO ? StyleUtils.NONE : amargin.left;
			marginRight = margin.getRightType() == Insets.TYPE_AUTO ? StyleUtils.NONE : amargin.right;
			assert !StyleUtils.isNone(top);
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
		assert !StyleUtils.isNone(this.width);
		assert !StyleUtils.isNone(this.height);
	}

	public final void finishLayout(final IFramedBox containerBox) {
		if (this.builder != null) {
			this.shrinkToFit(containerBox, this.builder.getMinLineSize(), this.builder.getMaxLineSize());
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
		case AbstractTextParams.FLOW_TB:
			// 横書き
			double height = StyleUtils.computeDimensionHeight(this.size, cHeight);
			double marginTop = 0;
			double marginBottom = 0;
			double top = 0;
			for (int state = 0; state < 2; ++state) {
				marginTop = margin.top;
				marginBottom = margin.bottom;
				top = StyleUtils.computeInsetsTop(pos.location, cHeight);
				double bottom = StyleUtils.computeInsetsBottom(pos.location, cHeight);
				if (!StyleUtils.isNone(top) && !StyleUtils.isNone(bottom) && !StyleUtils.isNone(height)) {
					if (StyleUtils.isNone(marginTop) && StyleUtils.isNone(marginBottom)) {
						marginTop = marginBottom = (cHeight - top - bottom - height - border.getFrameHeight()
								- padding.getFrameHeight()) / 2.0;
					}
					if (StyleUtils.isNone(marginTop) && !StyleUtils.isNone(marginBottom)) {
						marginTop = cHeight - top - bottom - height - marginBottom - border.getFrameHeight()
								- padding.getFrameHeight();
					}
					if (!StyleUtils.isNone(marginTop) && StyleUtils.isNone(marginBottom)) {
						marginBottom = cHeight - top - bottom - height - marginTop - border.getFrameHeight()
								- padding.getFrameHeight();
					} else {
						// 制限しすぎ
						bottom = 0;
						// bottom = pageHeight - top - height - marginTop
						// - marginBottom - padding.getFrameHeight();
					}
				} else {
					if (StyleUtils.isNone(marginTop)) {
						marginTop = 0;
					}
					if (StyleUtils.isNone(marginBottom)) {
						marginBottom = 0;
					}
					double contentSize = this.height;
					if (StyleUtils.isNone(height)) {
						if (StyleUtils.isNone(top) && StyleUtils.isNone(bottom)) {
							top = 0;
							bottom = 0;
							height = contentSize;
						} else if (StyleUtils.isNone(top) && !StyleUtils.isNone(bottom)) {
							height = contentSize;
							top = cHeight - bottom - height - marginTop - marginBottom - border.getFrameHeight()
									- padding.getFrameHeight();
						} else if (!StyleUtils.isNone(top) && StyleUtils.isNone(bottom)) {
							height = contentSize;
							bottom = cHeight - top - height - marginTop - marginBottom - border.getFrameHeight()
									- padding.getFrameHeight();
						} else {
							height = cHeight - top - bottom - marginTop - marginBottom - border.getFrameHeight()
									- padding.getFrameHeight();
						}
					} else {
						if (StyleUtils.isNone(bottom)) {
							if (StyleUtils.isNone(top)) {
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
					double maxHeight = StyleUtils.computeDimensionHeight(this.params.maxSize, cHeight);
					if (!StyleUtils.isNone(maxHeight) && height > maxHeight) {
						height = maxHeight;
						continue;
					}
					state = 1;
				case 1:
					double minHeight = StyleUtils.computeDimensionHeight(this.minSize, cHeight);
					if (height < minHeight) {
						height = minHeight;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !StyleUtils.isNone(top);
			this.offsetY = top;
			assert !StyleUtils.isNone(margin.right);
			assert !StyleUtils.isNone(margin.left);
			assert !StyleUtils.isNone(marginTop);
			assert !StyleUtils.isNone(marginBottom);
			this.frame.margin.top = marginTop;
			this.frame.margin.bottom = marginBottom;
			assert !StyleUtils.isNone(height);
			if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
				height -= this.frame.getBorderHeight();
			}
			this.height = height;
			break;

		case AbstractTextParams.FLOW_RL:
		case AbstractTextParams.FLOW_LR:
			// 縦書き
			double marginLeft = 0;
			double marginRight = 0;
			double left = 0;
			double width = StyleUtils.computeDimensionWidth(this.size, cWidth);
			for (int state = 0; state < 2; ++state) {
				marginLeft = margin.left;
				marginRight = margin.right;
				left = StyleUtils.computeInsetsLeft(pos.location, cWidth);
				double right = StyleUtils.computeInsetsRight(pos.location, cWidth);
				if (!StyleUtils.isNone(left) && !StyleUtils.isNone(right) && !StyleUtils.isNone(width)) {
					if (StyleUtils.isNone(marginLeft) && StyleUtils.isNone(marginRight)) {
						marginLeft = marginRight = (cWidth - left - right - width - border.getFrameWidth()
								- padding.getFrameWidth()) / 2.0;
					}
					if (StyleUtils.isNone(marginLeft) && !StyleUtils.isNone(marginRight)) {
						marginLeft = cWidth - left - right - width - marginRight - border.getFrameWidth()
								- padding.getFrameWidth();
					}
					if (!StyleUtils.isNone(marginLeft) && StyleUtils.isNone(marginRight)) {
						marginRight = cWidth - left - right - width - marginLeft - border.getFrameWidth()
								- padding.getFrameWidth();
					} else {
						// 制限しすぎ
						right = 0;
						// right = pageHeight - left - width - marginLeft
						// - marginRight - padding.getFrameWidth();
					}
				} else {
					if (StyleUtils.isNone(marginLeft)) {
						marginLeft = 0;
					}
					if (StyleUtils.isNone(marginRight)) {
						marginRight = 0;
					}
					double contentSize = this.getWidth() - this.frame.getFrameWidth();
					if (StyleUtils.isNone(width)) {
						if (StyleUtils.isNone(left) && StyleUtils.isNone(right)) {
							left = 0;
							right = 0;
							width = contentSize;
						} else if (StyleUtils.isNone(left) && !StyleUtils.isNone(right)) {
							width = contentSize;
							left = cWidth - right - width - marginLeft - marginRight - border.getFrameWidth()
									- padding.getFrameWidth();
						} else if (!StyleUtils.isNone(left) && StyleUtils.isNone(right)) {
							width = contentSize;
							right = cWidth - left - width - marginLeft - marginRight - border.getFrameWidth()
									- padding.getFrameWidth();
						} else {
							width = cWidth - left - right - marginLeft - marginRight - border.getFrameWidth()
									- padding.getFrameWidth();
						}
					} else {
						if (StyleUtils.isNone(left)) {
							if (StyleUtils.isNone(right)) {
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
					double maxWidth = StyleUtils.computeDimensionWidth(params.maxSize, cWidth);
					if (!StyleUtils.isNone(maxWidth) && width > maxWidth) {
						width = maxWidth;
						continue;
					}
					state = 1;
				case 1:
					double minWidth = StyleUtils.computeDimensionWidth(this.minSize, cWidth);
					if (width < minWidth) {
						width = minWidth;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !StyleUtils.isNone(left);
			this.offsetX = left;
			assert !StyleUtils.isNone(margin.top);
			assert !StyleUtils.isNone(margin.bottom);
			assert !StyleUtils.isNone(marginRight);
			assert !StyleUtils.isNone(marginLeft);
			this.frame.margin.right = marginRight;
			this.frame.margin.left = marginLeft;
			if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
				width -= this.frame.getBorderWidth();
			}
			this.width = width;
			break;
		default:
			throw new IllegalStateException();
		}
		assert !StyleUtils.isNone(this.width);
		assert !StyleUtils.isNone(this.height);
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
