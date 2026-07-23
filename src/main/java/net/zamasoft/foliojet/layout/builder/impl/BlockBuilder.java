package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.content.BreakToken;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.layout.box.params.FloatSide;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;

import net.zamasoft.foliojet.layout.box.params.ClearMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.constraint.AxisSpan;
import net.zamasoft.foliojet.layout.constraint.ExclusionSpace;
import net.zamasoft.foliojet.layout.constraint.FloatExclusion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Insets;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineAbsoluteQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.TableBuilder;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextControl;

public class BlockBuilder implements Builder, LayoutContext {
	private static final Logger LOG = Logger.getLogger(BlockBuilder.class.getName());

	protected LayoutStack layoutStack;

	protected Flow contextFlow;

	/**
	 * 包含ブロックのスタック。
	 */
	protected List<Flow> flowStack = null;

	protected TextBuilder textBuilder = null;

	/**
	 * 次のテキストブロックの継続状態です。
	 */
	protected BreakToken breakToken = BreakToken.NONE;

	protected double poLastMargin = 0, neLastMargin = 0;

	/**
	 * 配置中の通常のフローのボックスの、コンテキストボックスに対する位置です。
	 */
	protected double lineAxis = 0, pageAxis = 0;

	/**
	 * 次の行またはフローの開始で追加する浮動ボックスです。
	 */
	protected List<IFloatBox> toAddFloatings = null;

	/**
	 * 追加済みの浮動ボックスです。
	 */
	protected List<Floating> floatings = null;
	/**
	 * overflow:hidden;に追加された浮動ボックスです
	 */
	private List<List<Floating>> noOverflowFloatings = null;

	/**
	 * 浮動ボックスをページ方向の底辺がページ開始位置にあるものから順に整列します。
	 */
	private static Comparator<Object> FLOAT_COMP = new Comparator<Object>() {
		public int compare(Object o1, Object o2) {
			double a, b;
			if (o1 instanceof LayoutContext.Floating) {
				LayoutContext.Floating c1 = (LayoutContext.Floating) o1;
				a = c1.pageEnd;
			} else {
				Double c1 = (Double) o1;
				a = c1.doubleValue();
			}
			if (o2 instanceof LayoutContext.Floating) {
				LayoutContext.Floating c2 = (LayoutContext.Floating) o2;
				b = c2.pageEnd;
			} else {
				Double c2 = (Double) o2;
				b = c2.doubleValue();
			}
			return (a > b) ? 1 : ((a == b) ? 0 : -1);
		}
	};

	public BlockBuilder(LayoutStack layoutStack, AbstractContainerBox contextBox) {
		this.layoutStack = layoutStack;
		if (contextBox != null) {
			this.contextFlow = new Flow(contextBox, 0, 0);
		}
	}

	public Builder getParentBuilder() {
		return (Builder) this.layoutStack;
	}

	public AbstractContainerBox getFixedWidthContextBox() {
		AbstractContainerBox box = this.getRootBox();
		if (box.getBlockParams().size.getWidthType() != LengthType.AUTO) {
			if (!box.getBlockParams().size.getWidthType().needsReference() || !box.getType().isTableInternal()) {
				return box;
			}
		}
		if (this.layoutStack == null) {
			return null;
		}
		switch (box.getPos().getType()) {
		case FLOW:
		case FLOAT:
		case INLINE:
		case TABLE_CELL:
			return this.layoutStack.getFixedWidthFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedWidthContextBox();
		default:
			throw new IllegalStateException();
		}
	}

	public AbstractContainerBox getFixedHeightContextBox() {
		AbstractContainerBox box = this.getRootBox();
		if (box.getBlockParams().size.getHeightType() != LengthType.AUTO) {
			if (!box.getBlockParams().size.getHeightType().needsReference() || !box.getType().isTableInternal()) {
				return box;
			}
		}
		if (this.layoutStack == null) {
			return null;
		}
		switch (box.getPos().getType()) {
		case FLOW:
		case FLOAT:
		case INLINE:
		case TABLE_CELL:
			return this.layoutStack.getFixedHeightFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedHeightContextBox();
		default:
			throw new IllegalStateException(String.valueOf(box.getClass()));
		}
	}

	public double getFixedWidth() {
		double frameWidth = 0;
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				BlockParams params = flow.box.getBlockParams();
				frameWidth += flow.box.getFrame().getFrameWidth();
				if (!params.flow.isVertical()) {
					// 横書き
					return flow.box.getWidth() - frameWidth;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// 幅が指定されている
					return flow.box.getWidth() - frameWidth;
				}
			}
		}
		AbstractContainerBox box = this.getFixedWidthContextBox();
		return box == null ? 0 : box.getWidth() - frameWidth;
	}

	public AbstractContainerBox getFixedWidthFlowBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				BlockParams params = flow.box.getBlockParams();
				if (!params.flow.isVertical()) {
					// 横書き
					return flow.box;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// 幅が指定されている
					return flow.box;
				}
			}
		}
		return this.getFixedWidthContextBox();
	}

	public AbstractContainerBox getFixedHeightFlowBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				final BlockParams params = flow.box.getBlockParams();
				if (params.flow.isVertical()) {
					// 縦書き
					return flow.box;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// 幅が指定されている
					return flow.box;
				}
			}
		}
		return this.getFixedHeightContextBox();
	}

	public double getFixedHeight() {
		double frameHeight = 0;
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				BlockParams params = flow.box.getBlockParams();
				frameHeight += flow.box.getFrame().getFrameHeight();
				if (params.flow.isVertical()) {
					// 縦書き
					return flow.box.getHeight() - frameHeight;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// 幅が指定されている
					return flow.box.getHeight() - frameHeight;
				}
			}
		}
		AbstractContainerBox box = this.getFixedHeightContextBox();
		return box == null ? 0 : box.getInnerHeight() - frameHeight;
	}

	public RootBuilder getPageContext() {
		return this.layoutStack.getPageContext();
	}

	private int getFloatingCount() {
		return this.floatings == null ? 0 : this.floatings.size();
	}

	private Floating getFloating(int index) {
		return (Floating) this.floatings.get(index);
	}

	public void setPageAxis(double pageAxis) {
		this.pageAxis = pageAxis;
	}

	public double getPageAxis() {
		return this.pageAxis;
	}

	public boolean isTwoPass() {
		return false;
	}

	public boolean isMain() {
		return false;
	}

	/**
	 * 現在のフローか上位にある最初のpositionが指定されているボックスを返す。
	 */
	public AbstractContainerBox getContextBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				AbstractContainerBox box = flow.box;
				if (box.isContextBox()) {
					return flow.box;
				}
			}
		}
		AbstractContainerBox box = this.contextFlow.box;
		if (this.layoutStack == null) {
			return box;
		}
		if (!box.isContextBox()) {
			return this.layoutStack.getContextBox();
		}
		return box;
	}

	public AbstractContainerBox getMulticolumnBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				if (flow.box.getColumnCount() > 1) {
					return flow.box;
				}
			}
		}
		return null;
	}

	/**
	 * 現在のフローからrootまでの間の最初のpositionが指定されているボックスを返す。
	 * 
	 * @return
	 */
	Flow getSubContextFlow() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				AbstractContainerBox box = flow.box;
				if (box.isContextBox()) {
					return flow;
				}
			}
		}
		return this.contextFlow;
	}

	public AbstractContainerBox getRootBox() {
		return this.contextFlow.box;
	}

	public AbstractContainerBox getFlowBox() {
		return this.getFlow().box;
	}

	public Flow getFlow() {
		if (this.flowStack == null || this.flowStack.isEmpty()) {
			return this.contextFlow;
		}
		return (Flow) this.flowStack.get(this.flowStack.size() - 1);
	}

	public int getFlowCount() {
		return this.flowStack == null ? 1 : this.flowStack.size() + 1;
	}

	public Flow getFlow(int index) {
		if (index == 0) {
			return this.contextFlow;
		}
		return (Flow) this.flowStack.get(index - 1);
	}

	public void startFlowBlock(final FlowBlockBox flowBox) {
		assert this.textBuilder == null;
		AbstractContainerBox containerBox = this.getFlowBox();
		double xmargin = 0;
		double lineSize = containerBox.getLineSize();
		if (flowBox.getColumnCount() > 1) {
			// マルチカラムの場合浮動ボックスを避ける(排除域は
			// ExclusionSpace queryへ一本化済み——2026-07-23、P0完了)
			final ExclusionSpace snapshot = this.snapshotExclusions();
			final AxisSpan band = snapshot.narrowLineBandForMulticol(this.pageAxis,
					new AxisSpan(this.lineAxis, this.lineAxis + lineSize));
			xmargin = band.start() - this.lineAxis;
			lineSize = band.extent();
		}
		flowBox.calculateSize(this, xmargin, lineSize);
		final FlowPos pos = flowBox.getFlowPos();

		if (flowBox.getBlockParams().overflow == OverflowMode.HIDDEN) {
			// hidden指定されたボックス内のfloatは外に影響しない
			if (this.noOverflowFloatings == null) {
				this.noOverflowFloatings = new ArrayList<List<Floating>>();
			}
			this.noOverflowFloatings.add(new ArrayList<Floating>());
		}

		final BlockParams cParams = this.getFlowBox().getBlockParams();
		final AbsoluteRectFrame frame = flowBox.getFrame();

		if (pos.clear != ClearMode.NONE && this.getFloatingCount() > 0) {
			// clearが指定されている場合
			final double marginStart;
			if (cParams.flow.isVertical()) {
				marginStart = frame.margin.right;
			} else {
				marginStart = frame.margin.top;
			}
			final double pageStart = this.pageAxis - marginStart;
			final ExclusionSpace snapshot = this.snapshotExclusions();
			final FloatExclusion found = snapshot.findClearBoundary(pageStart, marginStart, pos.clear);
			if (found != null) {
				// 浮動ボックスの下につける
				this.poLastMargin = this.neLastMargin = 0;
				this.pageAxis = found.pageSpan().end() - marginStart;
			}
		}

		// 開始位置マージンのつぶし
		// SPEC CSS 2.1 8.3.1
		// 正のマージンでは大きいほうが採用される
		// 正と負のマージンでは両方が足される
		// 負と負のマージンでは絶対値が大きいほうが採用される
		LayoutContext.Flow parentFlow = this.getFlow(this.getFlowCount() - 1);
		double marginStart, frameStart, frameHead;
		boolean bordered;
		if (cParams.flow.isVertical()) {
			// 縦書き
			marginStart = frame.margin.right;
			frameHead = frame.getFrameTop();
			frameStart = frame.getFrameRight();
			bordered = frame.padding.right > 0 || !frame.frame.border.getRight().isNull();
		} else {
			// 横書きのフロー
			marginStart = frame.margin.top;
			frameHead = frame.getFrameLeft();
			frameStart = frame.getFrameTop();
			bordered = frame.padding.top > 0 || !frame.frame.border.getTop().isNull();
		}
		if (marginStart >= 0) {
			if (marginStart > this.poLastMargin) {
				this.pageAxis -= this.poLastMargin;
				this.poLastMargin = marginStart;
			} else {
				this.pageAxis -= marginStart;
			}
		} else {
			if (marginStart < this.neLastMargin) {
				this.pageAxis -= this.neLastMargin;
				this.neLastMargin = marginStart;
			} else {
				this.pageAxis -= marginStart;
			}
		}
		if (bordered) {
			this.poLastMargin = this.neLastMargin = 0;
		}

		this.lineAxis += frameHead;
		parentFlow.box.addFlow(flowBox, this.pageAxis - parentFlow.pageAxis);
		this.pageAxis += frameStart;

		if (this.flowStack == null) {
			this.flowStack = new ArrayList<Flow>();
		}
		final Flow flow = new Flow(flowBox, this.lineAxis, this.pageAxis);
		this.flowStack.add(flow);
		this.breakToken = BreakToken.NONE;
	}

			/**
	 * 現在の{@link #floatings}を{@link ExclusionSpace}へ変換した
	 * スナップショットを返します(2026-07-23新設、P0 Step4以降は
	 * 実レイアウトに使用。{@code TextBuilder}もこれを共用する)。
	 * {@code this.floatings}は既に{@code FLOAT_COMP}(pageEnd昇順、
	 * 同値は追加順)でソート済みのため、並び順をそのまま
	 * {@link ExclusionSpace#copyOfSorted}へ渡すO(N)一括構築で足りる
	 * (2026-07-23、codexレビュー指摘のO(N²)解消)。
	 */
	ExclusionSpace snapshotExclusions() {
		final int count = this.getFloatingCount();
		if (count == 0) {
			return ExclusionSpace.EMPTY;
		}
		final List<FloatExclusion> exclusions = new ArrayList<>(count);
		for (int i = 0; i < count; ++i) {
			final LayoutContext.Floating floating = this.getFloating(i);
			final FloatPos floatingPos = floating.box.getFloatPos();
			exclusions.add(new FloatExclusion(i, floatingPos.floating,
					new AxisSpan(floating.pageStart, floating.pageEnd),
					new AxisSpan(floating.lineStart, floating.lineEnd)));
		}
		return ExclusionSpace.copyOfSorted(exclusions);
	}

							public void endFlowBlock() {
		assert this.textBuilder == null;
		final Flow flow = (Flow) this.flowStack.remove(this.flowStack.size() - 1);
		final FlowBlockBox flowBox = (FlowBlockBox) flow.box;
		final BlockParams params = flowBox.getBlockParams();

		if (flowBox.getColumnCount() > 1 && params.columns.fill == Columns.FILL_BALANCE) {
			// カラムのバランス
			this.pageAxis = flow.pageAxis;
			flowBox.balance(this);
		}
		if (flowBox.getBlockParams().overflow == OverflowMode.HIDDEN) {
			// hidden指定されたボックス内のfloatは外に影響しない
			List<?> floatings = (List<?>) this.noOverflowFloatings.remove(this.noOverflowFloatings.size() - 1);
			if (this.floatings != null) {
				this.floatings.removeAll(floatings);
			}
		}

		final Flow parentFlow = this.getFlow();
		final BlockParams parentParams = parentFlow.box.getBlockParams();
		final AbsoluteRectFrame frame = flowBox.getFrame();
		final double marginEnd, frameEnd, frameHead;
		boolean bordered;
		if (parentParams.flow.isVertical()) {
			// 縦書き
			marginEnd = frame.margin.left;
			bordered = frame.padding.left > 0 || !frame.frame.border.getLeft().isNull()
					|| params.overflow != OverflowMode.VISIBLE || flowBox.getColumnCount() > 1;
			double width = flowBox.getInnerWidth();
			if (flowBox.getContentSize() != width || bordered) {
				this.pageAxis = flow.pageAxis + width;
				if (params.size.getWidthType() == LengthType.ABSOLUTE && width > 0) {
					bordered = true;
				}
			}
			frameEnd = frame.getFrameLeft();
			frameHead = frame.getFrameTop();
		} else {
			// 横書き
			marginEnd = frame.margin.bottom;
			bordered = frame.padding.bottom > 0 || !frame.frame.border.getBottom().isNull()
					|| params.overflow != OverflowMode.VISIBLE || flowBox.getColumnCount() > 1;
			double height = flowBox.getInnerHeight();
			if (flowBox.getContentSize() != height || bordered) {
				this.pageAxis = flow.pageAxis + height;
				if (params.size.getHeightType() == LengthType.ABSOLUTE && height > 0) {
					bordered = true;
				}
			}
			frameEnd = frame.getFrameBottom();
			frameHead = frame.getFrameLeft();
		}
		if (bordered) {
			if (marginEnd >= 0) {
				this.poLastMargin = marginEnd;
				this.neLastMargin = 0;
			} else {
				this.poLastMargin = 0;
				this.neLastMargin = marginEnd;
			}
		} else {
			if (marginEnd >= 0) {
				if (marginEnd > this.poLastMargin) {
					this.pageAxis -= this.poLastMargin;
					this.poLastMargin = marginEnd;
				} else {
					this.pageAxis -= marginEnd;
				}
			} else {
				if (marginEnd < this.neLastMargin) {
					this.pageAxis -= this.neLastMargin;
					this.neLastMargin = marginEnd;
				} else {
					this.pageAxis -= marginEnd;
				}
			}
		}
		this.pageAxis += frameEnd;

		parentFlow.box.setPageAxis(this.pageAxis - parentFlow.pageAxis);
		this.lineAxis -= frameHead;
	}

	public void addBound(IBox box) {
		switch (box.getPos().getType()) {
		case FLOW:
		case TABLE: {
			// 通常のフロー
			assert this.textBuilder == null;
			IFlowBox flowBox = (IFlowBox) box;

			Flow flow = this.getFlow();
			BlockParams params = flow.box.getBlockParams();
			boolean vertical = params.flow.isVertical();
			AbsoluteInsets amargin;
			AbsoluteRectFrame frame;
			ClearMode clear;
			Align align;
			switch (box.getType()) {
			case REPLACED: {
				AbstractReplacedBox replacedBox = (AbstractReplacedBox) flowBox;
				LayoutUtils.calculateReplacedSize(this, replacedBox);
				frame = replacedBox.getFrame();
				FlowPos pos = (FlowPos) flowBox.getPos();
				clear = pos.clear;
				align = pos.align;
			}
				break;
			case BLOCK: {
				AbstractBlockBox blockBox = (AbstractBlockBox) flowBox;
				frame = blockBox.getFrame();
				FlowPos pos = (FlowPos) flowBox.getPos();
				clear = pos.clear;
				// 表整列の解決結果は箱ローカル(共有 pos は record 後不変)
				align = blockBox instanceof FlowBlockBox fb ? fb.getResolvedAlign() : pos.align;
			}
				break;
			case TABLE: {
				TableBox tableBox = (TableBox) flowBox;
				frame = tableBox.getFrame();
				clear = ClearMode.NONE;
				align = null;
			}
				break;
			default:
				throw new IllegalStateException();
			}
			Insets margin = frame.frame.margin;
			amargin = frame.margin;
			double lineSize = box.getLineExtent(params.flow);
			final double cLineSize = flow.box.getLineSize();
			final double lineStop = this.lineAxis + cLineSize;
			double xMarginStart = 0, lineEnd = lineStop, xMarginEnd = 0;
			if (this.getFloatingCount() > 0) {
				// clearのチェックと置換ボックスやテーブルが浮動ボックスと重ならない処理
				// *** CLEAR_NONEもチェックしていることに注意 ***
				final double pageStart;
				final double marginAdjust;
				if (vertical) {
					marginAdjust = amargin.right;
				} else {
					marginAdjust = amargin.top;
				}
				pageStart = this.pageAxis - marginAdjust;
				final ExclusionSpace snapshot = this.snapshotExclusions();
				final ExclusionSpace.BoundAvoidance found = snapshot.findBoundAvoidance(pageStart, lineSize, lineStop,
						marginAdjust, clear);
				xMarginStart = found.xMarginStart();
				lineEnd = found.lineEnd();
				if (found.clearingExclusion() != null) {
					this.poLastMargin = this.neLastMargin = 0;
					this.pageAxis = found.clearPageEnd();
				}
			}
			xMarginEnd = lineStop - lineEnd;

			//
			// ■ 通常のフローのマージンの計算
			//
			if (align != null) {
				double frameSize, marginStart, marginEnd;
				if (vertical) {
					frameSize = frame.getFrameHeight();
					marginStart = margin.getTopType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.top;
					marginEnd = margin.getBottomType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.bottom;
				} else {
					frameSize = frame.getFrameWidth();
					marginStart = margin.getLeftType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.left;
					marginEnd = margin.getRightType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.right;
				}
				lineSize -= frameSize;
				if (LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
					// 左右のマージンを同じにする
					marginStart = marginEnd = (cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd) / 2.0;
				} else if (LayoutUtils.isNone(marginStart)) {
					// 左が不確定
					marginStart = cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
				} else if (LayoutUtils.isNone(marginEnd)) {
					// 右が不確定
					marginEnd = cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
				} else {
					// 制限しすぎ
					switch (align) {
					case Align.START:
						// 左寄せ
						marginEnd = 0;
						break;
					case Align.END:
						// 右寄せ
						marginStart += cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
						break;
					case Align.CENTER:
						// 中央
						double remainder = cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
						remainder /= 2.0;
						marginStart += remainder;
						marginEnd += remainder;
						break;
					default:
						throw new IllegalStateException();
					}
				}
				if (vertical) {
					amargin.top = marginStart + xMarginStart;
					amargin.bottom = marginEnd + xMarginEnd;
				} else {
					amargin.left = marginStart + xMarginStart;
					amargin.right = marginEnd + xMarginEnd;
				}
			}
			if (amargin.top >= 0) {
				if (amargin.top > this.poLastMargin) {
					this.pageAxis -= this.poLastMargin;
					this.poLastMargin = amargin.top;
				} else {
					this.pageAxis -= amargin.top;
				}
			} else {
				if (amargin.top < this.neLastMargin) {
					this.pageAxis -= this.neLastMargin;
					this.neLastMargin = amargin.top;
				} else {
					this.pageAxis -= amargin.top;
				}
			}
			if (vertical) {
				this.poLastMargin = this.neLastMargin = amargin.left;
			} else {
				this.poLastMargin = this.neLastMargin = amargin.bottom;
			}
			flow.box.addFlow(flowBox, this.pageAxis - flow.pageAxis);

			this.pageAxis += flowBox.getPageExtent(params.flow);
			flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
		}
			break;

		case FLOAT: {
			if (box.getType() == BoxType.REPLACED) {
				AbstractReplacedBox replacedBox = (AbstractReplacedBox) box;
				LayoutUtils.calculateReplacedSize(this, replacedBox);
			}

			// 浮動体
			final IFloatBox floatBox = (IFloatBox) box;
			if (this.textBuilder != null && this.textBuilder.getLineAxis() > 0) {
				this.toAddFloating(floatBox);
			} else {
				this.addFloating(floatBox);
			}
		}
			break;

		case ABSOLUTE: {
			// 絶対位置
			final IAbsoluteBox absoluteBox = (IAbsoluteBox) box;
			final AbsolutePos pos = absoluteBox.getAbsolutePos();
			final AbstractContainerBox contextBox;
			{
				// 通常の絶対配置
				// 固定配置
				final Flow flow = this.getFlow();
				contextBox = flow.box;
				double staticX = this.lineAxis - flow.lineAxis;
				double staticY = this.pageAxis - flow.pageAxis;
				if (this.textBuilder != null) {
					assert pos.autoPosition == AutoPosition.BLOCK : box.getParams();
					staticY += this.textBuilder.getActualPageAxis();
				}
				contextBox.addAbsolute(absoluteBox, staticX, staticY);
			}
			if (box.getType() == BoxType.REPLACED) {
				final AbstractReplacedBox replacedBox = (AbstractReplacedBox) box;
				replacedBox.calculateFrame(contextBox.getLineSize());
			}
		}
			break;

		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * 左浮動体の位置を設定します。
	 * 
	 * @param box
	 */
	protected void addStartFloat(IFloatBox box) {
		// 1.浮動ボックスの基準となる左右の辺は包含ボックスからはみ出さない
		// 2.浮動ボックスの次に浮動ボックスがある場合、後の浮動ボックスは前の浮動ボックスの横に並ぶか下につくかのどちらかである
		// 3.左右の浮動ボックスが重なることはない
		// 4.浮動ボックスの上辺は包含ボックスからはみ出さない
		// 5.浮動ボックスの上辺は以前に現れたブロックボックスの上辺より上にはならない
		// 6.浮動ボックスの上辺は以前に現れたボックスを包含する行ボックスの上辺より上にはならない
		// 7.浮動ボックスは最も端にある場合を除き、包含ボックスの左右の辺をはみ出してはならない
		// 8.浮動ボックスは第一になるべく高く、第二になるべく端に位置しなければならない
		// 浮動体はこれより上にはならない
		WritingMode progression = this.getRootBox().getBlockParams().flow;
		double lineWidth = box.getLineExtent(progression);
		double pageWidth = box.getPageExtent(progression);
		double pageStart = this.pageAxis;
		if (this.textBuilder != null) {
			pageStart += this.textBuilder.getActualPageAxis();
		}
		double lineStart = this.lineAxis;
		if (this.getFloatingCount() > 0) {
			LayoutContext.Floating lastFloating = (LayoutContext.Floating) this.floatings
					.get(this.floatings.size() - 1);
			pageStart = Math.max(pageStart, lastFloating.pageStart);

			FloatPos pos = box.getFloatPos();
			// floatingsはこのループ中不変なのでスナップショットはループ外で
			// 1回だけ取る。
			final ExclusionSpace snapshot = this.snapshotExclusions();
			for (;;) {
				final double lineEnd0 = this.lineAxis + this.getFlowBox().getLineSize();
				final ExclusionSpace.FloatPlacementScan found = snapshot.scanFloatPlacementBand(pageStart,
						this.lineAxis, lineEnd0, pos.clear);
				pageStart = found.pageStart();
				lineStart = found.lineStart();
				final double lineEnd = found.lineEnd();
				double width = lineEnd - lineStart;
				if (LayoutUtils.compare(width, lineWidth) >= 0) {
					// 幅に余裕がある
					break;
				}

				// 余裕がない場合は１つ下りて再探索
				if (found.startExclusion() == null && found.endExclusion() == null) {
					break;
				}
				if (found.endExclusion() == null) {
					pageStart = found.startExclusion().pageSpan().end();
				} else if (found.startExclusion() == null) {
					pageStart = found.endExclusion().pageSpan().end();
				} else {
					double lineStartPageEnd = found.startExclusion().pageSpan().end();
					double lineEndPageEnd = found.endExclusion().pageSpan().end();
					if (lineStartPageEnd > lineEndPageEnd) {
						pageStart = lineEndPageEnd;
					} else {
						pageStart = lineStartPageEnd;
					}
				}
			}
		}
		final FloatCommitKind kind = this.classifyFloatPlacement(box, pageStart);
		if (kind != FloatCommitKind.PLACED) {
			this.recordBreakFloat(box.getFloatPos().floating);
		}
		// 配置
		Flow flow = this.getFlow();
		flow.box.addFloating(box, lineStart - flow.lineAxis, pageStart - flow.pageAxis);
		if (kind != FloatCommitKind.MOVE_TO_NEXT) {
			LayoutContext.Floating floating = new LayoutContext.Floating(box, lineStart, pageStart, progression);
			this.addFloating(floating);
		}
		// 上位ボックスの幅の拡張
		this.extendParents(pageStart, pageWidth);
	}

	/**
	 * 右浮動体の位置を設定します。
	 * 
	 * @param box
	 */
	protected void addEndFloat(IFloatBox box) {
		// 浮動体はこれより上にはならない
		WritingMode progression = this.getRootBox().getBlockParams().flow;
		double lineWidth = box.getLineExtent(progression);
		double pageWidth = box.getPageExtent(progression);
		double pageStart = this.pageAxis;
		if (this.textBuilder != null) {
			pageStart += this.textBuilder.getActualPageAxis();
		}
		double lineEnd = this.lineAxis + this.getFlowBox().getLineSize();
		if (this.getFloatingCount() > 0) {
			LayoutContext.Floating lastFloating = (LayoutContext.Floating) this.floatings
					.get(this.floatings.size() - 1);
			pageStart = Math.max(pageStart, lastFloating.pageStart);

			FloatPos pos = box.getFloatPos();
			// addStartFloatと同様、スナップショットはループ外で1回だけ取る。
			final ExclusionSpace snapshot = this.snapshotExclusions();
			for (;;) {
				final double lineStart0 = this.lineAxis;
				final double lineEnd0 = this.lineAxis + this.getFlowBox().getLineSize();
				final ExclusionSpace.FloatPlacementScan found = snapshot.scanFloatPlacementBand(pageStart, lineStart0,
						lineEnd0, pos.clear);
				pageStart = found.pageStart();
				lineEnd = found.lineEnd();
				final double lineStart = found.lineStart();
				double width = lineEnd - lineStart;
				if (LayoutUtils.compare(width, lineWidth) >= 0) {
					// 幅に余裕がある
					break;
				}
				// 余裕がない場合は１つ下りて再探索
				if (found.startExclusion() == null && found.endExclusion() == null) {
					break;
				}
				if (found.endExclusion() == null) {
					pageStart = found.startExclusion().pageSpan().end();
				} else if (found.startExclusion() == null) {
					pageStart = found.endExclusion().pageSpan().end();
				} else {
					double leftBottom = found.startExclusion().pageSpan().end();
					double rightBottom = found.endExclusion().pageSpan().end();
					if (leftBottom > rightBottom) {
						pageStart = rightBottom;
					} else {
						pageStart = leftBottom;
					}
				}
			}
		}
		lineEnd -= lineWidth;

		final FloatCommitKind kind = this.classifyFloatPlacement(box, pageStart);
		if (kind != FloatCommitKind.PLACED) {
			this.recordBreakFloat(box.getFloatPos().floating);
		}
		// 配置
		Flow flow = this.getFlow();
		flow.box.addFloating(box, lineEnd - flow.lineAxis, pageStart - flow.pageAxis);
		if (kind != FloatCommitKind.MOVE_TO_NEXT) {
			LayoutContext.Floating floating = new LayoutContext.Floating(box, lineEnd, pageStart, progression);
			this.addFloating(floating);
		}
		// 上位ボックスの幅の拡張
		this.extendParents(pageStart, pageWidth);
	}

				private void extendParents(final double pageStart, final double pageWidth) {
		// 浮動ボックスによる上位ボックスの幅の拡張
		Flow contextFlow = this.getSubContextFlow();
		double pageAxis = pageStart + pageWidth;
		int i;
		if (this.flowStack != null) {
			i = this.flowStack.size() - 1;
			for (; i >= 0; --i) {
				contextFlow = (Flow) this.flowStack.get(i);
				final BlockParams params = contextFlow.box.getBlockParams();
				if (params.overflow == OverflowMode.HIDDEN) {
					contextFlow.box.setPageAxis(pageAxis - contextFlow.pageAxis);
					if (params.overflow == OverflowMode.HIDDEN) {
						// overflowで高さが指定されている場合は、外に影響しない
						pageAxis = contextFlow.box.getInnerPageExtent(contextFlow.box.getBlockParams().flow)
								+ contextFlow.pageAxis;
					}
				}
			}
		} else {
			i = -1;
		}
		if (i == -1) {
			AbstractContainerBox rootBox = this.getRootBox();
			rootBox.setPageAxis(pageAxis);
		}
	}

	/**
	 * 浮動体の追加を予約します。
	 * 
	 * @param box
	 */
	private void toAddFloating(IFloatBox box) {
		if (this.toAddFloatings == null) {
			this.toAddFloatings = new ArrayList<IFloatBox>();
		}
		this.toAddFloatings.add(box);
		if (this.textBuilder == null) {
			this.checkFloatings();
		}
	}

	/**
	 * 予約された浮動体が存在すれば追加します。
	 */
	void checkFloatings() {
		if (this.toAddFloatings == null || this.toAddFloatings.isEmpty()) {
			return;
		}
		for (Iterator<IFloatBox> i = this.toAddFloatings.iterator(); i.hasNext();) {
			IFloatBox box = (IFloatBox) i.next();
			i.remove();
			this.addFloating(box);
		}
	}

	private void addFloating(IFloatBox box) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("Add float: " + box.getParams().element + "/" + this.getFlow().box.getParams().element);
		}
		FloatPos pos = box.getFloatPos();
		switch (pos.floating) {
		case FloatSide.START:
			this.addStartFloat(box);
			break;
		case FloatSide.END:
			this.addEndFloat(box);
			break;
		default:
			throw new IllegalStateException();
		}
		if (this.floatings != null) {
			// 底辺を下から順に整列
			// このソートは安定ソートである必要があります
			Collections.sort(this.floatings, FLOAT_COMP);
		}
	}

	private void addFloating(LayoutContext.Floating floating) {
		if (this.floatings == null) {
			this.floatings = new ArrayList<Floating>();
		}
		this.floatings.add(floating);
		if (this.noOverflowFloatings != null && !this.noOverflowFloatings.isEmpty()) {
			List<Floating> floatings = (List<Floating>) this.noOverflowFloatings
					.get(this.noOverflowFloatings.size() - 1);
			floatings.add(floating);
		}
	}

	/**
	 * fragment境界(改ページ・改段)通過後に、overflow:hiddenのfloat台帳
	 * ({@link #noOverflowFloatings})を現在のflowStack上のactive hidden
	 * flowから再構築します(2026-07-23、排除域P1増分1)。
	 *
	 * <p>
	 * 従来は{@code resetFragmentCursor()}が{@code floatings}だけをnullに
	 * 戻しこの台帳には触れなかったため、PAGE改ページ(flowStack.clear()+
	 * resume再駆動でhidden flowが台帳を再pushする経路)では旧断片の
	 * スコープエントリがpopされないまま残留し続けた——レイアウト結果には
	 * 影響しない(popは常に末尾=新エントリ、removeAllは空振り)が、
	 * ページ数×hidden深さ×float数で旧Floating/IFloatBox参照が文書終了
	 * までGCできなかった(codexレビュー指摘)。
	 * </p>
	 *
	 * <p>
	 * PAGE({@code flowStack.clear()}直後に呼ぶ)では結果はnull——再開される
	 * hidden flowが{@code startFlowBlock()}で新しい台帳を積む。COLUMN
	 * ({@code pruneFlowStackTo()}+{@code resetFragmentCursor()}直後に呼ぶ)
	 * では、保持されたhidden flowの数だけ空の台帳を積み直す——それらの
	 * flowは再度{@code startFlowBlock()}されないため、積み直さないと
	 * 終了時のpopが破綻する。どちらも{@code floatings}自体はリセット済み
	 * (assertで検査)のため、旧floatを引き継ぐ必要はなく空台帳で十分
	 * (owner付けは不要)。
	 * </p>
	 */
	protected final void rebuildNoOverflowFloatingScopes() {
		assert this.floatings == null;
		this.noOverflowFloatings = null;
		if (this.flowStack == null) {
			return;
		}
		for (int i = 0; i < this.flowStack.size(); ++i) {
			final Flow flow = (Flow) this.flowStack.get(i);
			if (flow.box.getBlockParams().overflow == OverflowMode.HIDDEN) {
				if (this.noOverflowFloatings == null) {
					this.noOverflowFloatings = new ArrayList<List<Floating>>();
				}
				this.noOverflowFloatings.add(new ArrayList<Floating>());
			}
		}
	}

	public void addTable(final TableBuilder tableBuilder) {
		final RetainedTableBuilder autoTableBuilder = (RetainedTableBuilder) tableBuilder;
		autoTableBuilder.prepareLayout();
		autoTableBuilder.bind(this);
	}

	public Builder newBuilder(AbstractBlockBox blockBox) {
		final Builder builder;
		AbstractContainerBox containerBox;
		switch (blockBox.getPos().getType()) {
		case FLOW:
		case TABLE_CAPTION:
			if (blockBox.isFixedMulticolumn()) {
				final FlowBlockBox flowBox = (FlowBlockBox) blockBox;
				containerBox = this.getFlowBox();
				flowBox.calculateSize(this, 0, containerBox.getLineSize());
				return new ColumnBuilder(this, blockBox);
			}
			// フロー（ページ進行方向が違う場合）
		case FLOAT:
		case INLINE: {
			// 浮動体
			// インライン配置
			final AbstractStaticBlockBox staticBlockBox = (AbstractStaticBlockBox) blockBox;
			containerBox = this.getFlowBox();
			if (!LayoutUtils.needsIntrinsicSizing(containerBox, blockBox)) {
				// 固定幅
				staticBlockBox.shrinkToFit(this, IntrinsicSizes.ZERO, false);
				if (blockBox.isFixedMulticolumn()) {
					// ページ方向が固定されたマルチカラム
					builder = new ColumnBuilder(this, blockBox);
				} else {
					builder = new BlockBuilder(this, blockBox);
				}
			} else {
				// STF
				blockBox.firstPassLayout(containerBox);
				builder = new TwoPassBlockBuilder(this, blockBox);
			}
		}
			break;

		case ABSOLUTE: {
			final AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) blockBox;
			if (absoluteBox.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
				// 固定配置
				containerBox = this.getPageContext().getRootBox();
			} else {
				// 絶対配置
				containerBox = this.getContextBox();
			}
			if (!LayoutUtils.needsIntrinsicSizing(containerBox, blockBox)) {
				// 固定幅
				absoluteBox.shrinkToFit(containerBox, IntrinsicSizes.ZERO);

				// 高さは最後に確定するので、マルチカラムで高さが明示された場合でもリフローする
				builder = new BlockBuilder(this, blockBox);
			} else {
				// STF
				absoluteBox.firstPassLayout(containerBox);
				builder = new TwoPassBlockBuilder(this, blockBox);
			}
		}
			break;

		default:
			throw new IllegalStateException();
		}
		return builder;
	}

	public void finish() {
		assert this.flowStack == null || this.flowStack.isEmpty();
		assert this.textBuilder == null;

		final AbstractContainerBox flowBox = (AbstractContainerBox) this.contextFlow.box;
		final BlockParams params = flowBox.getBlockParams();
		if (flowBox.getColumnCount() > 1 && params.columns.fill == Columns.FILL_BALANCE) {
			// カラムのバランス
			this.pageAxis = this.contextFlow.pageAxis;
			flowBox.balance(this);
		}
	}

	public void close() {
		this.finish();
	}

	public void setBreakToken(BreakToken breakToken) {
		this.breakToken = this.breakToken.combine(breakToken);
	}

	protected void requireTextBlock() {
		// 新規テキストブロック
		// System.err.println("requireTextBlock");
		assert this.textBuilder == null;
		this.textBuilder = new TextBuilder(this, this.breakToken);
		this.breakToken = BreakToken.MID_FLOW;

		final Flow flow = this.getFlow();
		double localPageAxis = this.pageAxis - flow.pageAxis;
		flow.box.addFlow(this.textBuilder.textBlockBox, localPageAxis);
	}

	public void startTextRun(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
		if (this.textBuilder == null) {
			// System.err.println("begin1");
			this.requireTextBlock();
		}
		this.textBuilder.startTextRun(fontStyle, fontMetrics);
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		// System.err.println("glyph: "+new String(ch, coff, clen));
		this.textBuilder.glyph(charOffset, ch, coff, clen, gid);
	}

	public void endTextRun() {
		this.textBuilder.endTextRun();
	}

	public void control(final TextControl quad) {
		if (quad instanceof InlineQuad) {
			// インラインボックス
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				// インライン開始
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				inlineStartQuad.box.fixLineAxis(this.getFlowBox());
			}
				break;

			case InlineQuad.INLINE_END:
			case InlineQuad.INLINE_BLOCK:
				break;

			case InlineQuad.INLINE_ABSOLUTE:
				final InlineAbsoluteQuad inlineAbsoluteQuad = (InlineAbsoluteQuad) inlineQuad;
				if (inlineAbsoluteQuad.box.getType() == BoxType.REPLACED) {
					LayoutUtils.calculateReplacedSize(this, (AbstractReplacedBox) inlineAbsoluteQuad.box);
				}
				break;

			case InlineQuad.INLINE_REPLACED: {
				// 置換されたインライン
				final InlineReplacedQuad inlineReplacedQuad = (InlineReplacedQuad) inlineQuad;
				LayoutUtils.calculateReplacedSize(this, inlineReplacedQuad.box);
			}
				break;

			default:
				throw new IllegalStateException();
			}
		}
		if (this.textBuilder == null) {
			// System.err.println("begin2");
			this.requireTextBlock();
		}
		// System.err.println(this+"/"+quad);
		this.textBuilder.control(quad);
	}

	public void flush() {
		while (this.textBuilder.flush())
			;
	}

	public void endTextBlock() {
		// テキストブロックの終了。内容が空(control()が一度も呼ばれず
		// requireTextBlock()でtextBuilderが生成されない)場合はnullのまま
		// ここに達することがある(2026-07-18、空のテーブルセルで
		// NullPointerExceptionが実際に発生した)。このクラスの他の箇所
		// (809行目付近等)と同じくnullガードで対応する
		if (this.textBuilder != null) {
			this.textBuilder.finish();
			this.pageAxis += this.textBuilder.getPageAxis();
			this.textBuilder = null;
		}
		final Flow flow = this.getFlow();
		flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
		// System.err.println("endTextBlock");
	}

	/**
	 * 新規floatの配置確定の種別を、副作用なしで分類します(2026-07-23、
	 * 排除域P1増分2——従来の{@code transferFloatToNextPage}を純分類と
	 * {@link #recordBreakFloat}へ分解)。基底実装は常に
	 * {@link FloatCommitKind#PLACED}(改ページ文脈を持たないbuilderは
	 * floatを先送りしない)。
	 */
	FloatCommitKind classifyFloatPlacement(IFloatBox box, double pageStart) {
		return FloatCommitKind.PLACED;
	}

	/**
	 * フラグメント境界と交差したfloatの記録hookです(2026-07-23、
	 * 排除域P1増分2)。基底実装は何もしない。{@code BreakableBuilder}が
	 * {@code breakFloats}への追加として実装する。
	 */
	void recordBreakFloat(FloatSide side) {
	}
}
