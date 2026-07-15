package net.zamasoft.foliojet.style.box;

import net.zamasoft.foliojet.style.box.content.Container;
import net.zamasoft.foliojet.style.box.params.LengthType;
import net.zamasoft.foliojet.style.box.params.PosType;
import net.zamasoft.foliojet.style.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.style.builder.LayoutStack;
import net.zamasoft.foliojet.style.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.style.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.style.util.StyleUtils;

/**
 * ブロックボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractStaticBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractStaticBlockBox extends AbstractBlockBox {
	protected boolean specifiedPageAxis = false;

	public AbstractStaticBlockBox(final BlockParams params) {
		super(params);
	}

	protected AbstractStaticBlockBox(final BlockParams params, final Dimension size, final Dimension minSize,
			final AbsoluteRectFrame frame, final Container container) {
		super(params, size, minSize, frame, container);
	}

	public abstract AbstractStaticPos getStaticPos();

	public final boolean isSpecifiedPageSize() {
		return this.specifiedPageAxis;
	}

	public final boolean isContextBox() {
		return this.getStaticPos().offset != null;
	}

	public void shrinkToFit(LayoutStack layoutStack, double minLineAxis, double maxLineAxis, boolean table) {
		final AbstractContainerBox containerBox;
		if (this.getPos().getType() == PosType.FLOW) {
			if (table) {
				// テーブル
				BlockBuilder builder = (BlockBuilder) layoutStack;
				containerBox = builder.getFlow(builder.getFlowCount() - 2).box;
			} else {
				// 書字方向の混在
				containerBox = layoutStack.getFlowBox();
			}
		} else {
			containerBox = layoutStack.getFlowBox();
		}
		if (!table && containerBox.getType() == BoxType.TABLE_CELL) {
			table = true;
		}
		final BlockParams cParams = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();
		if (StyleUtils.isVertical(this.params.flow)) {
			// 縦書き
			this.specifiedPageAxis = this.params.size.getWidthType() == LengthType.ABSOLUTE
					|| (this.params.size.getWidthType() == LengthType.RELATIVE && (!table
							&& (this.getPos().getType() == PosType.INLINE || containerBox.isSpecifiedPageSize())));
		} else {
			// 横書き
			this.specifiedPageAxis = this.params.size.getHeightType() == LengthType.ABSOLUTE
					|| (this.params.size.getHeightType() == LengthType.RELATIVE && (!table
							&& (this.getPos().getType() == PosType.INLINE || containerBox.isSpecifiedPageSize())));
		}

		//
		// ■ パディングの計算
		//
		StyleUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ マージンの計算
		//
		StyleUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		//
		// ■ 行方向幅の計算
		//
		// 幅
		if (StyleUtils.isVertical(this.params.flow)) {
			// 縦書き
			AbstractContainerBox fixedWidthBox = layoutStack.getFixedWidthFlowBox();
			if (fixedWidthBox == null) {
				fixedWidthBox = containerBox;
			}
			double cHeight = table ? containerBox.getInnerHeight() : layoutStack.getFixedHeight();
			double cWidth = fixedWidthBox.getInnerHeight();
			this.height = StyleUtils.computeDimensionHeight(this.size, cHeight);
			if (StyleUtils.isNone(this.height)) {
				this.height = maxLineAxis;
			} else {
				if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
					this.height -= this.frame.getBorderHeight();
				}
			}
			if ((this.size.getHeightType() == LengthType.AUTO) &&
			// 縦中横が拡張されるようにページ方向が固定されていないとみなす。
					containerBox.getSubtype() != BoxSubtype.RUBY_BODY) {
				double limitHeight;
				if (StyleUtils.isVertical(cParams.flow) || containerBox.isSpecifiedPageSize()) {
					limitHeight = cHeight - this.frame.getFrameHeight();
				} else {
					// 親の幅が不確定の場合はページ高さを限度とする
					limitHeight = layoutStack.getFixedHeight() - this.frame.getFrameHeight();
				}
				this.height = Math.max(minLineAxis, Math.min(limitHeight, this.height));
			}
			double maxHeight = StyleUtils.computeDimensionHeight(this.params.maxSize, cHeight);
			if (!StyleUtils.isNone(maxHeight) && this.height > maxHeight) {
				this.height = maxHeight;
			}
			double minHeight = StyleUtils.computeDimensionHeight(this.minSize, cHeight);
			if (this.height < minHeight) {
				this.height = minHeight;
			}

			double minWidth;
			switch (this.minSize.getWidthType()) {
			case RELATIVE:
				if (!table && this.isSpecifiedPageSize()) {
					minWidth = this.minSize.getWidth() * cWidth;
					break;
				}
			case AUTO:
				minWidth = 0;
				break;
			case ABSOLUTE:
				minWidth = this.minSize.getWidth();
				break;
			default:
				throw new IllegalStateException();
			}
			double maxWidth;
			switch (this.params.maxSize.getWidthType()) {
			case RELATIVE:
				if (!table && this.isSpecifiedPageSize()) {
					maxWidth = params.maxSize.getWidth() * cWidth;
					break;
				}
			case AUTO:
				maxWidth = Double.MAX_VALUE;
				break;
			case ABSOLUTE:
				maxWidth = this.params.maxSize.getWidth();
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.size.getWidthType()) {
			case RELATIVE:
				if (!table && this.isSpecifiedPageSize()) {
					this.width = this.size.getWidth() * cWidth;
					this.width = Math.max(this.width, minWidth);
					this.width = Math.min(this.width, maxWidth);
					if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
						this.width -= this.getFrame().getBorderWidth();
					}
					minWidth = maxWidth = this.width;
					break;
				}
			case AUTO:
				if (!table) {
					this.width = 0;
				}
				break;
			case ABSOLUTE:
				this.width = this.size.getWidth();
				this.width = Math.max(this.width, minWidth);
				this.width = Math.min(this.width, maxWidth);
				if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
					this.width -= this.getFrame().getBorderWidth();
				}
				minWidth = maxWidth = this.width;
				break;
			default:
				throw new IllegalStateException();
			}
			this.minPageAxis = minWidth;
			this.maxPageAxis = maxWidth;
		} else {
			// 横書き
			AbstractContainerBox fixedHeightBox = layoutStack.getFixedHeightFlowBox();
			if (fixedHeightBox == null) {
				fixedHeightBox = containerBox;
			}
			double cHeight = fixedHeightBox.getInnerHeight();
			double cWidth = table ? containerBox.getInnerWidth() : layoutStack.getFixedWidth();
			this.width = StyleUtils.computeDimensionWidth(this.size, cWidth);
			if (StyleUtils.isNone(this.width)) {
				this.width = maxLineAxis;
			} else {
				if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
					this.width -= this.frame.getBorderWidth();
				}
			}
			if ((this.size.getWidthType() == LengthType.AUTO) &&
			// 縦中横が拡張されるようにページ方向が固定されていないとみなす。
					containerBox.getSubtype() != BoxSubtype.RUBY_BODY) {
				double limitWidth;
				if (!StyleUtils.isVertical(cParams.flow) || containerBox.isSpecifiedPageSize()) {
					limitWidth = cWidth - this.frame.getFrameWidth();
				} else {
					// 親の幅が不確定の場合はページ幅を限度とする
					limitWidth = layoutStack.getFixedWidth() - this.frame.getFrameWidth();
				}
				this.width = Math.max(minLineAxis, Math.min(limitWidth, this.width));
			}
			double maxWidth = StyleUtils.computeDimensionWidth(this.params.maxSize, cWidth);
			if (!StyleUtils.isNone(maxWidth) && this.width > maxWidth) {
				this.width = maxWidth;
			}
			double minWidth = StyleUtils.computeDimensionWidth(this.minSize, cWidth);
			if (this.width < minWidth) {
				this.width = minWidth;
			}

			double minHeight;
			switch (this.minSize.getHeightType()) {
			case RELATIVE:
				if (!table && this.isSpecifiedPageSize()) {
					minHeight = this.minSize.getHeight() * cHeight;
					break;
				}
			case AUTO:
				minHeight = 0;
				break;
			case ABSOLUTE:
				minHeight = this.minSize.getHeight();
				break;
			default:
				throw new IllegalStateException();
			}
			double maxHeight;
			switch (this.params.maxSize.getHeightType()) {
			case RELATIVE:
				if (!table && this.isSpecifiedPageSize()) {
					maxHeight = this.params.maxSize.getHeight() * cHeight;
					break;
				}
			case AUTO:
				maxHeight = Double.MAX_VALUE;
				break;
			case ABSOLUTE:
				maxHeight = this.params.maxSize.getHeight();
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.size.getHeightType()) {
			case RELATIVE:
				if (!table && this.isSpecifiedPageSize()) {
					this.height = this.size.getHeight() * cHeight;
					this.height = Math.max(this.height, minHeight);
					this.height = Math.min(this.height, maxHeight);
					if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
						this.height -= this.getFrame().getBorderHeight();
					}
					minHeight = this.height;
					maxHeight = this.height;
					break;
				}
			case AUTO:
				this.height = 0;
				break;
			case ABSOLUTE:
				this.height = this.size.getHeight();
				this.height = Math.max(this.height, minHeight);
				this.height = Math.min(this.height, maxHeight);
				if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
					this.height -= this.getFrame().getBorderHeight();
				}
				minHeight = this.height;
				maxHeight = this.height;
				break;
			default:
				throw new IllegalStateException();
			}
			this.minPageAxis = minHeight;
			this.maxPageAxis = maxHeight;
		}

		assert !StyleUtils.isNone(this.width);
		assert !StyleUtils.isNone(this.height);
	}

	public void finishLayout(IFramedBox containerBox) {
		// 位置の計算
		AbstractStaticPos pos = this.getStaticPos();
		if (pos.offset != null) {
			//
			// ■ 相対配置の位置の計算
			//
			this.offsetX = StyleUtils.computeOffsetX(pos.offset, containerBox);
			this.offsetY = StyleUtils.computeOffsetY(pos.offset, containerBox);
		}
		super.finishLayout(containerBox);
	}
}
