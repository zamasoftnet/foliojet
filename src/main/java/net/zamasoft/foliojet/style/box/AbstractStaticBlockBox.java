package net.zamasoft.foliojet.style.box;

import net.zamasoft.foliojet.style.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.style.sizing.Sizing;
import net.zamasoft.foliojet.style.sizing.SizingContext;
import net.zamasoft.foliojet.style.sizing.SizingMode;

import net.zamasoft.foliojet.style.box.params.BoxSizingMode;

import net.zamasoft.foliojet.style.box.content.Container;
import net.zamasoft.foliojet.style.box.params.LengthType;
import net.zamasoft.foliojet.style.box.params.PosType;
import net.zamasoft.foliojet.style.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.WritingMode;

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

	public void shrinkToFit(LayoutStack layoutStack, IntrinsicSizes sizes, boolean table) {
		final double minLineAxis = sizes.minContent(), maxLineAxis = sizes.maxContent();
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
		final WritingMode flow = this.params.flow;
		{
			final LengthType pageType = this.params.size.getPageType(flow);
			this.specifiedPageAxis = pageType == LengthType.ABSOLUTE || (pageType == LengthType.RELATIVE && (!table
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
		// 論理軸(行方向/ページ方向)で計算し、末尾で物理寸法へ書き戻す。
		final SizingContext context = this.fitContentContext(layoutStack, containerBox, table);
		final double cLine = context.availableLine();

		// 行方向: fit-content と min/max クランプ
		double lineExtent = StyleUtils.computeDimensionLine(this.size, flow, cLine);
		if (StyleUtils.isNone(lineExtent)) {
			lineExtent = maxLineAxis;
		} else {
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				lineExtent -= this.frame.getBorderLineExtent(flow);
			}
		}
		if ((this.size.getLineType(flow) == LengthType.AUTO) &&
		// 縦中横が拡張されるようにページ方向が固定されていないとみなす。
				containerBox.getSubtype() != BoxSubtype.RUBY_BODY) {
			double limitLine;
			if (cParams.flow.isVertical() == flow.isVertical() || containerBox.isSpecifiedPageSize()) {
				limitLine = cLine - this.frame.getFrameLineExtent(flow);
			} else {
				// 親の幅が不確定の場合はページ寸法を限度とする
				limitLine = (flow.isVertical() ? layoutStack.getFixedHeight() : layoutStack.getFixedWidth())
						- this.frame.getFrameLineExtent(flow);
			}
			lineExtent = Sizing.fitContent(minLineAxis, lineExtent, limitLine);
		}
		final double maxLine = StyleUtils.computeDimensionLine(this.params.maxSize, flow, cLine);
		if (!StyleUtils.isNone(maxLine) && lineExtent > maxLine) {
			lineExtent = maxLine;
		}
		final double minLine = StyleUtils.computeDimensionLine(this.minSize, flow, cLine);
		if (lineExtent < minLine) {
			lineExtent = minLine;
		}

		// ページ方向: min/max と指定寸法。%は percentBasePage が確定している場合のみ解決する
		double minPage;
		switch (this.minSize.getPageType(flow)) {
		case RELATIVE:
			if (context.isPagePercentDefinite()) {
				minPage = this.minSize.getPageLength(flow) * context.percentBasePage();
				break;
			}
		case AUTO:
			minPage = 0;
			break;
		case ABSOLUTE:
			minPage = this.minSize.getPageLength(flow);
			break;
		default:
			throw new IllegalStateException();
		}
		double maxPage;
		switch (this.params.maxSize.getPageType(flow)) {
		case RELATIVE:
			if (context.isPagePercentDefinite()) {
				maxPage = this.params.maxSize.getPageLength(flow) * context.percentBasePage();
				break;
			}
		case AUTO:
			maxPage = Double.MAX_VALUE;
			break;
		case ABSOLUTE:
			maxPage = this.params.maxSize.getPageLength(flow);
			break;
		default:
			throw new IllegalStateException();
		}
		double pageExtent = flow.isVertical() ? this.width : this.height;
		switch (this.size.getPageType(flow)) {
		case RELATIVE:
			if (context.isPagePercentDefinite()) {
				pageExtent = this.size.getPageLength(flow) * context.percentBasePage();
				pageExtent = Math.max(pageExtent, minPage);
				pageExtent = Math.min(pageExtent, maxPage);
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					pageExtent -= this.getFrame().getBorderPageExtent(flow);
				}
				minPage = maxPage = pageExtent;
				break;
			}
		case AUTO:
			// 既存挙動: 横書きは常に0、縦書きはテーブル時のみ既値を維持
			if (!table || !flow.isVertical()) {
				pageExtent = 0;
			}
			break;
		case ABSOLUTE:
			pageExtent = this.size.getPageLength(flow);
			pageExtent = Math.max(pageExtent, minPage);
			pageExtent = Math.min(pageExtent, maxPage);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				pageExtent -= this.getFrame().getBorderPageExtent(flow);
			}
			minPage = maxPage = pageExtent;
			break;
		default:
			throw new IllegalStateException();
		}
		this.minPageAxis = minPage;
		this.maxPageAxis = maxPage;

		// 物理寸法へ書き戻し
		if (flow.isVertical()) {
			this.height = lineExtent;
			this.width = pageExtent;
		} else {
			this.width = lineExtent;
			this.height = pageExtent;
		}

		assert !StyleUtils.isNone(this.width);
		assert !StyleUtils.isNone(this.height);
	}

	/**
	 * fit-content サイズ決定のための制約空間を包含コンテキストから導出します。
	 * 呼び出し前に specifiedPageAxis が確定している必要があります。
	 *
	 * @param layoutStack  レイアウトスタック
	 * @param containerBox 包含ブロック
	 * @param table        テーブル文脈であればtrue
	 * @return 制約空間
	 */
	private SizingContext fitContentContext(LayoutStack layoutStack, AbstractContainerBox containerBox, boolean table) {
		final WritingMode flow = this.params.flow;
		// ページ方向の基準ボックス。
		AbstractContainerBox fixedPageBox = flow.isVertical() ? layoutStack.getFixedWidthFlowBox()
				: layoutStack.getFixedHeightFlowBox();
		if (fixedPageBox == null) {
			fixedPageBox = containerBox;
		}
		// 注: 縦書きでも InnerHeight を参照する(鏡像なら InnerWidth)。既存挙動を温存(要調査)。
		final double cPage = fixedPageBox.getInnerHeight();
		final double cLine = table ? containerBox.getInnerLineExtent(flow)
				: (flow.isVertical() ? layoutStack.getFixedHeight() : layoutStack.getFixedWidth());
		// ページ方向の%は基準が確定している場合のみ解決する
		final double pagePercentBase = (!table && this.isSpecifiedPageSize()) ? cPage : StyleUtils.NONE;
		return new SizingContext(SizingMode.FIT_CONTENT, cLine, cLine, pagePercentBase);
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
