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
			// 直交ブロック(親と書字方向の軸が違う)のページ軸%の基準は
			// 親の線軸で、これは常に確定している——ここを親のページ軸
			// (isSpecifiedPageSize)で判定すると縦書き文書内の横ブロックの
			// height:100%が未確定扱い→AUTOフォールスルーで0になり、
			// firstPassLayoutが親線軸基準で出した正しい値を潰す(2026-08-10、
			// 実書籍の資料図版ページ全滅で発見)
			final boolean orthogonal = cParams.flow.isVertical() != flow.isVertical();
			this.specifiedPageAxis = pageType == LengthType.ABSOLUTE || (pageType.needsReference() && (!table
					&& (this.getPos().getType() == PosType.INLINE || orthogonal
							|| containerBox.isSpecifiedPageSize())));
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
		if (this.size.getLineType(flow) == LengthType.AUTO) {
			double limitLine;
			if (cParams.flow.isVertical() == flow.isVertical() || containerBox.isSpecifiedPageSize()) {
				limitLine = cLine - this.frame.getFrameLineExtent(flow);
			} else {
				// 親の幅が不確定の場合はページ寸法を限度とする。基準は
				// ページの**内容域**(マージンの内側)——物理寸法を使うと
				// fit-contentがマージンへ食い込む幅を許してしまう
				// (2026-08-10、縦書き書籍の資料図版ページで実測)
				final AbstractContainerBox fixedLineBox = flow.isVertical() ? layoutStack.getFixedHeightFlowBox()
						: layoutStack.getFixedWidthFlowBox();
				limitLine = (fixedLineBox != null ? fixedLineBox.getInnerLineExtent(flow)
						: (flow.isVertical() ? layoutStack.getFixedHeight() : layoutStack.getFixedWidth()))
						- this.frame.getFrameLineExtent(flow);
			}
			lineExtent = Sizing.fitContent(minLineAxis, lineExtent, limitLine);
			if (!table && sizes.columnInflated() && limitLine > 0 && lineExtent > limitLine) {
				// **段数倍で膨らんだ最小内容寸法で紙の行軸を超えない**
				// (2026-07-28)。
				//
				// `fit-content`は`max(min-content, min(available, max-content))`
				// なので、**最小内容寸法が使える空間より大きいとそれがそのまま
				// 採用される**。画面のブラウザではそれで正しい——はみ出した
				// ぶんはスクロールで読める。しかし紙には続きがない。しかも
				// **行軸は分割できない**(ページ分割はページ軸にしか効かない)
				// ので、行軸をはみ出した内容は次のページへ送られるのではなく、
				// 紙の外の座標にそのまま描かれる。
				//
				// 段組の最小内容寸法は「段数 × 中身の最小内容寸法 + 段間」
				// ——**段数倍に膨らみ**、入れ子にすれば積で効く。実測
				// (2026-07-28、seed 25503): 200pt紙に高さ823ptのフロート
				// (= 4段 × 196pt + 3 × 13pt)ができ、内容が y=-623 に
				// 描かれた。**段は狭くできる**(行軸を段数で割り直すだけ)
				// ので、この下限は守らなくてよい。段は細くなるが紙には載る
				// ——横書きがこの欠陥を1件も出さないのと同じ状態になる。
				//
				// **段数倍が効いたときだけ**にするのが肝心
				// ({@code columnInflated})。`height:150mm`の画像のように
				// 作者が明示した不可分な箱から来た最小内容寸法まで縮めると、
				// 箱だけ縮んで中身は縮まず、**はみ出しが増える**。実測で
				// 400pt紙の`writing-mode:vertical-rl`の箱が425.2→316ptに
				// 縮み、画像が段送りされずその場ではみ出した
				// (`WritingModeColumnTest`)。
				//
				// 明示された`min-*`は下でこの値を上書きするので、作者の
				// 指定は従来どおり通る。
				lineExtent = limitLine;
			}
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
		// していた。ページ方向%の基準は論理ページ軸の内寸(縦書き=幅)。
		// ただし直交ブロックのページ軸は親の線軸に一致するため、基準は
		// 包含ブロックの線軸内寸(2026-08-10、specifiedPageAxisの直交条件と対)
		final BlockParams cParams = containerBox.getBlockParams();
		final double cPage = (cParams.flow.isVertical() != flow.isVertical())
				? containerBox.getInnerLineExtent(cParams.flow)
				: fixedPageBox.getInnerPageExtent(flow);
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
