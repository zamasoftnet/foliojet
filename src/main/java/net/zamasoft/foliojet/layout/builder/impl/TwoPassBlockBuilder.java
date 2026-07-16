package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import net.zamasoft.foliojet.layout.box.params.FloatSide;

import net.zamasoft.foliojet.layout.box.params.ClearMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.impl.css.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Pos;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineBlockQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.TableBuilder;
import net.zamasoft.foliojet.layout.builder.TwoPass;
import net.zamasoft.foliojet.layout.util.StyleUtils;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Element;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.LineBreak;

public class TwoPassBlockBuilder implements Builder, LayoutStack, TwoPass {
	private static final boolean DEBUG = false;

	/**
	 * 実測パスで記録し、bind() で再生するイベントです。
	 */
	private sealed interface Recorded {
		/** テキスト要素または制御(インライン境界等)。 */
		record ElementEvent(Element element) implements Recorded {
		}

		/** テキストブロックの終了。 */
		record EndTextBlock() implements Recorded {
			static final EndTextBlock INSTANCE = new EndTextBlock();
		}

		/** フローブロックの開始。 */
		record StartFlow(FlowBlockBox box) implements Recorded {
		}

		/** フローブロックの終了。 */
		record EndFlow(FlowBlockBox box) implements Recorded {
		}

		/** 置換要素。 */
		record ReplacedEvent(IBox box) implements Recorded {
		}

		/** ネストした shrink-to-fit ブロック。 */
		record StfBlock(TwoPassBlockBuilder builder) implements Recorded {
		}

		/** 絶対配置ブロック。 */
		record AbsoluteBlock(TwoPassBlockBuilder builder) implements Recorded {
		}

		/** テーブル。 */
		record TableEvent(TwoPassTableBuilder builder) implements Recorded {
		}
	}

	protected final LayoutStack layoutStack;

	/**
	 * 最小行幅、最大行幅、最小ページ高さ
	 */
	private double minLineSize = 0, maxLineSize = 0, minPageSize = 0;

	private double maxStartFloatAdvance = 0, maxEndFloatAdvance = 0;

	private int columnCount = 1;

	private TextImpl text;

	/**
	 * 現在の行幅。
	 */
	private double lineAxis = 0;

	private double atomicLineSize = 0;

	private double letterSpacing = 0;

	private double textIndent;

	private boolean blockHead;

	/**
	 * 通常のフローのブロックボックスの枠部分の行方向の幅、ページ方向の幅。
	 */
	private double lineFrame = 0, pageFrame = 0;

	private LineBreak toLineFeed = null;

	private final List<AbstractContainerBox> flowStack = new ArrayList<AbstractContainerBox>();

	private final List<IBox> inlineStack = new ArrayList<IBox>();

	private final List<Recorded> records = new ArrayList<Recorded>();

	private final List<TwoPass> recordInlineBlocks = new ArrayList<TwoPass>();

	public TwoPassBlockBuilder(LayoutStack layoutStack, AbstractContainerBox containerBox) {
		this.layoutStack = layoutStack;
		this.flowStack.add(containerBox);
		this.textIndent = containerBox.getTextIndent();
		this.blockHead = true;
		this.letterSpacing = StyleUtils.computeLength(containerBox.getBlockParams().letterSpacing,
				this.getFlowBox().getLineSize());
	}

	public AbstractContainerBox getFixedWidthContextBox() {
		AbstractContainerBox box = this.getContextBox();
		if (box.getBlockParams().size.getWidthType() != LengthType.AUTO) {
			return box;
		}
		switch (box.getPos().getType()) {
		case PAGE:
		case INLINE:
		case FLOW:
		case FLOAT:
		case TABLE_CELL:
		case TABLE_CAPTION:
			return this.layoutStack.getFixedWidthFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedWidthContextBox();
		default:
			throw new IllegalStateException();
		}
	}

	public AbstractContainerBox getFixedHeightContextBox() {
		AbstractContainerBox box = this.getContextBox();
		if (box.getBlockParams().size.getHeightType() != LengthType.AUTO) {
			return box;
		}
		switch (box.getPos().getType()) {
		case PAGE:
		case INLINE:
		case FLOW:
		case FLOAT:
		case TABLE_CELL:
		case TABLE_CAPTION:
			return this.layoutStack.getFixedHeightFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedHeightContextBox();
		default:
			throw new IllegalStateException(String.valueOf(box.getPos().getType()));
		}
	}

	public double getFixedWidth() {
		double frameWidth = 0;
		for (int i = this.flowStack.size() - 1; i >= 1; --i) {
			AbstractContainerBox flowBox = (AbstractContainerBox) this.flowStack.get(i);
			frameWidth += flowBox.getFrame().getFrameWidth();
			if (flowBox.getBlockParams().size.getWidthType() != LengthType.AUTO) {
				return flowBox.getWidth() - frameWidth;
			}
		}
		AbstractContainerBox box = this.getFixedWidthContextBox();
		if (box == null) {
			return 0;
		}
		return box.getInnerWidth() - frameWidth;
	}

	public AbstractContainerBox getFixedWidthFlowBox() {
		for (int i = this.flowStack.size() - 1; i >= 1; --i) {
			AbstractContainerBox flowBox = (AbstractContainerBox) this.flowStack.get(i);
			if (flowBox.getBlockParams().size.getWidthType() != LengthType.AUTO) {
				return flowBox;
			}
		}
		return this.getFixedWidthContextBox();
	}

	public double getFixedHeight() {
		double flowHeight = 0;
		for (int i = this.flowStack.size() - 1; i >= 1; --i) {
			AbstractContainerBox flowBox = (AbstractContainerBox) this.flowStack.get(i);
			flowHeight += flowBox.getFrame().getFrameHeight();
			if (flowBox.getBlockParams().size.getHeightType() != LengthType.AUTO) {
				return flowBox.getHeight() - flowHeight;
			}
		}
		AbstractContainerBox box = this.getFixedHeightContextBox();
		if (box == null) {
			return 0;
		}
		return box.getInnerHeight() - flowHeight;
	}

	public AbstractContainerBox getFixedHeightFlowBox() {
		for (int i = this.flowStack.size() - 1; i >= 1; --i) {
			AbstractContainerBox flowBox = (AbstractContainerBox) this.flowStack.get(i);
			if (flowBox.getBlockParams().size.getHeightType() != LengthType.AUTO) {
				return flowBox;
			}
		}
		return this.getFixedHeightContextBox();
	}

	public RootBuilder getPageContext() {
		return this.layoutStack.getPageContext();
	}

	public Builder getParentBuilder() {
		return (Builder) this.layoutStack;
	}

	public IntrinsicSizes getIntrinsicSizes() {
		return new IntrinsicSizes(this.minLineSize, this.maxLineSize, this.minPageSize);
	}

	public boolean isMain() {
		return false;
	}

	public boolean isTwoPass() {
		return true;
	}

	public AbstractContainerBox getContextBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 1; --i) {
				AbstractContainerBox box = (AbstractContainerBox) this.flowStack.get(i);
				if (box.isContextBox()) {
					return box;
				}
			}
		}
		AbstractContainerBox box = (AbstractContainerBox) this.flowStack.get(0);
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
				final AbstractContainerBox box = (AbstractContainerBox) this.flowStack.get(i);
				if (box.getColumnCount() > 1) {
					return box;
				}
			}
		}
		return null;
	}

	public AbstractContainerBox getRootBox() {
		return (AbstractContainerBox) this.flowStack.get(0);
	}

	public AbstractContainerBox getFlowBox() {
		return (AbstractContainerBox) this.flowStack.get(this.flowStack.size() - 1);
	}

	public void startFlowBlock(final FlowBlockBox flowBox) {
		// 通常のフローのブロックボックス
		assert this.inlineStack.isEmpty();
		AbstractContainerBox containerBox = this.getFlowBox();
		BlockParams params = containerBox.getBlockParams();
		FlowPos pos = (FlowPos) flowBox.getPos();
		this.clearFloatAdvance(pos.clear);

		flowBox.firstPassLayout(containerBox);
		double lineSize = this.lineFrame + flowBox.getLineExtent(params.flow);
		this.lineFrame += flowBox.getFrame().getFrameLineExtent(params.flow);
		this.pageFrame += flowBox.getFrame().getFramePageExtent(params.flow);
		assert !StyleUtils.isNone(this.lineFrame);
		if (flowBox.getColumnCount() > 0) {
			this.lineFrame += flowBox.getBlockParams().columns.gap * (flowBox.getColumnCount() - 1);
		}
		this.lineFrame *= this.columnCount;
		lineSize *= this.columnCount;
		if (this.lineFrame > this.minLineSize) {
			this.minLineSize = this.lineFrame;
		}
		if (this.pageFrame > this.minPageSize) {
			this.minPageSize = this.pageFrame;
		}
		if (lineSize > this.maxLineSize) {
			this.maxLineSize = lineSize;
		}
		this.textIndent = flowBox.getTextIndent();
		this.blockHead = true;

		this.flowStack.add(flowBox);
		this.records.add(new Recorded.StartFlow(flowBox));
		this.columnCount *= flowBox.getColumnCount();
		this.letterSpacing = StyleUtils.computeLength(flowBox.getBlockParams().letterSpacing,
				this.getFlowBox().getLineSize());
	}

	public void endFlowBlock() {
		// 通常のフローのブロックボックス
		assert this.inlineStack.isEmpty();
		AbstractBlockBox flowBox = (AbstractBlockBox) this.flowStack.remove(this.flowStack.size() - 1);
		AbstractContainerBox containerBox = this.getFlowBox();
		BlockParams params = containerBox.getBlockParams();
		BlockParams flowParams = flowBox.getBlockParams();
		this.columnCount /= flowBox.getColumnCount();
		this.lineFrame /= this.columnCount;
		if (flowBox.getColumnCount() > 0) {
			this.lineFrame -= flowBox.getBlockParams().columns.gap * (flowBox.getColumnCount() - 1);
		}

		switch (params.flow) {
		case WritingMode.TB:
			// 横書き
			this.lineFrame -= flowBox.getFrame().getFrameWidth();
			this.pageFrame -= flowBox.getFrame().getFrameHeight();
			if (flowParams.size.getWidthType() == LengthType.ABSOLUTE) {
				// 固定幅フロー
				this.maxLineSize = this.minLineSize = flowBox.getWidth();
			}
			break;
		case WritingMode.LR:
		case WritingMode.RL:
			// 縦書き
			this.lineFrame -= flowBox.getFrame().getFrameHeight();
			this.pageFrame -= flowBox.getFrame().getFrameWidth();
			if (flowParams.size.getHeightType() == LengthType.ABSOLUTE) {
				// 固定幅フロー
				this.maxLineSize = this.minLineSize = flowBox.getHeight();
			}
			break;
		default:
			throw new IllegalStateException();
		}

		assert !StyleUtils.isNone(this.lineFrame);
		this.records.add(new Recorded.EndFlow((FlowBlockBox) flowBox));

		this.textIndent = 0;
		this.blockHead = false;
		this.letterSpacing = StyleUtils.computeLength(flowBox.getBlockParams().letterSpacing,
				this.getFlowBox().getLineSize());
	}

	public void addBound(IBox box) {
		AbstractReplacedBox replacedBox = (AbstractReplacedBox) box;
		switch (replacedBox.getPos().getType()) {
		case FLOW: {
			// 静的・相対配置
			AbstractContainerBox containerBox = this.getFlowBox();
			IFlowBox flowBox = (IFlowBox) replacedBox;
			FlowPos pos = (FlowPos) flowBox.getPos();
			this.clearFloatAdvance(pos.clear);
			StyleUtils.calclateReplacedSize(this, replacedBox);

			double minLineAxis, maxLineAxis = 0, minPageAxis;
			BlockParams params = containerBox.getBlockParams();
			if (params.flow.isVertical()) {
				// 縦書き
				minLineAxis = replacedBox.getHeight();
				minPageAxis = replacedBox.getWidth();
				if (replacedBox.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getHeight();
				}
			} else {
				// 横書き
				minLineAxis = replacedBox.getWidth();
				minPageAxis = replacedBox.getHeight();
				if (replacedBox.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getWidth();
				}
			}
			minPageAxis += this.pageFrame;
			minLineAxis *= this.columnCount;
			minLineAxis += this.lineFrame;
			
			maxLineAxis *= this.columnCount;
			maxLineAxis += this.lineFrame;

			assert !StyleUtils.isNone(minLineAxis);
			if (minLineAxis > this.minLineSize) {
				this.minLineSize = minLineAxis;
			}
			if (minPageAxis > this.minPageSize) {
				this.minPageSize = minPageAxis;
			}
			if (maxLineAxis > this.maxLineSize) {
				this.maxLineSize = maxLineAxis;
			}
		}
			break;
		case FLOAT: {
			// 浮動体
			AbstractContainerBox containerBox = this.getFlowBox();
			IFloatBox floatingBox = (IFloatBox) replacedBox;
			this.clearFloatAdvance(floatingBox.getFloatPos().clear);
			StyleUtils.calclateReplacedSize(this, replacedBox);

			double minLineAxis, minPageAxis, maxLineAxis = 0;
			BlockParams params = containerBox.getBlockParams();
			if (params.flow.isVertical()) {
				// 縦書き
				minLineAxis = replacedBox.getHeight();
				minPageAxis = replacedBox.getWidth();
				if (replacedBox.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getHeight();
				}
			} else {
				// 横書き
				minLineAxis = replacedBox.getWidth();
				minPageAxis = replacedBox.getHeight();
				if (replacedBox.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getWidth();
				}
			}
			assert !StyleUtils.isNone(minLineAxis);
			if (minLineAxis > this.minLineSize) {
				this.minLineSize = minLineAxis;
			}
			if (minPageAxis > this.minPageSize) {
				this.minPageSize = minPageAxis;
			}

			switch (floatingBox.getFloatPos().floating) {
			case FloatSide.START: {
				this.maxStartFloatAdvance += minLineAxis;
			}
				break;
			case FloatSide.END: {
				this.maxEndFloatAdvance += minLineAxis;
			}
				break;
			default:
				throw new IllegalStateException();
			}
			double xmaxLineAxis = this.maxStartFloatAdvance + this.maxEndFloatAdvance;
			if (xmaxLineAxis > maxLineAxis) {
				maxLineAxis = xmaxLineAxis;
			}
			maxLineAxis *= this.columnCount;
			maxLineAxis += this.lineFrame;
			if (maxLineAxis > this.maxLineSize) {
				this.maxLineSize = maxLineAxis;
			}
		}
			break;

		case ABSOLUTE:
			// 絶対配置
			AbstractContainerBox contextBox;
			switch (((AbsoluteReplacedBox) replacedBox).getAbsolutePos().fiducial) {
			case Fiducial.CONTEXT:
			case Fiducial.ALL_PAGE: {
				// 通常の絶対配置
				// 固定配置
				contextBox = this.getFlowBox();
			}
				break;
			case Fiducial.CURRENT_PAGE: {
				// ページコンテンツ
				contextBox = this.getPageContext().getRootBox();
			}
				break;
			default:
				throw new IllegalStateException();
			}
			replacedBox.calculateFrame(contextBox.getLineSize());
			break;

		default:
			throw new IllegalStateException();
		}
		this.records.add(new Recorded.ReplacedEvent(replacedBox));
	}

	public void addTable(TableBuilder tableBuilder) {
		TwoPassTableBuilder autoTableBuilder = (TwoPassTableBuilder) tableBuilder;
		autoTableBuilder.prepareLayout();
		final IntrinsicSizes tableSizes = autoTableBuilder.getIntrinsicSizes();
		this.minLineSize = Math.max(this.minLineSize, tableSizes.minContent() * this.columnCount);
		this.maxLineSize = Math.max(this.maxLineSize, tableSizes.maxContent() * this.columnCount);
		this.records.add(new Recorded.TableEvent(autoTableBuilder));
		switch (autoTableBuilder.getTableBox().getBlockBox().getPos().getType()) {
		case INLINE:
			this.recordInlineBlocks.add(autoTableBuilder);
			break;
		}
	}

	public Builder newBuilder(final AbstractBlockBox stfBox) {
		// * TODO 絶対幅の場合はBoundContainerContextが使えますが、
		// * 絶対配置の位置調整を構築後に行わないといけないため
		// * そのままにしています。
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this, stfBox);
		final AbstractContainerBox box = this.getFlowBox();
		stfBox.firstPassLayout(box);
		switch (stfBox.getPos().getType()) {
		case FLOW:
			// 書字方向が違う
		case FLOAT:
			// 浮動体
			this.records.add(new Recorded.StfBlock(builder));
			break;

		case ABSOLUTE:
			// 絶対配置
			this.records.add(new Recorded.AbsoluteBlock(builder));
			break;

		case INLINE:
			// インラインブロック
			this.recordInlineBlocks.add(builder);
			break;

		default:
			throw new IllegalStateException();
		}
		return builder;
	}

	public void fitFloating(TwoPassBlockBuilder childBuilder) {
		FloatBlockBox floatingBox = (FloatBlockBox) childBuilder.getRootBox();
		this.clearFloatAdvance(floatingBox.getFloatPos().clear);

		BlockParams params = floatingBox.getBlockParams();
		BlockParams flowParams = this.getFlowBox().getBlockParams();
		double minLineAxis, maxLineAxis;
		if (flowParams.flow.isVertical()) {
			// 縦書き
			if (params.size.getHeightType() != LengthType.AUTO) {
				minLineAxis = maxLineAxis = floatingBox.getHeight();
			} else {
				final IntrinsicSizes childSizes = childBuilder.getIntrinsicSizes();
				minLineAxis = childSizes.minContent() + floatingBox.getFrame().getFrameWidth();
				maxLineAxis = childSizes.maxContent() + floatingBox.getFrame().getFrameHeight();
			}
		} else {
			// 横書き
			if (params.size.getWidthType() != LengthType.AUTO) {
				minLineAxis = maxLineAxis = floatingBox.getWidth();
			} else {
				final IntrinsicSizes childSizes = childBuilder.getIntrinsicSizes();
				minLineAxis = childSizes.minContent() + floatingBox.getFrame().getFrameWidth();
				maxLineAxis = childSizes.maxContent() + floatingBox.getFrame().getFrameWidth();
			}
		}
		assert !StyleUtils.isNone(maxLineAxis);
		// System.err.println(this.minLineAxis + "/" + this.maxLineAxis);
		if (minLineAxis > this.minLineSize) {
			this.minLineSize = minLineAxis;
		}

		switch (floatingBox.getFloatPos().floating) {
		case FloatSide.START:
			this.maxStartFloatAdvance += maxLineAxis;
			break;
		case FloatSide.END:
			this.maxEndFloatAdvance += maxLineAxis;
			break;
		default:
			throw new IllegalStateException();
		}
		maxLineAxis = this.maxStartFloatAdvance + this.maxEndFloatAdvance;
		maxLineAxis *= this.columnCount;
		maxLineAxis += this.lineFrame;
		if (maxLineAxis > this.maxLineSize) {
			this.maxLineSize = maxLineAxis;
		}
	}

	public void bind(BlockBuilder builder) {
		// 再レイアウト
		if (DEBUG) {
			System.err.println("BIND");
		}
		FilterGlyphHandler textUnitizer = null;

		for (final Recorded recorded : this.records) {
			switch (recorded) {
			case Recorded.ElementEvent elementEvent: {
				if (textUnitizer == null) {
					textUnitizer = new CSSJTextUnitizer(builder.getFlowBox().getBlockParams().hyphenation);
					textUnitizer.setGlyphHandler(new BuilderGlyphHandler(builder));
				}
				final Element e = elementEvent.element();
				if (DEBUG) {
					System.err.println("ELEMENT " + e);
				}
				if (e instanceof Text) {
					final Text text = ((Text) e);
					assert text.getGlyphCount() > 0;
					text.toGlyphs(textUnitizer);
				} else if (e instanceof TextControl) {
					final TextControl quad = (TextControl) e;
					if (quad instanceof InlineBlockQuad) {
						final TwoPass twoPass = (TwoPass) this.recordInlineBlocks.remove(0);
						if (twoPass instanceof TwoPassBlockBuilder) {
							final InlineBlockQuad inlineBlockQuad = (InlineBlockQuad) quad;
							final InlineBlockBox inlineBlockBox = inlineBlockQuad.box;
							final TwoPassBlockBuilder stfBuilder = (TwoPassBlockBuilder) twoPass;
							inlineBlockBox.shrinkToFit(builder, stfBuilder.getIntrinsicSizes(), false);
							final BlockBuilder lnlineBlockBuilder = new BlockBuilder(this, inlineBlockBox);
							stfBuilder.bind(lnlineBlockBuilder);
							lnlineBlockBuilder.close();
						}
					}
					textUnitizer.control(quad);
				} else {
					throw new IllegalStateException();
				}
			}
				break;

			case Recorded.EndTextBlock endTextBlock:
				if (DEBUG) {
					System.err.println("END_TEXT_BLOCK");
				}
				textUnitizer.close();
				textUnitizer = null;
				builder.endTextBlock();
				break;

			case Recorded.StartFlow startFlow: {
				if (DEBUG) {
					System.err.println("START_FLOW");
				}
				final FlowBlockBox flow = startFlow.box();
				if (flow.getBlockParams().flow.isVertical() == builder.getRootBox().getBlockParams().flow.isVertical()) {
					builder.startFlowBlock(flow);
				} else {
					// 書字方向が違う場合
					builder = (BlockBuilder) builder.newBuilder(flow);
				}
			}
				break;

			case Recorded.EndFlow endFlow:
				if (DEBUG) {
					System.err.println("END_FLOW");
				}
				final FlowBlockBox flow = endFlow.box();
				if (flow == builder.getRootBox()) {
					builder = (BlockBuilder) builder.getParentBuilder();
					builder.addBound(flow);
				} else {
					builder.endFlowBlock();
				}
				break;

			case Recorded.ReplacedEvent replacedEvent: {
				if (DEBUG) {
					System.err.println("REPLACED");
				}
				final IBox replacedBox = replacedEvent.box();
				switch (replacedBox.getPos().getType()) {
				case FLOAT:
				case FLOW:
					if (textUnitizer != null) {
						textUnitizer.flush();
					}
				}
				builder.addBound(replacedBox);
			}
				break;

			case Recorded.StfBlock stfBlock: {
				if (DEBUG) {
					System.err.println("STF");
				}
				if (textUnitizer != null) {
					textUnitizer.flush();
				}
				final TwoPassBlockBuilder stfBuilder = stfBlock.builder();
				final AbstractStaticBlockBox blockBox = (AbstractStaticBlockBox) stfBuilder.getRootBox();
				blockBox.shrinkToFit(builder, stfBuilder.getIntrinsicSizes(), false);
				final BlockBuilder boundBuilder = new BlockBuilder(this, blockBox);
				stfBuilder.bind(boundBuilder);
				boundBuilder.close();
				builder.addBound(blockBox);
			}
				break;

			case Recorded.AbsoluteBlock absoluteBlock: {
				if (DEBUG) {
					System.err.println("ABSOLUTE");
				}

				final TwoPassBlockBuilder stfBuilder = absoluteBlock.builder();
				final AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) stfBuilder.getRootBox();
				final AbstractContainerBox containerBox;
				if (absoluteBox.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
					containerBox = builder.getPageContext().getRootBox();
				} else {
					containerBox = builder.getContextBox();
				}
				absoluteBox.shrinkToFit(containerBox, stfBuilder.getIntrinsicSizes());
				final BlockBuilder boundBuilder = new BlockBuilder(this, absoluteBox);
				stfBuilder.bind(boundBuilder);
				boundBuilder.close();
				final AbsolutePos pos = absoluteBox.getAbsolutePos();
				switch (pos.autoPosition) {
				case AutoPosition.BLOCK:
					builder.addBound(absoluteBox);
					break;
				case AutoPosition.INLINE:
					final TextControl quad = InlineQuad.createInlineAbsoluteBoxQuad(absoluteBox);
					if (textUnitizer == null) {
						textUnitizer = new CSSJTextUnitizer(builder.getFlowBox().getBlockParams().hyphenation);
						textUnitizer.setGlyphHandler(new BuilderGlyphHandler(builder));
					}
					textUnitizer.control(quad);
					break;
				default:
					throw new IllegalStateException();
				}
			}
				break;

			case Recorded.TableEvent tableEvent: {
				if (DEBUG) {
					System.err.println("TABLE");
				}
				final TwoPassTableBuilder tableBuilder = tableEvent.builder();
				switch (tableBuilder.getTableBox().getBlockBox().getPos().getType()) {
				case FLOAT:
				case FLOW:
					if (textUnitizer != null) {
						textUnitizer.flush();
					}
				}
				tableBuilder.bind(builder);
			}
				break;

			default:
				throw new IllegalStateException();
			}
		}
		if (DEBUG) {
			System.err.println("/FINISH");
		}
	}

	private double getCurrentLineHeight() {
		if (this.inlineStack.isEmpty()) {
			return this.getFlowBox().getBlockParams().lineHeight;
		}
		InlineBox box = (InlineBox) this.inlineStack.get(this.inlineStack.size() - 1);
		return box.getInlinePos().lineHeight;
	}

	public void startTextRun(int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.text = new TextImpl(charOffset, fontStyle, fontMetrics);
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		double advance = this.text.appendGlyph(ch, coff, clen, gid);
		advance += this.letterSpacing;
		this.atomicLineSize += advance;
		this.lineAxis += advance;
		double minPageAxis = this.getCurrentLineHeight() + this.pageFrame;
		if (minPageAxis > this.minPageSize) {
			this.minPageSize = minPageAxis;
		}
	}

	public void endTextRun() {
		this.text.pack();
		this.records.add(new Recorded.ElementEvent(this.text));
		this.text = null;
	}

	public void control(final TextControl quad) {
		if (quad instanceof LineBreak) {
			this.toLineFeed = (LineBreak) quad;
		}
		this.records.add(new Recorded.ElementEvent(quad));

		double minAdvance, maxAdvance, pageSize;
		if (quad instanceof InlineQuad) {
			final InlineQuad inlineQuad = (InlineQuad) quad;
			final BlockParams cParams = this.getFlowBox().getBlockParams();
			if (quad instanceof InlineReplacedQuad) {
				// 画像
				final AbstractReplacedBox box = (AbstractReplacedBox) inlineQuad.getBox();
				maxAdvance = quad.getAdvance();
				minAdvance = 0;
				if (cParams.flow.isVertical()) {
					// 縦書き
					if (box.getReplacedParams().size.getHeightType() != LengthType.RELATIVE
							&& box.getReplacedParams().maxSize.getHeightType() != LengthType.RELATIVE) {
						minAdvance = maxAdvance;
					}
					if (box.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE) {
						if(box.getReplacedParams().size.getHeight() > maxAdvance) {
							maxAdvance = box.getReplacedParams().size.getHeight();
						}
					}	
					pageSize = box.getWidth();
				} else {
					// 横書き
					if (box.getReplacedParams().size.getWidthType() != LengthType.RELATIVE
							&& box.getReplacedParams().maxSize.getWidthType() != LengthType.RELATIVE) {
						minAdvance = maxAdvance;
					}
					if (box.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE) {
						if(box.getReplacedParams().size.getWidth() > maxAdvance) {
							maxAdvance = box.getReplacedParams().size.getWidth();
						}
					}	
					pageSize = box.getHeight();
				}
			} else if (quad instanceof InlineBlockQuad) {
				// インラインブロック
				final AbstractContainerBox box = (AbstractContainerBox) inlineQuad.getBox();
				final double lineFrame = box.getFrame().getFrameLineExtent(cParams.flow);
				final double pageFrame = box.getFrame().getFramePageExtent(cParams.flow);
				// インラインブロック
				final BlockParams params = (BlockParams) box.getParams();
				final TwoPass stfBuilder = (TwoPass) this.recordInlineBlocks.get(this.recordInlineBlocks.size() - 1);
				final IntrinsicSizes stfSizes = stfBuilder.getIntrinsicSizes();
				if (cParams.flow.isVertical() == params.flow.isVertical()) {
					minAdvance = stfSizes.minContent() + lineFrame;
					maxAdvance = stfSizes.maxContent() + lineFrame;
					pageSize = stfSizes.minPage() + pageFrame;
				} else {
					// 縦中横/横中縦
					minAdvance = maxAdvance = stfSizes.minPage() + pageFrame;
					pageSize = stfSizes.minContent() + lineFrame;
				}
				minAdvance = Math.max(minAdvance, box.getLineExtent(params.flow));
				maxAdvance = Math.max(maxAdvance, box.getLineExtent(params.flow));
				pageSize = Math.max(pageSize, box.getPageExtent(params.flow));
			} else {
				if (inlineQuad instanceof InlineStartQuad) {
					this.inlineStack.add(inlineQuad.getBox());
					final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
					this.letterSpacing = StyleUtils.computeLength(inlineStartQuad.box.getTextParams().letterSpacing,
							this.getFlowBox().getLineSize());
				} else if (inlineQuad instanceof InlineEndQuad) {
					this.inlineStack.remove(this.inlineStack.size() - 1);
					AbstractTextParams params;
					if (this.inlineStack.isEmpty()) {
						params = this.getFlowBox().getBlockParams();
					} else {
						final InlineBox box = (InlineBox) this.inlineStack.get(this.inlineStack.size() - 1);
						params = box.getTextParams();
					}
					this.letterSpacing = StyleUtils.computeLength(params.letterSpacing,
							this.getFlowBox().getLineSize());
				}
				minAdvance = maxAdvance = quad.getAdvance();
				pageSize = inlineQuad.getBox().getPageExtent(cParams.flow);
			}
		} else {
			minAdvance = maxAdvance = quad.getAdvance();
			pageSize = 0;
		}
		pageSize = Math.max(pageSize, this.getCurrentLineHeight());
		pageSize += this.pageFrame;
		if (pageSize > this.minPageSize) {
			this.minPageSize = pageSize;
		}
		this.atomicLineSize += minAdvance;
		this.lineAxis += maxAdvance;
	}

	public void flush() {
		double minLineSize = this.atomicLineSize;
		if (this.blockHead) {
			minLineSize += this.textIndent;
			this.blockHead = false;
		}
		minLineSize *= this.columnCount;
		minLineSize += this.lineFrame;
		if (minLineSize > this.minLineSize) {
			this.minLineSize = minLineSize;
			if (minLineSize > this.maxLineSize) {
				this.maxLineSize = minLineSize;
			}
		}
		this.atomicLineSize = 0;
		if (this.toLineFeed != null) {
			assert !StyleUtils.isNone(this.lineAxis);
			assert !StyleUtils.isNone(this.lineFrame);
			double maxLineSize = this.textIndent + this.maxStartFloatAdvance + this.maxEndFloatAdvance + this.lineAxis;
			maxLineSize *= this.columnCount;
			maxLineSize += this.lineFrame;
			if (maxLineSize > this.maxLineSize) {
				this.maxLineSize = maxLineSize;
			}
			this.lineAxis = 0;
			this.toLineFeed = null;
			this.textIndent = 0;
			this.clearFloatAdvance(ClearMode.BOTH);
		}
	}
	
	public void finish() {
		this.flush();
	}

	public void close() {
		this.finish();
	}

	public void endTextBlock() {
		this.records.add(Recorded.EndTextBlock.INSTANCE);
		assert !StyleUtils.isNone(this.lineAxis);
		assert !StyleUtils.isNone(this.lineFrame);
		double minLineSize = this.atomicLineSize;
		if (this.blockHead) {
			minLineSize += this.textIndent;
			this.blockHead = false;
		}
		minLineSize *= this.columnCount;
		minLineSize += this.lineFrame;
		if (minLineSize > this.minLineSize) {
			this.minLineSize = minLineSize;
		}
		double maxLineSize = this.textIndent + this.maxStartFloatAdvance + this.maxEndFloatAdvance + this.lineAxis;
		maxLineSize *= this.columnCount;
		maxLineSize += this.lineFrame;
		if (maxLineSize > this.maxLineSize) {
			this.maxLineSize = maxLineSize;
		}
		this.atomicLineSize = 0;
		this.lineAxis = 0;
	}

	private void clearFloatAdvance(ClearMode clear) {
		switch (clear) {
		case ClearMode.BOTH:
			this.maxStartFloatAdvance = 0;
			this.maxEndFloatAdvance = 0;
			break;
		case ClearMode.START:
			this.maxStartFloatAdvance = 0;
			break;
		case ClearMode.END:
			this.maxEndFloatAdvance = 0;
			break;
		case ClearMode.NONE:
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public boolean isEmpty() {
		return this.records.isEmpty();
	}
}
