package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.RetainedTextLimit;

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
	/** State during measurement and the replay source after finalization. No body is retained during measurement. */
	private sealed interface ReplayBody {
		record Measuring() implements ReplayBody { }

		/** Standalone replay without an anchor. Retains only expanded events, with no records or lease. */
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
		 * A body represented by a child event range [fromId, toId] in LayoutSource.
		 * Binds via {@code SourceReplayer.bindTwoPassRange} (driven by SegmentExecutor); the
		 * {@code RetentionLease} acquired at seal protects the range from compaction.
		 * RangeHandle accepts lease termination exactly once.
		 */
		record SourceRangeBody(RangeHandle handle,
				net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator) implements ReplayBody {
		}

		/**
		 * State after transferring a sealed body to {@link DeferredBind} (E-6 increment 4e).
		 * DeferredBind now owns the lease. A bind request to this builder violates the contract
		 * (no further bind goes through this builder; DeferredBind handles binding deferred absolutes).
		 */
		record Detached() implements ReplayBody {
		}

		/**
		 * An empty body. Accepts MAIN bind exactly once; no lease is needed. {@code ownPlan} is set when the root is
		 * an empty grid/flex container measured as its own root (2026-10-08): bind then lays the container out
		 * without items, so its explicit tracks ({@code grid-template-rows: 50px}) still give it its size.
		 */
		final class Empty implements ReplayBody {
			final net.zamasoft.foliojet.layout.builder.PageGenerator ownPlan;
			boolean consumed;

			Empty() {
				this(null);
			}

			Empty(final net.zamasoft.foliojet.layout.builder.PageGenerator ownPlan) {
				this.ownPlan = ownPlan;
			}
		}

		/**
		 * State after subsumption into the parent's range (DP increment 3, 2026-07-30;
		 * codex consultation consult-codex-2026-07-30-dualpath-endgame.txt,
		 * NESTED_BUILDER elimination). The parent's {@code SourceRangeBody} includes the child range,
		 * and parent range replay (SegmentExecutor) reconstructs the child content during bind.
		 * A bind request to this builder violates the contract (treated like {@link Detached}).
		 * The child's lease was released on subsumption (the parent lease was acquired first,
		 * so the compaction watermark does not move backward).
		 */
		record Subsumed() implements ReplayBody {
		}
	}


	/**
	 * Transferable form of a sealed body and intrinsic sizes. Absolute positioning, table cells,
	 * and Grid/Flex items use it to replay without retaining the measurement builder.
	 * It can also carry an empty body or standalone replay.
	 *
	 * <p>sizes is a snapshot of IntrinsicMeasurer. The range lease is released exactly once,
	 * on MAIN bind, subsumption into the parent, or disposal at document end.
	 * Scratch measurement does not consume the original body.</p>
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
			// The handle is authoritative for a range body. Do not retain a second SourceRangeBody
			// and sizes snapshot for every cell after transfer.
			this.body = this.handle == null ? body : null;
			this.sizes = this.handle == null ? sizes : this.handle.sizes();
			this.pageGenerator = body instanceof ReplayBody.SourceRangeBody range ? range.pageGenerator() : null;
			this.censusTag = censusTag;
			this.ownedAbsoluteAnchors = java.util.Set.copyOf(ownedAbsoluteAnchors);
		}

		/** Intrinsic sizes (a snapshot of simulated measurement; see the class javadoc). */
		public IntrinsicSizes sizes() {
			return this.sizes;
		}

		/** Page context for bind (the first argument to {@code new BlockBuilder(pageContext, box)}). */
		public RootBuilder pageContext() {
			return this.pageContext;
		}

		/**
		 * Redrives the sealed range into {@code builder}
		 * (equivalent to the SourceRangeBody arm of {@link TwoPassBlockBuilder#bind};
		 * releases the lease on both success and failure).
		 */
		public void bind(final BlockBuilder builder) {
			if (ReplayIntent.current() == ReplayIntent.MEASURE) {
				this.measureInto(builder);
				if (this.handle != null) this.handle.completeScratchHost();
				return;
			}
			if (this.pageContext != null) {
				this.pageContext.enterTranslateBlockScope();
			}
			final RetainedTextLimit limit = RetainedTextLimit.get(builder);
			try (var retained = limit == null ? null
					: limit.enter(RetainedTextLimit.elementName(builder.getRootBox().getParams(), "two-pass"))) {
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

		/** Ownership handle for a range body. null for an empty body or standalone replay. */
		public RangeHandle handle() {
			return this.handle;
		}

		/**
		 * Redrives the sealed range into {@code builder} for table Pass B (row measurement)
		 * (E-6 increment 5b-1, 2026-07-24; codex design §4.4). Uses the same SegmentExecutor as
		 * {@link #bind}, but <b>does not release the lease</b> (the subsequent actual bind captures
		 * the same range again; capture is a nondestructive read that acquires and releases its
		 * slice's own lease each time). Does not count statistics (TWO_PASS_RANGE_BINDS) either
		 * (to avoid distorting the seal:bind 1:1 validation).
		 */
		public void measureInto(final BlockBuilder builder) {
			final RetainedTextLimit limit = RetainedTextLimit.get(builder);
			try (var retained = limit == null || ReplayIntent.current() == ReplayIntent.MEASURE ? null
					: limit.measurement(RetainedTextLimit.elementName(builder.getRootBox().getParams(), "measure"));
					ContinuationStats.TwoPassMeasurement measurement =
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
		 * Returns whether this sealed body is contained in [from, to] on {@code log}
		 * (table subsumption = codex increment 5, 2026-07-30; used by the validation phase
		 * of parent range conversion, with no side effects).
		 */
		boolean within(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long from, final long to) {
			return this.handle != null && this.handle.state() == RangeHandle.State.OPEN && this.handle.source() == log
					&& !this.handle.hasTextSlice()
					&& this.handle.fromId() >= from && this.handle.toId() <= to;
		}

		/** After checking containment, passes ownership proof validated at cell seal to the parent's exact matching. */
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
		 * Subsumes into the parent's range (commit phase of table subsumption = codex increment 5).
		 * Transitions the handle to SUBSUMED and records the SUBSUMED side of seal:bind accounting
		 * (the same handle also records cell-specific accounting).
		 * The parent lease must already be acquired at the time of the call
		 * (the ordering contract for the compaction watermark).
		 */
		void abandonForParentRange() {
			if (this.handle != null) this.handle.subsume();
			else if (this.body instanceof ReplayBody.Empty empty) empty.consumed = true;
		}

		boolean isEmpty() { return this.body instanceof ReplayBody.Empty; }
	}

	protected final LayoutStack layoutStack;

	/** Intrinsic size measurer. Receives body input and computes intrinsic sizes only. */
	private final IntrinsicMeasurer measurer = new IntrinsicMeasurer(this);

	private TextImpl text;

	private final List<AbstractContainerBox> flowStack = new ArrayList<AbstractContainerBox>();

	/** Replay source for bind(). Retains no body during measurement; finalizes at close. */
	private ReplayBody body = new ReplayBody.Measuring();

	/** Avoid allocating an empty ledger for each cell until a child or plan ownership node exists. */
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

	/** Also notifies the ledger of body ownership transitions. The ledger decides whether children can be subsumed. */
	private void setBody(final ReplayBody body) {
		this.body = body;
		if (!(body instanceof ReplayBody.Measuring) && !(body instanceof ReplayBody.ReplayOnly)) {
			// Do not retain the measuring run or the preceding pair reference from the finalized body either.
			this.text = null;
			this.autospace = null;
		}
		if (this.ownershipLedger != null) this.ownershipLedger.bodyChanged(this.bodyState(), this.rangeHandle());
	}

	/**
	 * Measurement token for the most recent inline-block. Reads intrinsic sizes when the quad
	 * arrives after child recording ends. Independent of body replay.
	 */
	private record InlineMeasureToken(TwoPass builder) implements TwoPass {
		@Override
		public IntrinsicSizes getIntrinsicSizes() {
			return this.builder.getIntrinsicSizes();
		}
	}

	// Association until the quad arrives. A measurement token independent of the body replay source.
	private InlineMeasureToken pendingInlineMeasure;

	private boolean hasLayoutContent;

	/**
	 * Whether anything besides this root's own Grid/Flex execution plan reached the measurer (2026-10-08). An empty
	 * grid or flex container with an intrinsic width ({@code width: fit-content}) is the root of its own measurement:
	 * its plan is the only content, and its source range between Start and End is empty.
	 */
	private boolean hasContentBesidesOwnPlan;

	// Ownership proof that passed exact matching at seal. Also passes from normal child ranges to their parent.
	private java.util.Set<Long> rangeOwnedAbsoluteAnchors = java.util.Set.of();

	private final ContinuationStats.TwoPassCensusTag censusTag;

	/** Root classification for the range census. Does not affect body retention. */
	public void tagRootKind(final ContinuationStats.TwoPassRootKind kind) {
		if (this.censusTag != null) this.censusTag.rootKind(kind);
	}

	public TwoPassBlockBuilder(LayoutStack layoutStack, AbstractContainerBox containerBox) {
		this.layoutStack = layoutStack;
		this.censusTag = ContinuationStats.newTwoPassCensusTag();
		this.flowStack.add(containerBox);
		this.measurer.start(containerBox);
		// E-6 increment 1 (2026-07-24): observe the nesting depth high-water mark
		// (read-only, with no effect on behavior). Count consecutive TwoPassBlockBuilders
		// along the layoutStack chain (nesting through table cells appears naturally here
		// because RetainedTableBuilder inherits its parent's layoutStack).
		int depth = 1;
		for (LayoutStack stack = layoutStack; stack instanceof TwoPassBlockBuilder parent; stack = parent.layoutStack) {
			++depth;
		}
		TableBuildStats.reportTwoPassNestDepth(depth);
	}

	/**
	 * Starts recording standalone replay. Intrinsic size measurement is needed, but the input
	 * is already expanded, so glyph sequences and live box records need not be retained again.
	 */
	public void startReplayOnly(final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator) {
		if (!(this.body instanceof ReplayBody.Measuring) || this.hasLayoutContent) {
			throw new IllegalStateException("独立再生は本文の給餌前に開始します");
		}
		this.setBody(new ReplayBody.ReplayOnly(pageGenerator));
	}

	/** Retains standalone events in the same order as DocumentBuilder's boundary decisions. */
	public void recordReplayOnlyEvent(final SegmentEvent event, final long ordinal) {
		if (this.body instanceof ReplayBody.ReplayOnly replay) {
			if (replay.closed) {
				throw new IllegalStateException("終了済み独立再生への追記");
			}
			replay.events.add(event);
			replay.lastOrdinal = ordinal;
		}
	}

	/** Finalizes body events, excluding its own End or the start of the next sibling. */
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

	/** Records that characters, controls, and boxes after folding have reached the measurer. */
	private void noteLayoutContent() {
		if (!(this.body instanceof ReplayBody.Measuring)
				&& !(this.body instanceof ReplayBody.ReplayOnly)) {
			throw this.invariant("給餌終了後のレイアウト内容");
		}
		this.hasLayoutContent = true;
		this.hasContentBesidesOwnPlan = true;
	}

	/** Records a Grid/Flex execution plan: content like any other, unless it is this root's own. */
	private void notePlanContent(final IBox planBox) {
		if (planBox != this.getRootBox()) {
			this.noteLayoutContent();
			return;
		}
		if (!(this.body instanceof ReplayBody.Measuring) && !(this.body instanceof ReplayBody.ReplayOnly)) {
			throw this.invariant("給餌終了後のレイアウト内容");
		}
		this.hasLayoutContent = true;
	}

	public AbstractContainerBox getFixedWidthContextBox() {
		AbstractContainerBox box = this.getContextBox();
		if (box.getBlockParams().size.getWidthType() != LengthType.AUTO) {
			return box;
		}
		// Absolute box with page-axis size set by both end positions (2026-10-04). Use as the basis for inner % sizes.
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox absolute
				&& absolute.getBlockParams().flow.isVertical() && absolute.isPageAxisDefinite()) {
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
		// Absolute box with page-axis size set by both end positions (2026-10-04). Use as the basis for inner % sizes.
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox absolute
				&& !absolute.getBlockParams().flow.isVertical() && absolute.isPageAxisDefinite()) {
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
	 * Obtains intrinsic sizes by actual layout measurement (M2c), falling back to the old two-pass
	 * simulated measurement if the range cannot be identified. All shrinkToFit consumers must use
	 * this instead of getIntrinsicSizes() (simulation only).
	 */
	public IntrinsicSizes intrinsicSizesMeasured() {
		final net.zamasoft.foliojet.layout.builder.impl.RootBuilder root = this.layoutStack == null ? null
				: this.getPageContext();
		if (root != null) {
			final AbstractContainerBox rootBox = (AbstractContainerBox) this.getRootBox();
			if (rootBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox) {
				// A flex container sized by its own intrinsic keyword (width: min-content, fit-content, max-content;
				// 2026-10-10) takes the flex intrinsic sizes (Flexbox §9.9, FlexBuilder.getIntrinsicSizes through
				// measurer.flex), as a nested flex container does. Laid out on the scratch page at line width 0, its
				// items shrank to what bind lets them, not to their min-content contributions: a row of an item with
				// flex: 0 0 200px came out 200px wide where Chrome makes its min-content 60px. Its own sizes are left
				// out, which its shrink-to-fit applies.
				return this.measurer.sizesBeforeRoot();
			}
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

	/**
	 * Whether there is an orthogonal child (e.g., a table/block in horizontal writing within vertical writing)
	 * (2026-10-05). If so, simulated line-axis measurement does not know the child's actual size,
	 * so shrink-to-fit lays it out once and measures again ({@code DocumentBuilder}).
	 */
	public boolean hasOrthogonalContent() {
		return this.measurer.hasOrthogonalContent();
	}

	/** Intrinsic sizes from simulated measurement, excluding contributions from orthogonal children. */
	public IntrinsicSizes intrinsicSizesWithoutOrthogonal() {
		return this.measurer.sizesWithoutOrthogonal();
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

	/**
	 * The flow holding a box on this builder's stack: the one under it, the parent's current flow for the root, or the
	 * current flow when the box is not on the stack (2026-10-10, IntrinsicMeasurer).
	 */
	AbstractContainerBox flowHolding(final AbstractContainerBox box) {
		for (int i = this.flowStack.size() - 1; i >= 0; --i) {
			if (this.flowStack.get(i) == box) {
				return i > 0 ? this.flowStack.get(i - 1) : this.layoutStack == null ? null : this.layoutStack.getFlowBox();
			}
		}
		return this.getFlowBox();
	}

	public AbstractContainerBox getFlowBox() {
		return (AbstractContainerBox) this.flowStack.get(this.flowStack.size() - 1);
	}

	/** The number of open flows, the root included (a box directly under the root is at depth 2). */
	public int getFlowDepth() {
		return this.flowStack.size();
	}

	public void startFlowBlock(final FlowBlockBox flowBox) {
		// A block box in normal flow.
		AbstractContainerBox containerBox = this.getFlowBox();
		// firstPassLayout does not read measurement state (float advance), so swapping its order
		// with clearFloatAdvance (on the measurer side) is equivalent.
		flowBox.firstPassLayout(containerBox);
		this.measurer.startFlow(flowBox, containerBox);

		this.flowStack.add(flowBox);
		this.noteLayoutContent();
	}

	public void endFlowBlock() {
		// A block box in normal flow.
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
		this.measurer.table(tableSizes, autoTableBuilder.getTableBox().getBlockBox().getBlockParams().flow
				.isVertical() != this.getFlowBox().getBlockParams().flow.isVertical());
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
		// In the host, register the execution plan in the ledger and pass Grid's intrinsic content-box
		// sizes to the measurer (the normal startFlowBlock -> measurer.startFlow path adds GridBox's
		// frame exactly once; preventing double counting is recommendation Q5).
		// Grid always uses FLOW positioning, so no inline-block measurement token is needed.
		this.measurer.grid(gridBuilder.getIntrinsicSizes(), gridBuilder.getGridBox());
		this.notePlanContent(gridBuilder.getGridBox());
		this.ownershipLedger().addPlan(gridBuilder, OwnershipLedger.Kind.GRID);
	}

	public void addFlex(final net.zamasoft.foliojet.layout.builder.RetainedFlex flexBuilder) {
		// Flex F1f (equivalent to addGrid): register the execution plan in the ledger and pass Flex's
		// intrinsic content-box sizes to the measurer (the normal path adds the frame exactly once).
		this.measurer.flex(flexBuilder.getIntrinsicSizes(), flexBuilder.getFlexBox());
		this.notePlanContent(flexBuilder.getFlexBox());
		this.ownershipLedger().addPlan(flexBuilder, OwnershipLedger.Kind.FLEX);
	}

	public Builder newBuilder(final AbstractBlockBox stfBox) {
		// * TODO BoundContainerContext can be used for an absolute width,
		// * but absolute positioning must be adjusted after construction,
		// * so leave this as is.
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this, stfBox);
		builder.tagRootKind(
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind.NESTED);
		final AbstractContainerBox box = this.getFlowBox();
		stfBox.firstPassLayout(box);
		switch (stfBox.getPos().getType()) {
		case FLOW:
			// Different writing directions.
		case FLOAT:
			// Float.
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
			// Absolute positioning.
			this.noteLayoutContent();
			this.ownershipLedger().addChild(builder, OwnershipLedger.Kind.ABSOLUTE);
			break;

		case INLINE:
			// Inline block.
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

	/** Converts the intrinsic sizes of a nested shrink-to-fit block to the parent's axes. */
	public void fitBlock(final TwoPassBlockBuilder childBuilder) {
		this.measurer.fitBlock(childBuilder);
	}

	/** Finalizes the body range at close. Ineligibility fails the conversion. */
	public void sealBodyForRangeBind() {
		this.sealBodyForRangeBind(this.getRootBox().getSourceAnchor(), RangeHandle.ReplayMode.CHILDREN_ONLY);
	}

	/** Only cell close in an immediately placed table allows a text body to be extracted. */
	void sealCellBodyForRangeBind(final boolean sliceText) {
		this.sealBodyForRangeBind(this.getRootBox().getSourceAnchor(), RangeHandle.ReplayMode.CHILDREN_ONLY, sliceText);
	}

	/** Called from item close. anchor is the authored child, or the synthetic Start for an anonymous item. */
	void sealBodyForRangeBind(final long anchor, final RangeHandle.ReplayMode mode) {
		this.sealBodyForRangeBind(anchor, mode, false);
	}

	private void sealBodyForRangeBind(final long anchor, final RangeHandle.ReplayMode mode, final boolean sliceText) {
		if (!(this.body instanceof ReplayBody.Measuring)) {
			return; // Idempotent.
		}
		this.sealAnchor = anchor;
		if (this.layoutStack == null) {
			this.reject(ContinuationStats.TwoPassSealReject.NO_SOURCE);
		}
		final RootBuilder root = this.getPageContext();
		if (root == null) {
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_SOURCE);
			return;
		}
		final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator = root.getPageGenerator();
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = pageGenerator.getLayoutSource();
		if (log == null) {
			// A context without a log, such as scratch measurement (MeasurePageGenerator).
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.NO_SOURCE);
			return;
		}
		// Opaque record types (tables/table captions) have endOf=-1, making them structurally
		// ineligible here (fail closed). Absolute positioning gained endOf lookup through
		// recipe recording in E-6 increment 4e (resolved NO_RANGE=81).
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
			// An empty grid/flex container measured as its own root (width: fit-content) has only its own plan as
			// content: nothing to replay either (2026-10-08; it failed the conversion with NO_RANGE). The plan has
			// no items: the measured sizes give the box its width, and bind lays the plan out again without items.
			if (!this.hasContentBesidesOwnPlan) {
				// If both the source and measured content are empty, use a terminal state with no body.
				this.setBody(new ReplayBody.Empty(this.hasLayoutContent ? pageGenerator : null));
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
			// containsCaption (caption recipe conversion C1): captions moved from Opaque to recipe
			// records, but until C2's context-complete validation, reject the same ranges for
			// the same reason (OPAQUE_RANGE); routing is unchanged.
			// This branch takes over the old comment's rule: reject tables with captions here
			// because they use Opaque records.
			// containsOpaque is also true if the start is missing. Only actual Opaque corresponds to
			// the converter's NOT_YET_SUPPORTED. The caption gate itself is not a Barrier.
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.OPAQUE_RANGE,
					this.censusTag != null && opaque && log.get(fromId) != null
							? BarrierReason.NOT_YET_SUPPORTED : null);
			return;
		}
		// The validation phase scans only the ledger. Release children after acquiring the parent lease.
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
			// Absolute subsumption (codex increment 9): some Absolute Starts in the range still lack
			// ownership proof in the ownership ledger (owned by an outer context or a different
			// execution plan, etc.). Fail closed.
			reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.ABSOLUTE_RANGE);
			return;
		}
		// Final validation of range completeness (consecutive IDs, no gaps). Release the probe lease immediately.
		try (net.zamasoft.foliojet.layout.fragment.LayoutSource.ReplaySlice probe = log.capture(fromId, toId)) {
			if (probe == null) {
				reject(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.RANGE_NOT_INTACT);
				return;
			}
		}
		// Seal (commit phase): subsume children (release leases + mark Subsumed) after acquiring the parent lease.
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
			// Table subsumption (codex increment 5): release sealed cells' leases and abandon the plan.
			// Parent range replay reconstructs the entire table from the source.
			table.abandonForParentRange();
		}
		if (this.ownershipLedger != null) this.ownershipLedger.plansSubsumed();
	}

	/**
	 * Validates whether one recorded Retained table plan can be subsumed (table subsumption = codex
	 * increment 5, validation phase, no side effects). A table and an inline measurement token may
	 * share the same plan by identity, so skip duplicates in outTables idempotently.
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
			// Own a positioned table exclusively as a table plan.
			// Verify the inner/outer objects point to the same Start and are unanchored; pass to final exact matching.
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

	/** Uses the same ledger to decide whether subsumption from a table or item is allowed. */
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
	 * Subsumes into the parent's range (commit phase of DP increment 3). The parent lease must
	 * already be acquired at the time of the call (the ordering contract prevents releasing
	 * a child lease from moving the compaction watermark backward). Lease close is idempotent
	 * and non-throwing.
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

	/** Determines an empty body from measured content. Does not authorize binding an unsealed body. */
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

	/** Whether content was fed after folding. Anonymous item disposal does not depend on records. */
	boolean hasLayoutContent() {
		return this.hasLayoutContent;
	}

	/** Transfers the finalized body. Empty bodies and standalone replay also do not retain the builder. */
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

	/** Makes the body reclaimable for a host placed in B or discarded as outside flow. */
	public void completeScratchHost() {
		if (this.body instanceof ReplayBody.SourceRangeBody range) range.handle().completeScratchHost();
	}

	/**
	 * Replays the recorded body into {@code builder}.
	 *
	 * <p>
	 * <b>{@code intent}=MEASURE replays during disposable measurement</b> (introduced on 2026-08-03).
	 * It <b>does not release</b> the usage right (lease) or count statistics, because the actual
	 * bind reads the same range again.
	 * </p>
	 *
	 * <p>
	 * Without this, content was lost: <b>disposable measurement consumed the body, leaving it empty
	 * for actual layout</b>. Table row measurement replayed the recorded range with the intent to
	 * discard the result, but a nested float's body was bound as "actual layout" during that replay,
	 * closing the usage right. Reproduction: {@code files/fuzz-repro/nested-float-content-loss.html}
	 * (the inner float's text disappears when a narrow box, a table, right alignment, and left
	 * alignment all occur together). The same problem for absolute positioning was fixed differently
	 * on 2026-07-30 (skip it entirely during scratch measurement), but floats contribute to measured
	 * sizes and cannot be skipped. This is why nondestructive replay is needed.
	 * </p>
	 */
	public void bind(final BlockBuilder builder, final ReplayIntent intent) {
		final RootBuilder root = builder.getPageContext();
		if (root != null) {
			root.enterTranslateBlockScope();
		}
		final RetainedTextLimit limit = RetainedTextLimit.get(builder);
		try (var replica = limit == null || intent != ReplayIntent.MEASURE
				|| ReplayIntent.current() == ReplayIntent.MEASURE ? null
				: limit.measurement(RetainedTextLimit.elementName(this.getRootBox().getParams(), "measure"));
				var retained = limit == null ? null
				: limit.enter(RetainedTextLimit.elementName(this.getRootBox().getParams(), "two-pass"));
				ReplayIntent.Scope replay = intent.enter();
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
				// E-6 increment 4e: already transferred to DeferredBind. DeferredBind handles bind.
				throw new IllegalStateException("DeferredBindへ持ち出し済みのビルダーへのbind");
			case ReplayBody.Subsumed subsumed ->
				// DP increment 3: parent range replay reconstructs the content. An individual bind violates the contract.
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
		// Restore the temporary scratch connection in bind before signaling the end of the caller-owned body.
		// Unlike measureInto, bind is this host's final placement. Do not signal a MAIN borrow.
		if (intent == ReplayIntent.MEASURE) this.completeScratchHost();
	}

	/** Common execution for bodies without leases. Unreachable when a normal source is ineligible. */
	private static void bindWithoutRange(final ReplayBody body, final BlockBuilder builder,
			final ContinuationStats.TwoPassCensusTag censusTag) {
		switch (body) {
		case ReplayBody.Empty empty -> {
			if (empty.consumed) throw new IllegalStateException("空本文の再bind");
			if (empty.ownPlan != null) {
				// The root's own grid/flex without items, as a range replay with no events lays it out.
				final DocumentBuilder doc = new DocumentBuilder(empty.ownPlan, builder, ReplayIntent.current());
				doc.finishReplay();
			}
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

	/** Japanese spacing A2: pair tracking for text-autospace (lazily initialized on the first glyph). */
	private net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker autospace;

	public void startTextRun(int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.text = new TextImpl(charOffset, fontStyle, fontMetrics);
		this.lastRunFontStyle = fontStyle;
		this.lastRunFontMetrics = fontMetrics;
	}

	/** Font of the most recent run (for lazy resumption when a glyph arrives after the run closes). */
	private FontStyle lastRunFontStyle;
	private FontMetrics lastRunFontMetrics;

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		// Apply gap/trim only to intrinsic sizes. TextBuilder measures again during range replay.
		// Centralize the width formula in IntrinsicMeasurer.glyph.
		if (this.autospace == null) {
			this.autospace = new net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker();
			final net.zamasoft.foliojet.layout.box.params.AbstractTextParams params = //
					(net.zamasoft.foliojet.layout.box.params.AbstractTextParams) this.getRootBox().getParams();
			this.autospace.setFlags(params.textAutospace);
			this.autospace.setTrimOff(params.textSpacingTrimOff);
		}
		if (this.text == null) {
			// A glyph arrives after the run closes (observed in ::before/::after generated content
			// inside a table caption, 2026-09-05: pending glyphs flush after CharacterHandler calls endRun).
			// As in BlockBuilder(:1927), lazily resume the run with the most recent font.
			if (this.lastRunFontStyle == null) {
				throw new IllegalStateException("glyph before any text run");
			}
			this.startTextRun(charOffset, this.lastRunFontStyle, this.lastRunFontMetrics);
		}
		final double fontSize = this.text.getFontStyle().getSize();
		final double gap = this.autospace.gapBefore(ch, coff, fontSize);
		final double trim = this.autospace.trimBefore(ch, coff, gid, this.text,
				this.text.getFontMetrics(), fontSize, this.text.getFontStyle());
		// appendGlyph returns the advance for spacing measurement within a run,
		// so call it only once and pass the result to the measurer.
		this.measurer.glyph(this.text.appendGlyph(ch, coff, clen, gid), gap, trim);
		this.autospace.glyphAdded(this.text, fontSize, ch, coff, clen, gid);
		this.noteLayoutContent();
	}

	public void endTextRun() {
		// Use only for spacing measurement within a run; do not retain as body content.
		this.text = null;
	}

	public void control(final TextControl quad) {
		// Japanese spacing A2: controls break pairs (same rules as TextBuilder;
		// only zero-width inline starts/ends preserve pairs).
		if (this.autospace != null && !(quad instanceof InlineQuad inlineQuad
				&& (inlineQuad.getType() == InlineQuad.INLINE_START
						|| inlineQuad.getType() == InlineQuad.INLINE_END)
				&& inlineQuad.getAdvance() == 0)) {
			this.autospace.reset();
		}
		final TwoPass inlineBlockMeasure;
		if (quad instanceof InlineBlockQuad inlineBlockQuad && !inlineBlockQuad.box.isPreMeasured()) {
			// Associate the quad with the child's measurement token and register body ownership in the ledger.
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
		// Sealed (SourceRangeBody) is always nonempty because eligibility excludes empty ranges
		// (E-6 increment 4a). An empty body seal (Empty, DP increment 2) is empty.
		return this.hasEmptyBody();
	}


}
