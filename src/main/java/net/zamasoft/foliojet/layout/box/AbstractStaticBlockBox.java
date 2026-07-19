package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.sizing.Sizing;
import net.zamasoft.foliojet.layout.sizing.SizingContext;
import net.zamasoft.foliojet.layout.sizing.SizingMode;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

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
			this.specifiedPageAxis = pageType == LengthType.ABSOLUTE || (pageType.needsReference() && (!table
					&& (this.getPos().getType() == PosType.INLINE || containerBox.isSpecifiedPageSize())));
		}

		//
		// ■ パディングの計算
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ マージンの計算
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		//
		// ■ 行方向幅の計算
		//
		// 論理軸(行方向/ページ方向)で計算し、末尾で物理寸法へ書き戻す。
		final SizingContext context = this.fitContentContext(layoutStack, containerBox, table);
		final double cLine = context.availableLine();

		// 行方向: fit-content と min/max クランプ
		double lineExtent = LayoutUtils.computeDimensionLine(this.size, flow, cLine);
		if (LayoutUtils.isNone(lineExtent)) {
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
		final double maxLine = LayoutUtils.computeDimensionLine(this.params.maxSize, flow, cLine);
		if (!LayoutUtils.isNone(maxLine) && lineExtent > maxLine) {
			lineExtent = maxLine;
		}
		final double minLine = LayoutUtils.computeDimensionLine(this.minSize, flow, cLine);
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
			// percentBasePage未確定ならAUTOへフォールスルー(既存の意図的な仕様)
		case AUTO:
			minPage = 0;
			break;
		case ABSOLUTE:
			minPage = this.minSize.getPageLength(flow);
			break;
		case MIXED:
			if (context.isPagePercentDefinite()) {
				minPage = this.minSize.getPageLength(flow) + this.minSize.getPageRatio(flow) * context.percentBasePage();
				break;
			}
			minPage = 0;
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
			// percentBasePage未確定ならAUTOへフォールスルー(既存の意図的な仕様)
		case AUTO:
			maxPage = Double.MAX_VALUE;
			break;
		case ABSOLUTE:
			maxPage = this.params.maxSize.getPageLength(flow);
			break;
		case MIXED:
			if (context.isPagePercentDefinite()) {
				maxPage = this.params.maxSize.getPageLength(flow)
						+ this.params.maxSize.getPageRatio(flow) * context.percentBasePage();
				break;
			}
			maxPage = Double.MAX_VALUE;
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
			// percentBasePage未確定ならAUTOへフォールスルー(既存の意図的な仕様)
		case AUTO:
			// 台帳#4 解消(2026-07-17): 旧実装は縦書きのテーブル時のみ
			// 既値を維持していた。横書きと同じく常に0(内容が後で決める)
			pageExtent = 0;
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
		case MIXED:
			if (context.isPagePercentDefinite()) {
				pageExtent = this.size.getPageLength(flow) + this.size.getPageRatio(flow) * context.percentBasePage();
				pageExtent = Math.max(pageExtent, minPage);
				pageExtent = Math.min(pageExtent, maxPage);
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					pageExtent -= this.getFrame().getBorderPageExtent(flow);
				}
				minPage = maxPage = pageExtent;
				break;
			}
			pageExtent = 0;
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

		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
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
		// 台帳#3 解消(2026-07-17): 旧実装は縦書きでも InnerHeight を参照
		// していた。ページ方向%の基準は論理ページ軸の内寸(縦書き=幅)
		final double cPage = fixedPageBox.getInnerPageExtent(flow);
		final double cLine = table ? containerBox.getInnerLineExtent(flow)
				: (flow.isVertical() ? layoutStack.getFixedHeight() : layoutStack.getFixedWidth());
		// ページ方向の%は基準が確定している場合のみ解決する
		final double pagePercentBase = (!table && this.isSpecifiedPageSize()) ? cPage : LayoutUtils.NONE;
		return new SizingContext(SizingMode.FIT_CONTENT, cLine, cLine, pagePercentBase);
	}

	public void finishLayoutSelf(IFramedBox containerBox) {
		// 位置の計算
		AbstractStaticPos pos = this.getStaticPos();
		if (pos.offset != null) {
			//
			// ■ 相対配置の位置の計算
			//
			this.offsetX = LayoutUtils.computeOffsetX(pos.offset, containerBox);
			this.offsetY = LayoutUtils.computeOffsetY(pos.offset, containerBox);
		}
	}
}
