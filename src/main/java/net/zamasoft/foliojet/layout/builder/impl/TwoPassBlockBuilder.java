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

	/**
	 * bind() の再生元となる本文表現です(E-6増分4a、2026-07-24——
	 * {@code docs/consultations/consult-e6b-remaining-increments-codex.md}
	 * §3.2)。録画中は常に {@link LegacyRecords}。表外float/absolute/
	 * inline-blockの適格なビルダーは録画完了(close)時に
	 * {@link #sealBodyForRangeBind()} で {@link SourceRangeBody} へ
	 * 切り替わり、records(TextImplのglyph列・liveボックス)を手放す
	 * (E-6増分4b)。
	 */
	private sealed interface ReplayBody {
		/** 従来のイベント記録(records)による本文です。 */
		final class LegacyRecords implements ReplayBody {
			final List<Recorded> records = new ArrayList<Recorded>();
		}

		/**
		 * LayoutSourceの子イベント範囲 [fromId, toId] による本文です。
		 * bindは{@code SourceReplayer.bindTwoPassRange}(SegmentExecutor
		 * 駆動)で行われ、範囲はseal時に取得した{@code RetentionLease}が
		 * compactから守る。リースはbindのfinallyで解放される(冪等)。
		 */
		record SourceRangeBody(net.zamasoft.foliojet.layout.fragment.LayoutSource source,
				net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator, long fromId, long toId,
				net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease) implements ReplayBody {
		}
	}

	protected final LayoutStack layoutStack;

	/**
	 * 固有寸法の計測器。イベントを記録(records)と同時にこちらへ流し込みます。
	 */
	private final IntrinsicMeasurer measurer = new IntrinsicMeasurer(this);

	private TextImpl text;

	private final List<AbstractContainerBox> flowStack = new ArrayList<AbstractContainerBox>();

	/**
	 * bind() の再生元(E-6増分4a)。録画中は常に LegacyRecords。
	 */
	private ReplayBody body = new ReplayBody.LegacyRecords();

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
		if (!(this.body instanceof ReplayBody.LegacyRecords legacy)) {
			// seal(録画完了)後の追記は契約違反(E-6増分4a)
			throw new IllegalStateException("seal済みビルダーへの記録: " + recorded);
		}
		legacy.records.add(recorded);
		TableBuildStats.reportTwoPassRecordRetention(legacy.records.size());
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

	/**
	 * TwoPass range化(E-6増分4a/4b)の有効スイッチです。増分4bで
	 * default-onへ切替(E-3の教訓: default-off期間を作ると二重経路が
	 * 固定化する。適格判定自体がfail closedのため、怪しい範囲は常に
	 * LegacyRecordsに残る)。{@code foliojet.noTwoPassRangeBind}は退避用の
	 * kill switch。動的に読むのは、parityテストが同一JVM内でon/offを
	 * 切り替えてdisplay list一致を比較するため。
	 */
	private static boolean rangeBindEnabled() {
		return !Boolean.getBoolean("foliojet.noTwoPassRangeBind");
	}

	/**
	 * 録画完了(close)時のrange sealです(E-6増分4a/4b——codex設計§3.2の
	 * 増分4b「close時range seal、records解放」)。表外float/absolute/
	 * inline-blockの録画完了点({@code DocumentBuilder.endBox}の
	 * FLOAT/ABSOLUTE/INLINE(ブロック)ケースが endContainerBuilder 直後に
	 * 呼ぶ)で、本文をLayoutSource範囲参照({@link ReplayBody.SourceRangeBody})
	 * へ切り替え、records(TextImpl glyph列・liveボックス)を手放す。
	 * 計測器({@link IntrinsicMeasurer})はsealの影響を受けない——
	 * shrinkToFitへの固有寸法供給は従来どおり。
	 *
	 * <p>
	 * 適格判定はfail closed(不適格理由は
	 * {@code ContinuationStats.TwoPassSealReject})。冪等で、録画完了後の
	 * 一度だけ効く。プローブ等の非live実行では常にno-op
	 * (ProbePageGeneratorはliveログへの参照自体を禁じている)。
	 * </p>
	 */
	public void sealBodyForRangeBind() {
		if (!(this.body instanceof ReplayBody.LegacyRecords legacy)) {
			return; // 冪等
		}
		if (!rangeBindEnabled() || !net.zamasoft.foliojet.layout.fragment.LayoutExecutionScope.isLive()
				|| this.layoutStack == null) {
			return;
		}
		final RootBuilder root = this.getPageContext();
		if (root == null || !root.isSegmentRestyle()) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_SOURCE);
			return;
		}
		final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator = root.getPageGenerator();
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = pageGenerator.getLayoutSource();
		if (log == null) {
			// scratch計測(MeasurePageGenerator)等、ログを持たない文脈
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_SOURCE);
			return;
		}
		final long anchor = this.getRootBox().getSourceAnchor();
		// 絶対配置はOpaqueとして記録される(StyleBuilder.boxKindがnull)ため
		// endOfが-1になり、ここで構造的に不適格になる(fail closed)
		final long endId = anchor < 0 ? -1 : log.endOf(anchor);
		if (endId < 0) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_RANGE);
			return;
		}
		final long fromId = anchor + 1;
		final long toId = endId - 1;
		if (toId < fromId) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.EMPTY_RANGE);
			return;
		}
		if (log.containsOpaque(fromId, toId)) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.OPAQUE_RANGE);
			return;
		}
		if (log.containsMulticol(fromId, toId)) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.MULTICOL_RANGE);
			return;
		}
		if (log.containsMixedFlow(fromId, toId, this.getRootBox().getBlockParams().flow)) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.MIXED_FLOW_RANGE);
			return;
		}
		for (final Recorded recorded : legacy.records) {
			// ネストしたビルダーを含む場合は不適格(leaf限定——理由は
			// TwoPassSealReject.NESTED_BUILDERのjavadoc。子が既に取得した
			// リースを親のrange化で孤児にしない構造的保証でもある)
			if (recorded instanceof Recorded.StfBlock || recorded instanceof Recorded.AbsoluteBlock
					|| recorded instanceof Recorded.InlineBlockEvent || recorded instanceof Recorded.TableEvent) {
				reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NESTED_BUILDER);
				return;
			}
		}
		// 範囲の完全性(連番で穴なし)の最終検証。probeのリースは即時解放
		try (net.zamasoft.foliojet.layout.fragment.LayoutSource.ReplaySlice probe = log.capture(fromId, toId)) {
			if (probe == null) {
				reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.RANGE_NOT_INTACT);
				return;
			}
		}
		// seal: 以後の再生元は範囲参照。recordsはこの差し替えで手放される
		this.body = new ReplayBody.SourceRangeBody(log, pageGenerator, fromId, toId, log.retainFrom(fromId));
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassSealEligible();
	}

	private static void reject(final net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject reason) {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassSealReject(reason);
	}

	public void bind(BlockBuilder builder) {
		switch (this.body) {
		case ReplayBody.SourceRangeBody range -> {
			// E-6増分4b: seal済み範囲からのSegmentExecutor駆動bind。
			// リースはbindの完了・失敗を問わず解放する(取り残すと以後の
			// compactが永久にclampされる——LayoutSource.ReplaySliceと同じ規約)
			try {
				net.zamasoft.foliojet.layout.SourceReplayer.bindTwoPassRange(range.source(), range.fromId(),
						range.toId(), builder, range.pageGenerator());
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassRangeBind();
			} finally {
				range.lease().close();
			}
		}
		case ReplayBody.LegacyRecords legacy -> {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassLegacyRecordBind();
			this.bindRecords(builder, legacy.records);
		}
		}
	}

	private void bindRecords(BlockBuilder builder, final List<Recorded> records) {
		// 再レイアウト
		if (DEBUG) {
			System.err.println("BIND");
		}
		FilterGlyphHandler textUnitizer = null;

		for (final Recorded recorded : records) {
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
		// seal済み(SourceRangeBody)は適格判定が空範囲を除外しているため
		// 常に非空(E-6増分4a)
		return this.body instanceof ReplayBody.LegacyRecords legacy && legacy.records.isEmpty();
	}
}
