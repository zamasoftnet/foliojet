package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineBlockQuad;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.TwoPass;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.layout.segment.SegmentExecutor;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusEvent;
import net.zamasoft.foliojet.layout.segment.BarrierReason;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

public class TwoPassBlockBuilder implements Builder, LayoutStack, TwoPass {
	/** 計測中の状態と、確定後の再生元。計測中は本文を保持しない。 */
	private sealed interface ReplayBody {
		record Measuring() implements ReplayBody { }

		/** アンカーなしの独立再生。展開済みイベントだけを保持し、recordsもリースも持ちません。 */
		final class ReplayOnly implements ReplayBody {
			final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator;
			List<SegmentEvent> events = new ArrayList<>();
			long lastOrdinal = -1;
			boolean closed;
			boolean consumed;

			ReplayOnly(final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator) {
				this.pageGenerator = pageGenerator;
			}
		}

		/**
		 * LayoutSourceの子イベント範囲 [fromId, toId] による本文です。
		 * bindは{@code SourceReplayer.bindTwoPassRange}(SegmentExecutor
		 * 駆動)で行われ、範囲はseal時に取得した{@code RetentionLease}が
		 * compactから守る。リースの終端はRangeHandleが一度だけ受け付ける。
		 */
		record SourceRangeBody(RangeHandle handle,
				net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator) implements ReplayBody {
		}

		/**
		 * seal済み本文を{@link DeferredBind}へ持ち出した後の状態です
		 * (E-6増分4e)。リースの所有はDeferredBindへ移っており、この
		 * ビルダーへのbind要求は契約違反(このビルダー経由のbindは
		 * 以後起きない——deferred absoluteのbindはDeferredBindが担う)。
		 */
		record Detached() implements ReplayBody {
		}

		/** 空本文。MAIN bindは一度だけ受け付け、リースは不要。 */
		final class Empty implements ReplayBody {
			boolean consumed;
		}

		/**
		 * 親のrange化に吸収された後の状態です(DP増分3、2026-07-30——
		 * codex相談 consult-codex-2026-07-30-dualpath-endgame.txt
		 * NESTED_BUILDER解消)。親の{@code SourceRangeBody}が子の範囲を
		 * 包含し、bindは親の範囲再生(SegmentExecutor)が子の内容ごと
		 * 再構築する——このビルダーへのbind要求は契約違反
		 * ({@link Detached}と同じ扱い)。子が保持していたリースは吸収時に
		 * 解放済み(親リースが先に取得されているためcompact可能水位は
		 * 後退しない)。
		 */
		record Subsumed() implements ReplayBody {
		}
	}


	/**
	 * seal済み本文と固有寸法の持ち出し形。絶対配置・表セル・Grid/Flex項目が
	 * 計測builderを保持せずに再生するために使う。空本文と独立再生も運べる。
	 *
	 * <p>sizesはIntrinsicMeasurerのスナップショット。範囲のリースは
	 * MAIN bind、親への吸収、文書終了時の破棄のいずれかで一度だけ解放する。
	 * scratch計測は元の本文を消費しない。</p>
	 */
	public static final class DeferredBind {
		private final RootBuilder pageContext;
		private final RangeHandle handle;
		private final ReplayBody body;
		private final IntrinsicSizes sizes;
		private final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator;
		private final ContinuationStats.TwoPassCensusTag censusTag;
		private final java.util.Set<Long> ownedAbsoluteAnchors;

		private DeferredBind(final RootBuilder pageContext, final ReplayBody body, final IntrinsicSizes sizes,
				final ContinuationStats.TwoPassCensusTag censusTag, final java.util.Set<Long> ownedAbsoluteAnchors) {
			this.pageContext = pageContext;
			this.handle = body instanceof ReplayBody.SourceRangeBody range ? range.handle() : null;
			// 範囲本文はhandleが正本。持ち出し後にSourceRangeBodyと寸法の
			// 二つ目のsnapshotを全セル分保持しない。
			this.body = this.handle == null ? body : null;
			this.sizes = this.handle == null ? sizes : this.handle.sizes();
			this.pageGenerator = body instanceof ReplayBody.SourceRangeBody range ? range.pageGenerator() : null;
			this.censusTag = censusTag;
			this.ownedAbsoluteAnchors = java.util.Set.copyOf(ownedAbsoluteAnchors);
		}

		/** 固有寸法(模倣計測のスナップショット——クラスjavadoc参照)。 */
		public IntrinsicSizes sizes() {
			return this.sizes;
		}

		/** bind用のページ文脈({@code new BlockBuilder(pageContext, box)}の第1引数)。 */
		public RootBuilder pageContext() {
			return this.pageContext;
		}

		/**
		 * seal済み範囲を{@code builder}へ再駆動します
		 * ({@link TwoPassBlockBuilder#bind}のSourceRangeBody armと同型。
		 * リースは完了・失敗を問わず解放する)。
		 */
		public void bind(final BlockBuilder builder) {
			if (ReplayIntent.current() == ReplayIntent.MEASURE) {
				this.measureInto(builder);
				return;
			}
			if (this.pageContext != null) {
				this.pageContext.enterTranslateBlockScope();
			}
			try {
				if (this.handle == null) {
					bindWithoutRange(this.body, builder, this.censusTag);
					return;
				}
				this.handle.bind(builder, this.pageGenerator);
				if (this.censusTag != null) {
					this.censusTag.record(TwoPassCensusEvent.BIND);
				}
			} finally {
				if (this.pageContext != null) {
					this.pageContext.exitTranslateBlockScope();
				}
			}
		}

		/** 範囲本文の所有ハンドル。空本文・独立再生ではnull。 */
		public RangeHandle handle() {
			return this.handle;
		}

		/**
		 * 表Pass B(行計測)用にseal済み範囲を{@code builder}へ再駆動します
		 * (E-6増分5b-1、2026-07-24——codex設計§4.4)。{@link #bind}と同じ
		 * SegmentExecutor駆動だが、<b>リースを解放しない</b>(後続の本bindが
		 * 同じ範囲をもう一度captureする——captureはslice自身のリースを都度
		 * 取得・解放する非破壊読み)。統計(TWO_PASS_RANGE_BINDS)も計上しない
		 * (seal:bind 1:1検証を汚さない)。
		 */
		public void measureInto(final BlockBuilder builder) {
			try (ContinuationStats.TwoPassMeasurement measurement =
					ContinuationStats.twoPassMeasurement(ReplayIntent.MEASURE)) {
				if (this.handle == null) {
					try (ReplayIntent.Scope intent = ReplayIntent.MEASURE.enter();
							ScratchReplayScope scratch = new ScratchReplayScope()) {
						bindWithoutRange(this.body, builder, this.censusTag);
					}
					return;
				}
				this.handle.measure(builder, this.pageGenerator);
				if (this.censusTag != null) {
					this.censusTag.record(TwoPassCensusEvent.MEASURE_RANGE);
				}
			}
		}

		/**
		 * このseal済み本文が{@code log}上の[from, to]に包含されるかを
		 * 返します(表吸収=codex増分5、2026-07-30。親range化の検証相が使う
		 * ——副作用なし)。
		 */
		boolean within(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long from, final long to) {
			return this.handle != null && this.handle.state() == RangeHandle.State.OPEN && this.handle.source() == log
					&& !this.handle.hasTextSlice()
					&& this.handle.fromId() >= from && this.handle.toId() <= to;
		}

		/** セルのsealで検証済みの所有証明を、包含確認後に親のexact照合へ引き継ぐ。 */
		boolean collectAbsorbableInto(final net.zamasoft.foliojet.layout.fragment.LayoutSource log,
				final long from, final long to, final java.util.Set<Long> anchors) {
			if (this.body instanceof ReplayBody.Empty empty) return !empty.consumed;
			if (!this.within(log, from, to)) return false;
			for (final long anchor : this.ownedAbsoluteAnchors) {
				if (!anchors.add(anchor)) return false;
			}
			return true;
		}

		/**
		 * 親のrange化への吸収です(表吸収=codex増分5のコミット相)。
		 * ハンドルをSUBSUMEDへ遷移し、seal:bind収支のSUBSUMED側を
		 * 計上する(セル専用の収支も同じハンドルが計上する)。
		 * 呼び出し時点で親のリースは取得済みであること
		 * (compact可能水位の順序契約)。
		 */
		void abandonForParentRange() {
			if (this.handle != null) this.handle.subsume();
			else if (this.body instanceof ReplayBody.Empty empty) empty.consumed = true;
		}

		boolean isEmpty() { return this.body instanceof ReplayBody.Empty; }
	}

	protected final LayoutStack layoutStack;

	/**
	 * 固有寸法の計測器。本文の給餌を受けて固有寸法だけを求めます。
	 */
	private final IntrinsicMeasurer measurer = new IntrinsicMeasurer(this);

	private TextImpl text;

	private final List<AbstractContainerBox> flowStack = new ArrayList<AbstractContainerBox>();

	/**
	 * bind() の再生元。計測中は本文を保持せず、closeで確定する。
	 */
	private ReplayBody body = new ReplayBody.Measuring();

	/** 子または計画の所有ノードができるまで、空の台帳をセルごとに割り当てない。 */
	private OwnershipLedger ownershipLedger;

	OwnershipLedger ownershipLedger() {
		if (this.ownershipLedger == null) {
			this.ownershipLedger = new OwnershipLedger(this);
			this.ownershipLedger.bodyChanged(this.bodyState(), this.rangeHandle());
		}
		return this.ownershipLedger;
	}

	public String ownershipState() {
		return this.bodyState().name();
	}

	RangeHandle rangeHandle() {
		return this.body instanceof ReplayBody.SourceRangeBody range ? range.handle() : null;
	}

	OwnershipLedger.State bodyState() {
		return switch (this.body) {
		case ReplayBody.Measuring measuring -> OwnershipLedger.State.RECORDING;
		case ReplayBody.ReplayOnly replay -> replay.consumed ? OwnershipLedger.State.CONSUMED
				: OwnershipLedger.State.REPLAY_ONLY;
		case ReplayBody.SourceRangeBody range -> switch (range.handle().state()) {
			case OPEN -> OwnershipLedger.State.SEALED;
			case CONSUMED -> OwnershipLedger.State.CONSUMED;
			case SUBSUMED -> OwnershipLedger.State.SUBSUMED;
			case ABANDONED -> OwnershipLedger.State.ABANDONED;
		};
		case ReplayBody.Empty empty -> empty.consumed ? OwnershipLedger.State.CONSUMED : OwnershipLedger.State.EMPTY;
		case ReplayBody.Detached detached -> OwnershipLedger.State.DETACHED;
		case ReplayBody.Subsumed subsumed -> OwnershipLedger.State.SUBSUMED;
		};
	}

	/** 本文の所有遷移を台帳へも通知する。台帳が子の吸収可否を判定する。 */
	private void setBody(final ReplayBody body) {
		this.body = body;
		if (!(body instanceof ReplayBody.Measuring) && !(body instanceof ReplayBody.ReplayOnly)) {
			// 計測中のrunと直前pair参照も、確定本文からは保持しない。
			this.text = null;
			this.autospace = null;
		}
		if (this.ownershipLedger != null) this.ownershipLedger.bodyChanged(this.bodyState(), this.rangeHandle());
	}

	/**
	 * 直近のinline-blockの計測token。子の録画が終わってquadが届く時点で
	 * 固有寸法を読む。本文の再生とは独立している。
	 */
	private record InlineMeasureToken(TwoPass builder) implements TwoPass {
		@Override
		public IntrinsicSizes getIntrinsicSizes() {
			return this.builder.getIntrinsicSizes();
		}
	}

	// quad到着までの対応付け。本文の再生元とは独立した計測token。
	private InlineMeasureToken pendingInlineMeasure;

	private boolean hasLayoutContent;

	// seal時にexact照合を通った所有証明。通常の子rangeからも親へ引き継ぐ。
	private java.util.Set<Long> rangeOwnedAbsoluteAnchors = java.util.Set.of();

	private final ContinuationStats.TwoPassCensusTag censusTag;

	/** 範囲censusの根の分類。本文の保持には影響しない。 */
	public void tagRootKind(final ContinuationStats.TwoPassRootKind kind) {
		if (this.censusTag != null) this.censusTag.rootKind(kind);
	}

	public TwoPassBlockBuilder(LayoutStack layoutStack, AbstractContainerBox containerBox) {
		this.layoutStack = layoutStack;
		this.censusTag = ContinuationStats.newTwoPassCensusTag();
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
	 * 独立再生の録画を開始します。固有寸法の計測は必要ですが、入力は既に
	 * 展開済みなので、glyph列・live boxのrecordsを再び保持する必要はありません。
	 */
	public void startReplayOnly(final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator) {
		if (!(this.body instanceof ReplayBody.Measuring) || this.hasLayoutContent) {
			throw new IllegalStateException("独立再生は本文の給餌前に開始します");
		}
		this.setBody(new ReplayBody.ReplayOnly(pageGenerator));
	}

	/** DocumentBuilderの境界判断と同じ順序で、独立イベントを保持します。 */
	public void recordReplayOnlyEvent(final SegmentEvent event, final long ordinal) {
		if (this.body instanceof ReplayBody.ReplayOnly replay) {
			if (replay.closed) {
				throw new IllegalStateException("終了済み独立再生への追記");
			}
			replay.events.add(event);
			replay.lastOrdinal = ordinal;
		}
	}

	/** 自分のEndまたは次の兄弟の開始を除き、本文イベントを確定します。 */
	public void finishReplayOnly(final long ordinal, final boolean includeClosingEvent) {
		if (this.body instanceof ReplayBody.ReplayOnly replay) {
			if (replay.closed) {
				throw new IllegalStateException("独立再生本文の二重終了");
			}
			if (!includeClosingEvent && replay.lastOrdinal == ordinal && !replay.events.isEmpty()) {
				replay.events.remove(replay.events.size() - 1);
			}
			replay.events = List.copyOf(replay.events);
			replay.closed = true;
		}
	}

	/** 折り畳み後の文字・制御・箱が計測器へ届いたことを記録する。 */
	private void noteLayoutContent() {
		if (!(this.body instanceof ReplayBody.Measuring)
				&& !(this.body instanceof ReplayBody.ReplayOnly)) {
			throw this.invariant("給餌終了後のレイアウト内容");
		}
		this.hasLayoutContent = true;
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
		this.noteLayoutContent();
	}

	public void endFlowBlock() {
		// 通常のフローのブロックボックス
		AbstractBlockBox flowBox = (AbstractBlockBox) this.flowStack.remove(this.flowStack.size() - 1);
		this.measurer.endFlow(flowBox);
		this.noteLayoutContent();
	}

	public void addBound(IBox box) {
		AbstractReplacedBox replacedBox = (AbstractReplacedBox) box;
		this.measurer.bound(replacedBox);
		this.noteLayoutContent();
	}

	public void addTable(net.zamasoft.foliojet.layout.builder.RetainedTable autoTableBuilder) {
		autoTableBuilder.prepareLayout();
		final IntrinsicSizes tableSizes = autoTableBuilder.getIntrinsicSizes();
		this.measurer.table(tableSizes);
		this.noteLayoutContent();
		this.ownershipLedger().addPlan(autoTableBuilder, OwnershipLedger.Kind.TABLE);
		switch (autoTableBuilder.getTableBox().getBlockBox().getPos().getType()) {
		case INLINE:
			this.pendingInlineMeasure = new InlineMeasureToken(autoTableBuilder);
			break;
		}
	}

	public void addGrid(final net.zamasoft.foliojet.layout.builder.RetainedGrid gridBuilder) {
		// Grid G3d1/d2(consult-codex-2026-07-31-grid-g3.txt Q3): TwoPass
		// 宿主では実行計画を台帳に登録し、Gridのcontent-box固有寸法を計測器へ
		// 伝える(GridBoxのframeはstartFlowBlock→measurer.startFlowの
		// 通常経路が一度だけ加算する——二重計上防止は答申Q5)。
		// Gridは常にFLOW配置のためinline-block計測tokenは不要
		this.measurer.grid(gridBuilder.getIntrinsicSizes(), gridBuilder.getGridBox());
		this.noteLayoutContent();
		this.ownershipLedger().addPlan(gridBuilder, OwnershipLedger.Kind.GRID);
	}

	public void addFlex(final net.zamasoft.foliojet.layout.builder.RetainedFlex flexBuilder) {
		// Flex F1f(addGridと同型): 実行計画を台帳に登録し、Flexのcontent-box
		// 固有寸法を計測器へ伝える(frameは通常経路が一度だけ加算)
		this.measurer.flex(flexBuilder.getIntrinsicSizes(), flexBuilder.getFlexBox());
		this.noteLayoutContent();
		this.ownershipLedger().addPlan(flexBuilder, OwnershipLedger.Kind.FLEX);
	}

	public Builder newBuilder(final AbstractBlockBox stfBox) {
		// * TODO 絶対幅の場合はBoundContainerContextが使えますが、
		// * 絶対配置の位置調整を構築後に行わないといけないため
		// * そのままにしています。
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this, stfBox);
		builder.tagRootKind(
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind.NESTED);
		final AbstractContainerBox box = this.getFlowBox();
		stfBox.firstPassLayout(box);
		switch (stfBox.getPos().getType()) {
		case FLOW:
			// 書字方向が違う
		case FLOAT:
			// 浮動体
			if (stfBox.getPos() instanceof net.zamasoft.foliojet.layout.box.params.PageFloatPos pageFloat) {
				this.noteLayoutContent();
				this.ownershipLedger().addChild(builder, OwnershipLedger.Kind.PAGE_FLOAT);
			} else if (stfBox.getPos() instanceof net.zamasoft.foliojet.layout.box.params.PageMarginNotePos note) {
				this.noteLayoutContent();
				this.ownershipLedger().addChild(builder, OwnershipLedger.Kind.MARGIN_NOTE);
			} else if (stfBox.getPos() instanceof net.zamasoft.foliojet.layout.box.params.FootnotePos) {
				this.noteLayoutContent();
				this.ownershipLedger().addChild(builder, OwnershipLedger.Kind.FOOTNOTE);
			} else {
				this.noteLayoutContent();
				this.ownershipLedger().addChild(builder, OwnershipLedger.Kind.STF);
			}
			break;

		case ABSOLUTE:
			// 絶対配置
			this.noteLayoutContent();
			this.ownershipLedger().addChild(builder, OwnershipLedger.Kind.ABSOLUTE);
			break;

		case INLINE:
			// インラインブロック
			this.pendingInlineMeasure = new InlineMeasureToken(builder);
			break;

		default:
			throw new IllegalStateException();
		}
		return builder;
	}

	public void fitFloating(TwoPassBlockBuilder childBuilder) {
		this.measurer.fitFloating(childBuilder);
	}

	/** ネストしたshrink-to-fitブロックの固有寸法を親の軸へ換算します。 */
	public void fitBlock(final TwoPassBlockBuilder childBuilder) {
		this.measurer.fitBlock(childBuilder);
	}

	/** close時に本文範囲を確定する。不適格は変換を失敗させる。 */
	public void sealBodyForRangeBind() {
		this.sealBodyForRangeBind(this.getRootBox().getSourceAnchor(), RangeHandle.ReplayMode.CHILDREN_ONLY);
	}

	/** 即時配置表のセルcloseだけが文字本文の切り出しを許す。 */
	void sealCellBodyForRangeBind(final boolean sliceText) {
		this.sealBodyForRangeBind(this.getRootBox().getSourceAnchor(), RangeHandle.ReplayMode.CHILDREN_ONLY, sliceText);
	}

	/** 項目closeから呼ぶ。anchorはauthored child、匿名項目では合成Startのもの。 */
	void sealBodyForRangeBind(final long anchor, final RangeHandle.ReplayMode mode) {
		this.sealBodyForRangeBind(anchor, mode, false);
	}

	private void sealBodyForRangeBind(final long anchor, final RangeHandle.ReplayMode mode, final boolean sliceText) {
		if (!(this.body instanceof ReplayBody.Measuring)) {
			return; // 冪等
		}
		this.sealAnchor = anchor;
		if (this.layoutStack == null) {
			this.reject(ContinuationStats.TwoPassSealReject.NO_SOURCE);
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
		// Opaque記録の種別(表・表キャプション)はendOfが-1になり、ここで
		// 構造的に不適格になる(fail closed)。絶対配置はE-6増分4eの
		// recipe記録化でendOfが引けるようになった(NO_RANGE=81の解消)
		final long endId = anchor < 0 ? -1
				: mode == RangeHandle.ReplayMode.ROOTED_SUBTREE && log.get(anchor) instanceof net.zamasoft.foliojet.layout.fragment.LayoutSource.Replaced
						? anchor : log.endOf(anchor);
		this.sealEnd = endId;
		if (endId < 0) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_RANGE,
					this.censusTag != null && log.get(anchor) instanceof net.zamasoft.foliojet.layout.fragment.LayoutSource.Opaque
							? BarrierReason.NOT_YET_SUPPORTED : null);
			return;
		}
		final boolean childrenOnly = switch (mode) {
		case CHILDREN_ONLY, ANONYMOUS_CHILDREN -> true;
		case ROOTED_SUBTREE -> false;
		};
		final long fromId = childrenOnly ? anchor + 1 : anchor;
		final long toId = childrenOnly ? endId - 1 : endId;
		if (toId < fromId) {
			if (!this.hasLayoutContent) {
				// ソースも計測内容も空なら、本文を持たない終端とする。
				this.setBody(new ReplayBody.Empty());
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassEmptySeal();
				if (this.censusTag != null) {
					this.censusTag.seal(true, "accepted", null);
				}
			} else {
				reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_RANGE);
			}
			return;
		}
		final boolean opaque = log.containsOpaque(fromId, toId);
		if (opaque || log.captionSealGate(fromId, toId)) {
			// containsCaption(caption recipe化C1): キャプションはOpaque記録
			// からrecipe記録へ移ったが、C2のcontext-complete検証までは
			// 従来と同じ範囲を同じ理由(OPAQUE_RANGE)で弾く——routing不変。
			// 旧コメントの「キャプション付き表はOpaque記録のためここが弾く」
			// はこの分岐が引き継いだ
			// containsOpaqueは先頭欠落でもtrue。実Opaqueだけがconverterの
			// NOT_YET_SUPPORTEDに対応する。caption gate自体はBarrierではない。
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.OPAQUE_RANGE,
					this.censusTag != null && opaque && log.get(fromId) != null
							? BarrierReason.NOT_YET_SUPPORTED : null);
			return;
		}
		// 検証相はledgerのみを走査する。子の解放は親リース取得後。
		final List<TwoPassBlockBuilder> absorbable = new ArrayList<TwoPassBlockBuilder>();
		final List<RetainedTableBuilder> absorbableTables = new ArrayList<RetainedTableBuilder>();
		final List<RangeHandle> absorbableRanges = new ArrayList<>();
		final java.util.Set<Long> ownedAbsoluteAnchors = new java.util.HashSet<Long>();
		final boolean nestedAccepted = this.collectAbsorbableChildren(log, fromId, toId, absorbable,
				absorbableTables, absorbableRanges, ownedAbsoluteAnchors,
				java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
		if (!nestedAccepted) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NESTED_BUILDER);
			return;
		}
		if (!log.absoluteStartsExactly(fromId, toId, ownedAbsoluteAnchors)) {
			// absolute吸収(codex増分9): 範囲内のAbsolute Startのうちownership ledger
			// が所有を証明できないものが残る(外側context・別実行計画の所有
			// など)——fail closed
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.ABSOLUTE_RANGE);
			return;
		}
		// 範囲の完全性(連番で穴なし)の最終検証。probeのリースは即時解放
		try (net.zamasoft.foliojet.layout.fragment.LayoutSource.ReplaySlice probe = log.capture(fromId, toId)) {
			if (probe == null) {
				reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.RANGE_NOT_INTACT);
				return;
			}
		}
		// seal(コミット相): 子は親リース取得後に吸収(リース解放+Subsumed化)する
		this.setBody(new ReplayBody.SourceRangeBody(new RangeHandle(log, fromId, toId,
				this.measurer.sizes(), mode, sliceText), pageGenerator));
		if (this.censusTag != null) {
			this.censusTag.seal(true, "accepted", null);
		}
		this.rangeOwnedAbsoluteAnchors = java.util.Set.copyOf(ownedAbsoluteAnchors);
		for (final RangeHandle range : absorbableRanges) {
			range.subsume();
		}
		for (final TwoPassBlockBuilder child : absorbable) {
			child.subsumeIntoParentRange();
		}
		for (final RetainedTableBuilder table : absorbableTables) {
			// 表吸収(codex増分5): seal済みセルのリース解放+計画のabandon。
			// 親の範囲再生がソースから表全体を再構築する
			table.abandonForParentRange();
		}
		if (this.ownershipLedger != null) this.ownershipLedger.plansSubsumed();
	}

	/**
	 * 記録済みRetained表計画1個の吸収可否検証です(表吸収=codex増分5、
	 * 検証相・副作用なし)。表とインライン計測tokenは同一計画をidentityで共有しうるため、outTablesの重複を
	 * 冪等スキップする。
	 */
	static boolean collectAbsorbableTable(final RetainedTableBuilder retained,
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId, final long toId,
			final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final List<net.zamasoft.foliojet.layout.fragment.RangeHandle> outRanges,
			final java.util.Set<Long> ownedAbsoluteAnchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		for (int i = 0; i < outTables.size(); ++i) {
			if (outTables.get(i) == retained) {
				return true;
			}
		}
		final var table = retained.getTableBox();
		final long anchor = table.getSourceAnchor();
		final long end = log.endOf(anchor);
		if (anchor < fromId || end < anchor || end > toId
				|| !(log.get(anchor) instanceof net.zamasoft.foliojet.layout.fragment.LayoutSource.Start start)
				|| start.recipe().kind() != net.zamasoft.foliojet.layout.segment.BoxKind.TABLE) {
			return false;
		}
		if (table.getBlockBox() instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox absolute) {
			// 配置付き表は表計画として排他所有する。
			// 内外が同じStartを指し、未係留であることを確かめ、最後のexact照合へ渡す。
			if (!(start.recipe() instanceof net.zamasoft.foliojet.layout.segment.BoxRecipe.PlacedTable placed
					&& placed.placement() instanceof net.zamasoft.foliojet.layout.segment.BoxRecipe.Absolute)
					|| absolute.getSourceAnchor() != anchor || !absolute.isUnattachedForParentRange()
					|| !ownedAbsoluteAnchors.add(anchor)) {
				return false;
			}
		}
		if (!retained.collectAbsorbableInto(log, fromId, toId, out, outTables, outRanges, ownedAbsoluteAnchors, seen)) {
			return false;
		}
		outTables.add(retained);
		return true;
	}

	/** 表・項目からの吸収可否も同じledgerで判定する。 */
	boolean collectAbsorbableSelf(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId,
			final long toId, final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final List<RangeHandle> outRanges, final java.util.Set<Long> anchors,
			final java.util.Set<TwoPassBlockBuilder> seen) {
		return OwnershipLedger.collectSelf(this, log, fromId, toId, out, outTables, outRanges, anchors, seen);
	}

	boolean collectAbsorbableChildren(final net.zamasoft.foliojet.layout.fragment.LayoutSource log,
			final long fromId, final long toId, final List<TwoPassBlockBuilder> out,
			final List<RetainedTableBuilder> outTables, final List<RangeHandle> outRanges,
			final java.util.Set<Long> anchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		if (this.ownershipLedger != null) {
			return this.ownershipLedger.collectAbsorbable(log, fromId, toId, out, outTables, outRanges, anchors, seen);
		}
		OwnershipLedger.observeCollection(this);
		return true;
	}

	/**
	 * 親のrange化に吸収されます(DP増分3のコミット相)。呼び出し時点で
	 * 親のリースは取得済みであること(子リース解放でcompact可能水位が
	 * 後退しないための順序契約)。リースcloseは冪等・非throwing。
	 */
	private void subsumeIntoParentRange() {
		if (this.body instanceof ReplayBody.SourceRangeBody range) {
			range.handle().subsume();
		}
		this.setBody(new ReplayBody.Subsumed());
		if (this.ownershipLedger != null) this.ownershipLedger.plansSubsumed();
	}

	private void reject(final net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject reason) {
		this.reject(reason, null);
	}

	private void reject(final net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject reason,
			final BarrierReason barrier) {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTwoPassSealReject(reason);
		if (this.censusTag != null) {
			this.censusTag.seal(true, reason.name(), barrier);
		}
		throw this.invariant(reason.name());
	}

	private long sealAnchor = -1, sealEnd = -1;

	ContinuationInvariantViolationException invariant(final String reason) {
		final RootBuilder root = this.layoutStack == null ? null : this.getPageContext();
		final var ua = root == null ? null : root.getPageGenerator().getUserAgent();
		return new ContinuationInvariantViolationException("TwoPass " + reason
				+ " uri=" + (ua == null ? "<unknown>" : ua.getDocumentContext().getBaseURI())
				+ " EventId=[" + this.sealAnchor + "," + this.sealEnd + "] box kind="
				+ this.getRootBox().getClass().getSimpleName() + " owner state=" + this.ownershipState());
	}

	/** 空本文は計測内容で判定する。未seal本文をbindする許可ではない。 */
	boolean hasEmptyBody() {
		return this.body instanceof ReplayBody.Empty
				|| this.body instanceof ReplayBody.ReplayOnly replay && replay.closed && !this.hasLayoutContent
				|| this.body instanceof ReplayBody.Measuring && !this.hasLayoutContent;
	}

	ContinuationStats.TwoPassCensusTag itemCensusTag() {
		return this.censusTag;
	}

	void tagItemKind(final boolean anonymous, final boolean takeover) {
		if (this.censusTag != null) {
			this.censusTag.itemKind(anonymous ? ContinuationStats.TwoPassItemKind.ANONYMOUS
					: takeover ? ContinuationStats.TwoPassItemKind.TAKEOVER : ContinuationStats.TwoPassItemKind.ELEMENT);
		}
	}

	java.util.Set<Long> rangeOwnedAbsoluteAnchors() {
		return this.rangeOwnedAbsoluteAnchors;
	}

	/** 折り畳み後に内容が給餌されたか。匿名項目の破棄判定はrecordsに依存しない。 */
	boolean hasLayoutContent() {
		return this.hasLayoutContent;
	}

	/** 確定本文を持ち出す。空・独立再生もbuilderを保持しない。 */
	public DeferredBind detachDeferredBind() {
		if (!(this.body instanceof ReplayBody.SourceRangeBody) && !(this.body instanceof ReplayBody.Empty)
				&& !(this.body instanceof ReplayBody.ReplayOnly replay && replay.closed)) {
			throw this.invariant("未seal本文のdetach");
		}
		final DeferredBind deferred = new DeferredBind(this.layoutStack == null ? null : this.getPageContext(),
				this.body, this.body instanceof ReplayBody.SourceRangeBody range ? range.handle().sizes() : this.measurer.sizes(),
				this.censusTag, this.rangeOwnedAbsoluteAnchors);
		this.setBody(new ReplayBody.Detached());
		return deferred;
	}

	public void bind(BlockBuilder builder) {
		this.bind(builder, ReplayIntent.current());
	}

	/**
	 * 記録した本文を{@code builder}へ再生します。
	 *
	 * <p>
	 * <b>{@code intent}=MEASUREは使い捨て計測の最中の再生</b>です(2026-08-03
	 * 新設)。使用権(リース)を<b>解放せず</b>、統計にも数えません——同じ
	 * 範囲を本番のbindがもう一度読むからです。
	 * </p>
	 *
	 * <p>
	 * これが無かったために、<b>使い捨ての計測が本文を使い切ってしまい、
	 * 本番では空になる</b>という内容消失が起きていた。表の行の計測は
	 * 記録した範囲を捨てるつもりで再生するが、その途中で入れ子の浮動体の
	 * 本文が「本番として」bindされ、使用権が閉じられていた。再現は
	 * {@code files/fuzz-repro/nested-float-content-loss.html}(細い箱・
	 * 表・右寄せ・左寄せの4つが揃うと、内側の浮動体の文字が消える)。
	 * 絶対配置は同じ問題を2026-07-30に別の形(scratchでは丸ごと飛ばす)で
	 * 塞いであるが、浮動体は計測値に寄与するため飛ばせない——だから
	 * 「消費しない再生」が要る。
	 * </p>
	 */
	public void bind(final BlockBuilder builder, final ReplayIntent intent) {
		final RootBuilder root = builder.getPageContext();
		if (root != null) {
			root.enterTranslateBlockScope();
		}
		try (ReplayIntent.Scope replay = intent.enter();
				ScratchReplayScope scratch = ReplayIntent.current() == ReplayIntent.MEASURE ? new ScratchReplayScope() : null;
				ContinuationStats.TwoPassMeasurement measurement = ContinuationStats.twoPassMeasurement(ReplayIntent.current())) {
			switch (this.body) {
			case ReplayBody.SourceRangeBody range -> {
				final boolean measuring = ReplayIntent.current() == ReplayIntent.MEASURE;
				if (measuring) {
					range.handle().measure(builder, range.pageGenerator());
				} else {
					range.handle().bind(builder, range.pageGenerator());
				}
				if (this.censusTag != null) {
					this.censusTag.record(measuring ? TwoPassCensusEvent.MEASURE_RANGE : TwoPassCensusEvent.BIND);
				}
			}
			case ReplayBody.ReplayOnly replayOnly -> bindWithoutRange(replayOnly, builder, this.censusTag);
			case ReplayBody.Empty empty -> bindWithoutRange(empty, builder, this.censusTag);
			case ReplayBody.Measuring measuring -> throw this.invariant("未seal本文のbind");
			case ReplayBody.Detached detached ->
				// E-6増分4e: DeferredBindへ持ち出し済み。bindはDeferredBindが担う
				throw new IllegalStateException("DeferredBindへ持ち出し済みのビルダーへのbind");
			case ReplayBody.Subsumed subsumed ->
				// DP増分3: 親の範囲再生が内容ごと再構築する。個別bindは契約違反
				throw new IllegalStateException("親のrange化に吸収済みのビルダーへのbind");
			}
			if (ReplayIntent.current() == ReplayIntent.MAIN) {
				if (this.ownershipLedger != null) this.ownershipLedger.bound();
			}
		} finally {
			if (intent == ReplayIntent.MAIN) {
				this.text = null;
				this.autospace = null;
			}
			if (root != null) {
				root.exitTranslateBlockScope();
			}
		}
	}

	/** leaseなし本文の共通駆動。通常ソースの不適格時には到達しない。 */
	private static void bindWithoutRange(final ReplayBody body, final BlockBuilder builder,
			final ContinuationStats.TwoPassCensusTag censusTag) {
		switch (body) {
		case ReplayBody.Empty empty -> {
			if (empty.consumed) throw new IllegalStateException("空本文の再bind");
			if (ReplayIntent.current() == ReplayIntent.MAIN) {
				empty.consumed = true;
				ContinuationStats.recordTwoPassEmptyBind();
				if (censusTag != null) censusTag.record(TwoPassCensusEvent.EMPTY_BIND);
			}
		}
		case ReplayBody.ReplayOnly replay -> {
			if (!replay.closed || replay.consumed) throw new IllegalStateException("独立再生本文の状態違反");
			if (ReplayIntent.current() == ReplayIntent.MAIN) replay.consumed = true;
			try {
				final DocumentBuilder doc = new DocumentBuilder(replay.pageGenerator, builder, ReplayIntent.current());
				new SegmentExecutor(doc, SegmentExecutor.AnchorMode.NONE).drive(replay.events);
				doc.finishReplay();
				ContinuationStats.TWO_PASS_REPLAY_ONLY_BINDS.incrementAndGet();
			} finally {
				if (replay.consumed) replay.events = List.of();
			}
		}
		case ReplayBody.Measuring measuring -> throw new IllegalStateException("未seal本文のbind");
		case ReplayBody.SourceRangeBody range -> throw new IllegalStateException("範囲本文はRangeHandleで再生する");
		case ReplayBody.Detached detached -> throw new IllegalStateException("持ち出し済み本文のbind");
		case ReplayBody.Subsumed subsumed -> throw new IllegalStateException("吸収済み本文のbind");
		}
	}

	/** 和文詰めA2: text-autospaceのpair追跡(初回glyphで遅延初期化)。 */
	private net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker autospace;

	public void startTextRun(int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.text = new TextImpl(charOffset, fontStyle, fontMetrics);
		this.lastRunFontStyle = fontStyle;
		this.lastRunFontMetrics = fontMetrics;
	}

	/** 直近の run の書体(run が閉じた後に届く glyph の遅延再開用)。 */
	private FontStyle lastRunFontStyle;
	private FontMetrics lastRunFontMetrics;

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		// gap・trimは固有寸法にだけ反映する。範囲再生ではTextBuilderが
		// 再計測する。幅式はIntrinsicMeasurer.glyphに集約する。
		if (this.autospace == null) {
			this.autospace = new net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker();
			final net.zamasoft.foliojet.layout.box.params.AbstractTextParams params = //
					(net.zamasoft.foliojet.layout.box.params.AbstractTextParams) this.getRootBox().getParams();
			this.autospace.setFlags(params.textAutospace);
			this.autospace.setTrimOff(params.textSpacingTrimOff);
		}
		if (this.text == null) {
			// run が閉じた後に glyph が届く(表の caption の中の ::before/::after の生成
			// 内容で実測、2026-09-05: CharacterHandler が endRun した後に保留 glyph が
			// flush される)。BlockBuilder(:1927)と同じく直近の書体で run を遅延再開する。
			if (this.lastRunFontStyle == null) {
				throw new IllegalStateException("glyph before any text run");
			}
			this.startTextRun(charOffset, this.lastRunFontStyle, this.lastRunFontMetrics);
		}
		final double fontSize = this.text.getFontStyle().getSize();
		final double gap = this.autospace.gapBefore(ch, coff, fontSize);
		final double trim = this.autospace.trimBefore(ch, coff, gid, this.text,
				this.text.getFontMetrics(), fontSize, this.text.getFontStyle().getDirection());
		// appendGlyph はrun内の字間計測用にアドバンスを返すため、
		// 呼び出しは一度だけ行い、結果を計測器へ渡す。
		this.measurer.glyph(this.text.appendGlyph(ch, coff, clen, gid), gap, trim);
		this.autospace.glyphAdded(this.text, fontSize, ch, coff, clen, gid);
		this.noteLayoutContent();
	}

	public void endTextRun() {
		// run内の字間計測にだけ使い、本文としては保持しない。
		this.text = null;
	}

	public void control(final TextControl quad) {
		// 和文詰めA2: 制御はpairを断つ(TextBuilder側と同じ規約——幅0の
		// インライン開始/終了だけはpairを維持)
		if (this.autospace != null && !(quad instanceof InlineQuad inlineQuad
				&& (inlineQuad.getType() == InlineQuad.INLINE_START
						|| inlineQuad.getType() == InlineQuad.INLINE_END)
				&& inlineQuad.getAdvance() == 0)) {
			this.autospace.reset();
		}
		final TwoPass inlineBlockMeasure;
		if (quad instanceof InlineBlockQuad inlineBlockQuad && !inlineBlockQuad.box.isPreMeasured()) {
			// quadと子の計測tokenを対応付け、本文の所有をledgerへ登録する
			inlineBlockMeasure = this.pendingInlineMeasure;
			assert inlineBlockMeasure != null;
			final TwoPass measuredBuilder = this.pendingInlineMeasure.builder();
			this.pendingInlineMeasure = null;
			this.noteLayoutContent();
			if (measuredBuilder instanceof TwoPassBlockBuilder child) {
				this.ownershipLedger().addChild(child, OwnershipLedger.Kind.INLINE_BLOCK);
			} else {
				this.ownershipLedger().addPlan(measuredBuilder, OwnershipLedger.Kind.INLINE_TABLE);
			}
		} else {
			inlineBlockMeasure = null;
			this.noteLayoutContent();
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
		this.noteLayoutContent();
		this.measurer.endTextBlock();
	}

	public boolean isEmpty() {
		// seal済み(SourceRangeBody)は適格判定が空範囲を除外しているため
		// 常に非空(E-6増分4a)。空本文seal(Empty、DP増分2)は空
		return this.hasEmptyBody();
	}


}
