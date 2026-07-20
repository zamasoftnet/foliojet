package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;

import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * ブロックボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FlowBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FlowBlockBox extends AbstractStaticBlockBox implements IFlowBox {
	private static final boolean DEBUG = false;

	protected final FlowPos pos;

	protected double contentSize;

	/**
	 * 解決済みの整列です(表の auto マージン整列)。初期値は pos.align で、
	 * shrinkToFit(table) が解決結果を保存します。共有 pos への書き戻しは
	 * しない(ログが参照する pos は record 後不変 — §5.7 前提 (ii))。
	 * 分割断片へは splitPage が引き継ぎます。
	 */
	protected Align resolvedAlign;

	public FlowBlockBox(BlockParams params, FlowPos pos) {
		super(params);
		this.pos = pos;
		this.resolvedAlign = pos.align;
	}

	protected FlowBlockBox(BlockParams params, FlowPos pos, Dimension size, Dimension minSize, AbsoluteRectFrame frame,
			Container container) {
		super(params, size, minSize, frame, container);
		this.pos = pos;
		this.resolvedAlign = pos.align;
	}

	/**
	 * 解決済みの整列を返します(表整列の解決後はその結果)。
	 */
	public final Align getResolvedAlign() {
		return this.resolvedAlign;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbstractStaticPos getStaticPos() {
		return this.pos;
	}

	public final FlowPos getFlowPos() {
		return this.pos;
	}

	public final void shrinkToFit(LayoutStack layoutStack, IntrinsicSizes sizes, boolean table) {
		super.shrinkToFit(layoutStack, sizes, table);
		final AbstractContainerBox containerBox;
		if (!table) {
			return;
		}
		// テーブル
		BlockBuilder builder = (BlockBuilder) layoutStack;
		containerBox = builder.getFlow(builder.getFlowCount() - 2).box;

		Align align = this.resolvedAlign;
		if (containerBox.getBlockParams().flow.isVertical()) {
			// 縦書き
			if (align == Align.START) {
				Insets margin = this.getBlockParams().frame.margin;
				if (margin.getTopType() == LengthType.AUTO) {
					if (margin.getBottomType() == LengthType.AUTO) {
						align = Align.CENTER;
					} else {
						align = Align.END;
					}
				}
			}
			final double remainder = containerBox.getLineSize() - this.height;
			switch (align) {
			case Align.START:
				this.frame.margin.bottom = remainder;
				break;
			case Align.END:
				this.frame.margin.top = remainder;
				break;
			case Align.CENTER:
				this.frame.margin.top = this.frame.margin.bottom = remainder / 2;
				break;
			}
			// 幅を固定
			this.size = Dimension.create(0, this.height, LengthType.AUTO, LengthType.ABSOLUTE);
		} else {
			// 横書き
			if (align == Align.START) {
				Insets margin = this.getBlockParams().frame.margin;
				if (margin.getLeftType() == LengthType.AUTO) {
					if (margin.getRightType() == LengthType.AUTO) {
						align = Align.CENTER;
					} else {
						align = Align.END;
					}
				}
			}
			final double remainder = containerBox.getLineSize() - this.width;
			switch (align) {
			case Align.START:
				this.frame.margin.right = remainder;
				break;
			case Align.END:
				this.frame.margin.left = remainder;
				break;
			case Align.CENTER:
				this.frame.margin.left = this.frame.margin.right = remainder / 2;
				break;
			}
			// 幅を固定
			this.size = Dimension.create(this.width, 0, LengthType.ABSOLUTE, LengthType.AUTO);
		}
		this.resolvedAlign = align;
	}

	public final void setPageAxis(final double newSize) {
		this.contentSize = Math.max(this.contentSize, newSize);

		if (this.params.flow.isVertical()) {
			// 縦書き
			if (newSize <= this.width) {
				return;
			}
			if ((this.isSpecifiedPageSize() || this.getColumnCount() > 1) && newSize < this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		} else {
			// 横書き
			if (newSize == this.height) {
				return;
			}
			if ((this.isSpecifiedPageSize() || this.getColumnCount() > 1) && newSize < this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
	}

	public void calculateSize(LayoutStack layoutStack, double xmargin, double lineSize) {
		final AbstractContainerBox containerBox = layoutStack.getFlowBox();
		final BlockParams cParams = containerBox.getBlockParams();
		if (this.params.flow.isVertical()) {
			// 縦書きのフロー
			this.specifiedPageAxis = this.size.getWidthType() == LengthType.ABSOLUTE
					|| (this.size.getWidthType().needsReference() && containerBox.isSpecifiedPageSize());
		} else {
			// 横書きのフロー
			this.specifiedPageAxis = this.size.getHeightType() == LengthType.ABSOLUTE
					|| (this.size.getHeightType().needsReference() && containerBox.isSpecifiedPageSize());
		}

		//
		// ■ パディングの計算
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ マージンの計算
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		Insets margin = this.frame.frame.margin;
		AbsoluteInsets amargin = this.frame.margin;
		double marginLeft, marginRight, marginTop, marginBottom;

		//
		// ■ 静的配置または相対配置の行方向幅の計算
		//

		// 行方向幅の計算
		double minWidth = LayoutUtils.NONE, maxWidth = LayoutUtils.NONE, minHeight = LayoutUtils.NONE,
				maxHeight = LayoutUtils.NONE;
		if (cParams.flow.isVertical()) {
			// 縦書きのフロー
			marginLeft = amargin.left;
			marginRight = amargin.right;
			this.height = LayoutUtils.computeDimensionHeight(this.size, lineSize);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(this.height)) {
				this.height -= this.frame.getBorderHeight();
			}
			marginTop = marginBottom = 0;
			for (int state = 0; state < 2; ++state) {
				if (!LayoutUtils.isNone(this.height)) {
					// 固定幅の場合
					marginTop = margin.getTopType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.top;
					marginBottom = margin.getBottomType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.bottom;
					if (LayoutUtils.isNone(marginTop) && LayoutUtils.isNone(marginBottom)) {
						// 上下のマージンを同じにする
						marginTop = marginBottom = (lineSize - this.height - this.frame.getFrameHeight()) / 2.0;
					} else if (LayoutUtils.isNone(marginTop)) {
						// 上のマージンが不確定
						marginTop = lineSize - this.height - this.frame.getFrameHeight();
					} else if (LayoutUtils.isNone(marginBottom)) {
						// 下のマージンが不確定
						marginBottom = lineSize - marginBottom - this.frame.getFrameHeight();
					} else {
						// 制限しすぎ
												switch (this.resolvedAlign) {
						case Align.START:
							// 上寄せ
							marginBottom = 0;
							break;
						case Align.END:
							// 下寄せ
							marginTop += lineSize - this.height - this.frame.getFrameHeight();
							break;
						case Align.CENTER:
							// 中央
							double remainder = lineSize - this.height - this.frame.getFrameHeight();
							remainder /= 2.0;
							marginTop += remainder;
							marginBottom += remainder;
							break;
						default:
							throw new IllegalStateException();
						}
					}
				} else {
					// 自動幅の場合
					marginTop = amargin.top;
					marginBottom = amargin.bottom;
					this.height = lineSize - this.frame.getFrameHeight();
				}
				switch (state) {
				case 0:
					maxHeight = LayoutUtils.computeDimensionHeight(this.params.maxSize, lineSize);
					if (LayoutUtils.isNone(maxHeight)) {
						maxHeight = Double.MAX_VALUE;
					} else if (this.height > maxHeight) {
						this.height = maxHeight;
						continue;
					}
					state = 1;
				case 1:
					minHeight = LayoutUtils.computeDimensionHeight(this.minSize, lineSize);
					if (this.height < minHeight) {
						this.height = minHeight;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !LayoutUtils.isNone(minHeight);
			assert !LayoutUtils.isNone(maxHeight);
			switch (this.minSize.getWidthType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					minWidth = this.minSize.getWidth() * containerBox.getInnerWidth();
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				minWidth = 0;
				break;
			case ABSOLUTE:
				minWidth = this.minSize.getWidth();
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					minWidth = this.minSize.getWidth() + this.minSize.getWidthRatio() * containerBox.getInnerWidth();
					break;
				}
				minWidth = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.params.maxSize.getWidthType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					maxWidth = this.params.maxSize.getWidth() * containerBox.getInnerWidth();
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				maxWidth = Double.MAX_VALUE;
				break;
			case ABSOLUTE:
				maxWidth = this.params.maxSize.getWidth();
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					maxWidth = this.params.maxSize.getWidth()
							+ this.params.maxSize.getWidthRatio() * containerBox.getInnerWidth();
					break;
				}
				maxWidth = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.size.getWidthType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					this.width = this.size.getWidth() * containerBox.getInnerWidth();
					this.width = Math.max(this.width, minWidth);
					this.width = Math.min(this.width, maxWidth);
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.width -= this.getFrame().getBorderWidth();
					}
					minWidth = maxWidth = this.width;
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				if (!this.params.flow.isVertical()) {
					// 横書きのボックス
					this.width = layoutStack.getFixedWidth() - this.frame.getFrameWidth();
				} else {
					this.width = 0;
				}
				this.width = Math.max(this.width, minWidth);
				break;
			case ABSOLUTE:
				this.width = this.size.getWidth();
				this.width = Math.max(this.width, minWidth);
				this.width = Math.min(this.width, maxWidth);
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					this.width -= this.getFrame().getBorderWidth();
				}
				minWidth = maxWidth = this.width;
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					this.width = this.size.getWidth() + this.size.getWidthRatio() * containerBox.getInnerWidth();
					this.width = Math.max(this.width, minWidth);
					this.width = Math.min(this.width, maxWidth);
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.width -= this.getFrame().getBorderWidth();
					}
					minWidth = maxWidth = this.width;
					break;
				}
				if (!this.params.flow.isVertical()) {
					this.width = layoutStack.getFixedWidth() - this.frame.getFrameWidth();
				} else {
					this.width = 0;
				}
				this.width = Math.max(this.width, minWidth);
				break;
			default:
				throw new IllegalStateException();
			}
			marginTop += xmargin;
		} else {
			// 横書きのフロー
			marginTop = amargin.top;
			marginBottom = amargin.bottom;
			this.width = LayoutUtils.computeDimensionWidth(this.size, lineSize);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(this.width)) {
				this.width -= this.frame.getBorderWidth();
			}
			marginLeft = marginRight = 0;
			for (int state = 0; state < 2; ++state) {
				if (!LayoutUtils.isNone(this.width)) {
					// 固定幅の場合
					marginLeft = margin.getLeftType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.left;
					marginRight = margin.getRightType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.right;
					if (LayoutUtils.isNone(marginLeft) && LayoutUtils.isNone(marginRight)) {
						// 左右のマージンを同じにする
						marginLeft = marginRight = (lineSize - this.width - this.frame.getFrameWidth()) / 2.0;
					} else {
						if (LayoutUtils.isNone(marginLeft) && !LayoutUtils.isNone(marginRight)) {
							// 左が不確定
							marginLeft = lineSize - this.width - this.frame.getFrameWidth();
						} else if (LayoutUtils.isNone(marginRight)) {
							// 右が不確定
							marginRight = lineSize - this.width - this.frame.getFrameWidth();
						} else {
							// 制限しすぎ
														switch (this.resolvedAlign) {
							case Align.START:
								// 左寄せ
								marginRight = 0;
								break;
							case Align.END:
								// 右寄せ
								marginLeft += lineSize - this.width - this.frame.getFrameWidth();
								break;
							case Align.CENTER:
								// 中央
								double remainder = lineSize - this.width - this.frame.getFrameWidth();
								remainder /= 2.0;
								marginLeft += remainder;
								marginRight += remainder;
								break;
							default:
								throw new IllegalStateException();
							}
						}
					}
				} else {
					// 自動幅の場合
					marginLeft = amargin.left;
					marginRight = amargin.right;
					this.width = lineSize - this.frame.getFrameWidth();
				}
				switch (state) {
				case 0:
					maxWidth = LayoutUtils.computeDimensionWidth(this.params.maxSize, lineSize);
					if (LayoutUtils.isNone(maxWidth)) {
						maxWidth = Double.MAX_VALUE;
					} else if (this.width > maxWidth) {
						this.width = maxWidth;
						continue;
					}
					state = 1;
				case 1:
					minWidth = LayoutUtils.computeDimensionWidth(this.minSize, lineSize);
					if (this.width < minWidth) {
						this.width = minWidth;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !LayoutUtils.isNone(minWidth);
			assert !LayoutUtils.isNone(maxWidth);
			switch (this.minSize.getHeightType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					minHeight = this.minSize.getHeight() * containerBox.getInnerHeight();
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				minHeight = 0;
				break;
			case ABSOLUTE:
				minHeight = this.minSize.getHeight();
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					minHeight = this.minSize.getHeight() + this.minSize.getHeightRatio() * containerBox.getInnerHeight();
					break;
				}
				minHeight = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.params.maxSize.getHeightType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					maxHeight = this.params.maxSize.getHeight() * containerBox.getInnerHeight();
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				maxHeight = Double.MAX_VALUE;
				break;
			case ABSOLUTE:
				maxHeight = this.params.maxSize.getHeight();
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					maxHeight = this.params.maxSize.getHeight()
							+ this.params.maxSize.getHeightRatio() * containerBox.getInnerHeight();
					break;
				}
				maxHeight = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.size.getHeightType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					this.height = this.size.getHeight() * containerBox.getInnerHeight();
					this.height = Math.max(this.height, minHeight);
					this.height = Math.min(this.height, maxHeight);
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.height -= this.getFrame().getBorderHeight();
					}
					minHeight = this.height;
					maxHeight = this.height;
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				if (this.params.flow.isVertical()) {
					// 縦書きのボックス
					this.height = layoutStack.getFixedHeight() - this.frame.getFrameHeight();
				} else {
					this.height = 0;
				}
				this.height = Math.max(this.height, minHeight);
				break;
			case ABSOLUTE:
				this.height = this.size.getHeight();
				this.height = Math.max(this.height, minHeight);
				this.height = Math.min(this.height, maxHeight);
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					this.height -= this.getFrame().getBorderHeight();
				}
				minHeight = this.height;
				// 指定幅に固定する
				maxHeight = this.height;
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					this.height = this.size.getHeight() + this.size.getHeightRatio() * containerBox.getInnerHeight();
					this.height = Math.max(this.height, minHeight);
					this.height = Math.min(this.height, maxHeight);
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.height -= this.getFrame().getBorderHeight();
					}
					minHeight = this.height;
					maxHeight = this.height;
					break;
				}
				if (this.params.flow.isVertical()) {
					this.height = layoutStack.getFixedHeight() - this.frame.getFrameHeight();
				} else {
					this.height = 0;
				}
				this.height = Math.max(this.height, minHeight);
				break;
			default:
				throw new IllegalStateException();
			}
			marginLeft += xmargin;
		}
		if (this.params.flow.isVertical()) {
			this.minPageAxis = minWidth;
			this.maxPageAxis = maxWidth;
		} else {
			this.minPageAxis = minHeight;
			this.maxPageAxis = maxHeight;
		}
		assert !LayoutUtils.isNone(marginTop);
		assert !LayoutUtils.isNone(marginRight);
		assert !LayoutUtils.isNone(marginBottom);
		assert !LayoutUtils.isNone(marginLeft);
		this.frame.margin.top = marginTop;
		this.frame.margin.right = marginRight;
		this.frame.margin.bottom = marginBottom;
		this.frame.margin.left = marginLeft;
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final double getContentSize() {
		return this.contentSize;
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(.5f, 1f, .5f));
			drawer.visitDrawable(drawable, x, y);
		}

		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		if (this.getFlowPos().offset != null) {
			this.frames(pageBox, drawer, clip, transform, x, y);
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FlowPos pos = this.getFlowPos();
		// 解決済み整列は断片間で共有される状態(旧実装の pos 書き戻し相当)。
		// レシピは値をキャプチャし、this を保持しない
		final Align resolvedAlign = this.resolvedAlign;
		return (state, container) -> {
			final FlowBlockBox next = new FlowBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
					state.nextFrame(), container);
			next.resolvedAlign = resolvedAlign;
			return next;
		};
	}

	public final void restyle(final BlockBuilder builder, final net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		builder.startFlowBlock(this);
		super.restyle(builder, shape);
		if (!(shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.Closed)) {
			// 開いた継続: 末尾はこの箱の中で live が続く
			return;
		}
		builder.endFlowBlock();
	}

	public boolean avoidBreakBefore() {
		if (this.getFlowPos().pageBreakBefore == PageBreakMode.AVOID) {
			return true;
		}
		return this.container.avoidBreakBefore();
	}

	public boolean avoidBreakAfter() {
		if (this.getFlowPos().pageBreakAfter == PageBreakMode.AVOID) {
			return true;
		}
		return this.container.avoidBreakAfter();
	}
}
