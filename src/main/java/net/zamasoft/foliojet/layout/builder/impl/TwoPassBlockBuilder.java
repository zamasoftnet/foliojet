package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.impl.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Pos;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineBlockQuad;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.TableBuilder;
import net.zamasoft.foliojet.layout.builder.TwoPass;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Element;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

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

		/** インラインブロック(ネストした実測ビルダーを内包)。 */
		record InlineBlockEvent(InlineBlockQuad quad, TwoPass measure) implements Recorded {
		}

		/** テーブル。 */
		record TableEvent(RetainedTableBuilder builder) implements Recorded {
		}
	}

	protected final LayoutStack layoutStack;

	/**
	 * 固有寸法の計測器。イベントを記録(records)と同時にこちらへ流し込みます。
	 */
	private final IntrinsicMeasurer measurer = new IntrinsicMeasurer(this);

	private TextImpl text;

	private final List<AbstractContainerBox> flowStack = new ArrayList<AbstractContainerBox>();

	private final List<Recorded> records = new ArrayList<Recorded>();

	/**
	 * glyph()イベントの累計(recordsへ保持されるTextImplのglyph総量の概算)。
	 * E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤。挙動には影響しない。
	 */
	private long glyphCount = 0;

	/**
	 * 直近に作られたネスト実測ビルダー。対応する InlineBlockQuad の到着時に
	 * InlineBlockEvent へ内包されます。
	 */
	private TwoPass pendingInlineBlock;

	public TwoPassBlockBuilder(LayoutStack layoutStack, AbstractContainerBox containerBox) {
		this.layoutStack = layoutStack;
		this.flowStack.add(containerBox);
		this.measurer.start(containerBox);
		// E-6増分1(2026-07-24): ネスト深さのhigh-water観測(読み取りのみ、
		// 挙動には影響しない)。layoutStack鎖上の連続するTwoPassBlockBuilder
		// 数を数える(表セル経由のネストはRetainedTableBuilderが親のlayoutStack
		// を引き継ぐため、この鎖に自然に現れる)
		int depth = 1;
		for (LayoutStack stack = layoutStack; stack instanceof TwoPassBlockBuilder parent; stack = parent.layoutStack) {
			++depth;
		}
		TableBuildStats.reportTwoPassNestDepth(depth);
	}

	/**
	 * recordsへ追記し、保持量high-waterを報告します(E-6増分1(2026-07-24)、
	 * spill閾値・対象選定の実測基盤。追記+max更新のみで挙動には影響しない)。
	 */
	private void addRecord(final Recorded recorded) {
		this.records.add(recorded);
		TableBuildStats.reportTwoPassRecordRetention(this.records.size());
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

	/**
	 * 固有寸法を実レイアウト計測(M2c)で求め、範囲を特定できない場合は
	 * 旧2パスの模倣計測へフォールバックします。shrinkToFit の全消費者は
	 * getIntrinsicSizes()(模倣のみ)ではなくこちらを使うこと。
	 */
	public IntrinsicSizes intrinsicSizesMeasured() {
		final net.zamasoft.foliojet.layout.builder.impl.RootBuilder root = this.layoutStack == null ? null
				: this.getPageContext();
		if (root != null && root.isSegmentRestyle()) {
			final AbstractContainerBox rootBox = (AbstractContainerBox) this.getRootBox();
			final IntrinsicSizes measured = net.zamasoft.foliojet.layout.sizing.MeasuredIntrinsics.of(
					root.getPageGenerator().getLayoutSource(), rootBox, rootBox.getBlockParams(),
					root.getPageGenerator().getUserAgent());
			if (measured != null) {
				return measured;
			}
		}
		return this.measurer.sizes();
	}

	public IntrinsicSizes getIntrinsicSizes() {
		return this.measurer.sizes();
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
		AbstractContainerBox containerBox = this.getFlowBox();
		// firstPassLayout は計測状態(浮動体アドバンス)を読まないため、
		// clearFloatAdvance(計測器側)との順序入れ替えは等価。
		flowBox.firstPassLayout(containerBox);
		this.measurer.startFlow(flowBox, containerBox);

		this.flowStack.add(flowBox);
		this.addRecord(new Recorded.StartFlow(flowBox));
	}

	public void endFlowBlock() {
		// 通常のフローのブロックボックス
		AbstractBlockBox flowBox = (AbstractBlockBox) this.flowStack.remove(this.flowStack.size() - 1);
		// 元コードでは記録(records.add)は枠反転処理と textIndent リセットの間にあったが、
		// 計測状態と records は独立のため 計測(末尾リセット含む)→記録 の順でも等価。
		this.measurer.endFlow(flowBox);
		this.addRecord(new Recorded.EndFlow((FlowBlockBox) flowBox));
	}

	public void addBound(IBox box) {
		AbstractReplacedBox replacedBox = (AbstractReplacedBox) box;
		this.measurer.bound(replacedBox);
		this.addRecord(new Recorded.ReplacedEvent(replacedBox));
	}

	public void addTable(TableBuilder tableBuilder) {
		RetainedTableBuilder autoTableBuilder = (RetainedTableBuilder) tableBuilder;
		autoTableBuilder.prepareLayout();
		final IntrinsicSizes tableSizes = autoTableBuilder.getIntrinsicSizes();
		this.measurer.table(tableSizes);
		this.addRecord(new Recorded.TableEvent(autoTableBuilder));
		switch (autoTableBuilder.getTableBox().getBlockBox().getPos().getType()) {
		case INLINE:
			this.pendingInlineBlock = autoTableBuilder;
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
			this.addRecord(new Recorded.StfBlock(builder));
			break;

		case ABSOLUTE:
			// 絶対配置
			this.addRecord(new Recorded.AbsoluteBlock(builder));
			break;

		case INLINE:
			// インラインブロック
			this.pendingInlineBlock = builder;
			break;

		default:
			throw new IllegalStateException();
		}
		return builder;
	}

	public void fitFloating(TwoPassBlockBuilder childBuilder) {
		this.measurer.fitFloating(childBuilder);
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
					textUnitizer = new CSSJTextUnitizer(builder.getFlowBox().getBlockParams());
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
					textUnitizer.control(quad);
				} else {
					throw new IllegalStateException();
				}
			}
				break;

			case Recorded.InlineBlockEvent inlineBlockEvent: {
				if (textUnitizer == null) {
					textUnitizer = new CSSJTextUnitizer(builder.getFlowBox().getBlockParams());
					textUnitizer.setGlyphHandler(new BuilderGlyphHandler(builder));
				}
				// インラインテーブルの実測(RetainedTableBuilder)は TableEvent 側で bind される
				if (inlineBlockEvent.measure() instanceof TwoPassBlockBuilder stfBuilder) {
					final InlineBlockBox inlineBlockBox = inlineBlockEvent.quad().box;
					inlineBlockBox.shrinkToFit(builder, stfBuilder.intrinsicSizesMeasured(), false);
					final BlockBuilder inlineBlockBuilder = new BlockBuilder(this, inlineBlockBox);
					stfBuilder.bind(inlineBlockBuilder);
					inlineBlockBuilder.close();
				}
				textUnitizer.control(inlineBlockEvent.quad());
			}
				break;

			case Recorded.EndTextBlock endTextBlock:
				if (DEBUG) {
					System.err.println("END_TEXT_BLOCK");
				}
				// 内容が空のテキストブロック(例: 空のテーブルセル)では
				// ElementEvent/InlineBlockEventが一度も来ずtextUnitizerが
				// 遅延初期化されないままここに達することがある(2026-07-18、
				// <td></td>のような空セルでNullPointerExceptionが実際に
				// 発生した)。他のcase節と同じくnullガードで対応する。
				if (textUnitizer != null) {
					textUnitizer.close();
					textUnitizer = null;
				}
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
				blockBox.shrinkToFit(builder, stfBuilder.intrinsicSizesMeasured(), false);
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
				absoluteBox.shrinkToFit(containerBox, stfBuilder.intrinsicSizesMeasured());
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
						textUnitizer = new CSSJTextUnitizer(builder.getFlowBox().getBlockParams());
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
				final RetainedTableBuilder tableBuilder = tableEvent.builder();
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

	public void startTextRun(int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.text = new TextImpl(charOffset, fontStyle, fontMetrics);
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		// appendGlyph は記録用 TextImpl を構築しつつアドバンスを返すため、
		// 呼び出しは一度だけ行い、結果を計測器へ渡す。
		double advance = this.text.appendGlyph(ch, coff, clen, gid);
		this.measurer.glyph(advance);
		// E-6増分1(2026-07-24): glyph保持量の概算観測(加算のみ、挙動不変)
		++this.glyphCount;
	}

	public void endTextRun() {
		this.text.pack();
		this.addRecord(new Recorded.ElementEvent(this.text));
		this.text = null;
		TableBuildStats.reportTwoPassGlyphRetention(this.glyphCount);
	}

	public void control(final TextControl quad) {
		final TwoPass inlineBlockMeasure;
		if (quad instanceof InlineBlockQuad inlineBlockQuad) {
			// ネストした実測ビルダーをイベントに内包する(旧: recordInlineBlocks 側チャネル)
			inlineBlockMeasure = this.pendingInlineBlock;
			assert inlineBlockMeasure != null;
			this.pendingInlineBlock = null;
			this.addRecord(new Recorded.InlineBlockEvent(inlineBlockQuad, inlineBlockMeasure));
		} else {
			inlineBlockMeasure = null;
			this.addRecord(new Recorded.ElementEvent(quad));
		}
		this.measurer.control(quad, inlineBlockMeasure);
	}

	public void flush() {
		this.measurer.flush();
	}

	public void finish() {
		this.flush();
	}

	public void close() {
		this.finish();
	}

	public void endTextBlock() {
		this.addRecord(Recorded.EndTextBlock.INSTANCE);
		this.measurer.endTextBlock();
	}

	public boolean isEmpty() {
		return this.records.isEmpty();
	}
}
