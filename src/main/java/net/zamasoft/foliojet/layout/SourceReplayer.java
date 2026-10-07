package net.zamasoft.foliojet.layout;

import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlexBox;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.segment.LayoutSourceEventConverter;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.layout.segment.FlexParamsTemplate;
import net.zamasoft.foliojet.layout.segment.GridParamsTemplate;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.layout.segment.SegmentExecutor;
import net.zamasoft.foliojet.layout.util.DebugFlags;

/**
 * A layout-source replay driver (M6b v3).
 *
 * <p>
 * Relays out closed subtrees moved wholly to the next page among page-break remainders,
 * using LayoutSource records without restyling. Drives a fresh DocumentBuilder toward
 * an existing root builder without touching the live StyleBuilder/DocumentBuilder state.
 * The doc protocol's symmetric pop→open→push sequence completes on a fresh unitizer,
 * structurally preventing v1's reentrancy crash. Boxes are reinstantiated from recorded
 * params/pos and therefore fully relaid out in the new page context (available width and floats).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class SourceReplayer {
	/** An exception-safe scope that prohibits Root translation throughout replay. */
	private static final class TranslateBlockScope implements AutoCloseable {
		private final RootBuilder root;

		TranslateBlockScope(final BlockBuilder target) {
			this.root = target instanceof RootBuilder r ? r : target.getPageContext();
			if (this.root != null) {
				this.root.enterTranslateBlockScope();
			}
		}

		@Override
		public void close() {
			if (this.root != null) {
				this.root.exitTranslateBlockScope();
			}
		}
	}

	/** Counts activations of source replay for closed subtrees (proof of migration coverage and diagnostics). */
	public static final AtomicLong SUBTREE_REPLAYS = new AtomicLong();

	/**
	 * Counts activations through absorbed replay ranges (C1c prefixItems).
	 * A subset of SUBTREE_REPLAYS, providing migration coverage that the path without box transport
	 * is actually exercised.
	 */
	public static final AtomicLong PREFIX_REPLAYS = new AtomicLong();

	/** Counts activations of source replay for column balancing (M6c). */
	public static final AtomicLong BALANCE_REPLAYS = new AtomicLong();

	/**
	 * Subtree ranges currently being replayed on this thread (2026-08-23).
	 *
	 * <p>
	 * If a replayed subtree triggers a page break before replay completes, a fresh box with the same
	 * SourceAnchor reattached appears in the continuation, and that range is stamped and replayed again.
	 * The resume position rewinds to the subtree's Start, recreating the same content on every page
	 * (v2 generator seed 30: a vertical-writing table with anonymous cells was duplicated over 34 pages).
	 * Rejects only reentry into the same range, returning to the caller's box-restyle fallback
	 * (replayFromSource==false).
	 * </p>
	 */
	private record ActiveReplay(LayoutSource source, long fromId, long toId) {
	}

	private static final ThreadLocal<java.util.ArrayDeque<ActiveReplay>> ACTIVE_REPLAYS = new ThreadLocal<>();

	private SourceReplayer() {
		// driver
	}

	/**
	 * Replays events from a captured range directly into doc (shared driver).
	 * The slice is a validated streaming view with a lease (E-6 increment 3a), so compaction
	 * from nested page breaks during replay cannot affect it; the lease protects unread ranges.
	 *
	 * <p>
	 * E-6 increment 3b-1 (2026-07-24): centralized execution (box construction, SourceAnchor
	 * reattachment, and replay with fresh Chars copies) in {@link SegmentExecutor}.
	 * E-6 increment 3b-6: removing the live type ({@code ReplacedLive}) also removed the transitional
	 * {@code executeLive} path. Converts {@code LayoutSource.Event} to {@link SegmentEvent}
	 * on the fly and drives everything through the single {@link SegmentExecutor#execute(SegmentEvent)}.
	 * If a range contains {@code Opaque}, conversion to Barrier followed by execute fails immediately;
	 * eligibility checking ({@code containsOpaque}) is the caller's contract.
	 * </p>
	 */
	private static void drive(final DocumentBuilder doc, final LayoutSource.ReplaySlice slice) {
		// Slice EventIds are consecutive from fromId (validated by capture).
		// Reattach SourceAnchor to replay instances from event IDs
		// (P0: anchors belong to individual boxes, providing the lineage
		// needed to make them replayable again at the next break).
		final SegmentExecutor executor = new SegmentExecutor(doc, slice.fromId());
		slice.replay(event -> executor.execute(LayoutSourceEventConverter.convert(event)));
	}

	/**
	 * Replays a log range into scratch pages and measures through actual layout (M2c).
	 * Creates and measures a fresh box tree without touching live state, so it can be called
	 * repeatedly with arbitrary dimensions.
	 *
	 * @param log      the source log
	 * @param fromId   the first EventId in the range
	 * @param toId     the last EventId in the range
	 * @param template computed parameters supplying fonts, etc.
	 * @param ua       the user agent
	 * @param width    the scratch page width (sufficiently large for max-content measurement)
	 * @param height   the scratch page height
	 * @param paginate whether to allow breaks (true for fit probes, false for size measurement)
	 * @return the generator holding measurement results (last page and page count)
	 */
	public static MeasurePageGenerator measure(final LayoutSource log, final long fromId, final long toId,
			final BlockParams template, final net.zamasoft.foliojet.ua.UserAgent ua, final double width,
			final double height, final boolean paginate) {
		try (var retained = ua.getRetainedTextLimit().measurement(RetainedTextLimit.elementName(template, "measure"));
				ReplayIntent.Scope replay = ReplayIntent.MEASURE.enter();
				ScratchReplayScope scratch = new ScratchReplayScope();
				ContinuationStats.TwoPassMeasurement measurement = ContinuationStats.twoPassMeasurement(ReplayIntent.MEASURE)) {
			return measureRange(log, fromId, toId, template, ua, width, height, paginate);
		}
	}

	private static MeasurePageGenerator measureRange(final LayoutSource log, final long fromId, final long toId,
			final BlockParams template, final net.zamasoft.foliojet.ua.UserAgent ua, final double width,
			final double height, final boolean paginate) {
		final MeasurePageGenerator pg = new MeasurePageGenerator(ua, template, width, height, log);
		final DocumentBuilder doc = new DocumentBuilder(pg);
		if (!paginate) {
			doc.setPageMode(DocumentBuilder.PAGE_MODE_NO_BREAK);
		}
		// Feeding a bare child range directly under a scratch page breaks when floats, etc.
		// try to anchor to the page box. Enclose it in a wrapper block corresponding
		// to the original block, making the anchoring context match normal construction.
		doc.startBox(createMeasureWrapper(template));
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			// Measurement has no fallback path (the caller must determine the range
			// while it is still live).
			throw new IllegalStateException("measure range is not intact: [" + fromId + ", " + toId + "]");
		}
		try (slice) {
			drive(doc, slice);
			doc.endBox();
			doc.end();
		}
		return pg;
	}

	/** Also restores the placement context corresponding to a Grid/Flex Start outside the child range. */
	private static FlowBlockBox createMeasureWrapper(final BlockParams template) {
		final BlockParams common = createMeasureWrapperParams(template);
		if (template instanceof GridParams grid) {
			final GridParams params = GridParamsTemplate.freeze(grid).materialize();
			BlockParamsTemplate.freeze(common).materializeInto(params);
			return new GridBox(params, new FlowPos());
		}
		if (template instanceof FlexParams flex) {
			final FlexParams params = FlexParamsTemplate.freeze(flex).materialize();
			BlockParamsTemplate.freeze(common).materializeInto(params);
			// Retain main-axis sizes needed for column eligibility and placement. Neutralize inline size and frame.
			params.size = net.zamasoft.foliojet.layout.box.params.Dimension.create(
					flex.flow.isVertical() ? flex.size.getWidth() : 0,
					flex.flow.isVertical() ? 0 : flex.size.getHeight(),
					flex.flow.isVertical() ? flex.size.getWidthType() : net.zamasoft.foliojet.layout.box.params.LengthType.AUTO,
					flex.flow.isVertical() ? net.zamasoft.foliojet.layout.box.params.LengthType.AUTO : flex.size.getHeightType());
			if (!flex.flexDirection.isRow() && flex.flexWrap.isWrap()) {
				// column wrap requires both axes to be definite. Do not lose the coordinator through neutralization.
				params.size = flex.size;
			}
			return new FlexBox(params, new FlowPos());
		}
		return new FlowBlockBox(common, new FlowPos());
	}

	/** Copies the original text context into the anonymous block wrapping scratch replay. */
	static BlockParams createMeasureWrapperParams(final BlockParams template) {
		final BlockParams wrapperParams = new BlockParams();
		wrapperParams.fontStyle = template.fontStyle;
		wrapperParams.fontManager = template.fontManager;
		wrapperParams.lineBreakRules = template.lineBreakRules;
		wrapperParams.flow = template.flow;
		wrapperParams.writingModeVariant = template.writingModeVariant;
		wrapperParams.direction = template.direction;
		wrapperParams.unicodeBidi = template.unicodeBidi;
		wrapperParams.bidiSemanticAlias = template.bidiSemanticAlias;
		// When bare text flows directly under the wrapper, its text state is reloaded from
		// the wrapper's params at line start (BuilderGlyphHandler). Without copying
		// the original block's text-layout parameters, autospace or letter-spacing
		// is lost, making actual measurement narrower than simulated measurement and causing wrong wraps
		// (kabutan "2,980.0円", 2026-08-08). Copy inherited fields that affect layout.
		wrapperParams.letterSpacing = template.letterSpacing;
		wrapperParams.wordSpacing = template.wordSpacing;
		wrapperParams.textTransform = template.textTransform;
		wrapperParams.whiteSpace = template.whiteSpace;
		wrapperParams.wordWrap = template.wordWrap;
		wrapperParams.textWrapStyle = template.textWrapStyle;
		// T5b (2026-09-06): also copy the strut convention; otherwise, atomic lines directly under the wrapper
		// measure at a different height from MAIN (codex review P2).
		wrapperParams.strictLineBox = template.strictLineBox;
		wrapperParams.tabSize = template.tabSize;
		wrapperParams.tabSizeIsMultiple = template.tabSizeIsMultiple;
		wrapperParams.hyphens = template.hyphens;
		wrapperParams.hyphenateCharacter = template.hyphenateCharacter;
		wrapperParams.hyphenator = template.hyphenator;
		wrapperParams.textAutospace = template.textAutospace;
		wrapperParams.textSpacingTrimOff = template.textSpacingTrimOff;
		wrapperParams.textSpacingTrimStart = template.textSpacingTrimStart;
		wrapperParams.textSpacingTrimEnd = template.textSpacingTrimEnd;
		wrapperParams.textSpacingSpaceFirst = template.textSpacingSpaceFirst;
		wrapperParams.rubyAlign = template.rubyAlign;
		wrapperParams.rubyMerge = template.rubyMerge;
		wrapperParams.rubyOverhang = template.rubyOverhang;
		wrapperParams.rubyPosition = template.rubyPosition;
		wrapperParams.warichu = template.warichu;
		wrapperParams.textCombine = template.textCombine;
		wrapperParams.hangingPunctuationEnd = template.hangingPunctuationEnd;
		wrapperParams.hangingPunctuationFirst = template.hangingPunctuationFirst;
		wrapperParams.hangingPunctuationForceEnd = template.hangingPunctuationForceEnd;
		wrapperParams.textAlign = template.textAlign;
		wrapperParams.textAlignLast = template.textAlignLast;
		wrapperParams.textIndent = template.textIndent;
		wrapperParams.lineHeight = template.lineHeight;
		wrapperParams.firstLineStyle = template.firstLineStyle;
		return wrapperParams;
	}

	/**
	 * Determines whether child-range replay ({@link #replayChildren}) is possible.
	 * Extracted on 2026-07-24, M6c-2 of exclusion area P2, so actual balancing probes
	 * can check once before iteration. Conditions are exactly the same as the former
	 * checks at the beginning of {@code replayChildren}.
	 *
	 * @param log    the source log
	 * @param selfId the EventId of the parent box's Start
	 * @param flow   the destination writing direction
	 * @return true if replay is possible
	 */
	public static boolean canReplayChildren(final LayoutSource log, final long selfId,
			final net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
		if (log == null || selfId < 0) {
			return false;
		}
		if (log.get(selfId) instanceof LayoutSource.Start start
				&& start.recipe() instanceof net.zamasoft.foliojet.layout.segment.BoxRecipe.PlacedTable) {
			// The placement host's children are table structure. Include TABLE Start to open the table builder.
			return false;
		}
		final long endId = log.endOf(selfId);
		if (endId < 0 || endId <= selfId + 1) {
			return false;
		}
		// Fall back because float/absolute anchoring, nested multi-column layout,
		// and mixed writing directions are unverified (before increment 4e, absolute positioning
		// was recorded as Opaque and caught by containsOpaque; separate the gate to preserve behavior).
		// Tables (table set, 2026-07-30): before recipe recording, they were recorded as Opaque
		// and caught by containsOpaque. Rebuilding entire tables during balancing replay,
		// including recalculating auto column widths, is unverified, so a table-specific gate
		// preserves previous behavior (the same form as MeasuredIntrinsics).
		// containsCaption (caption recipes C1): captions can appear in a table root's contents
		// (direct replay in restyleItem case TABLE). containsTable sees only nested tables,
		// so explicitly reject them here after recipe conversion (routing remains unchanged
		// until C2's context-complete validation permits them).
		return !(log.containsOpaque(selfId + 1, endId - 1) || log.observeCaptionGate(selfId + 1, endId - 1)
				|| log.containsTable(selfId + 1, endId - 1)
				|| log.containsFloat(selfId + 1, endId - 1) || log.containsAbsolute(selfId + 1, endId - 1)
				|| log.containsMulticol(selfId + 1, endId - 1) || log.containsMixedFlow(selfId + 1, endId - 1, flow));
	}

	/**
	 * Replays a closed block's child-event range into the specified builder
	 * (M6c: column balancing; multicol is a closed subtree at endFlowBlock, so its contents
	 * can be rebuilt from source into ColumnBuilder).
	 *
	 * @param log        the source log
	 * @param selfId     the EventId of the block's own StartBlock
	 * @param target     the destination builder (ColumnBuilder, etc.)
	 * @param pageGenerator the page generator
	 * @return true if replay succeeds (false for missing ranges or ranges containing Opaque)
	 */
	public static boolean replayChildren(final LayoutSource log, final long selfId, final BlockBuilder target,
			final PageGenerator pageGenerator) {
		if (!canReplayChildren(log, selfId, target.getRootBox().getBlockParams().flow)) {
			return false;
		}
		final long endId = log.endOf(selfId);
		if (endId >= pageGenerator.getDeliveredEventEnd()) return false;
		final LayoutSource.ReplaySlice slice = log.capture(selfId + 1, endId - 1);
		if (slice == null) {
			// Fall back to box replay if the range is incomplete.
			return false;
		}
		try (TranslateBlockScope scope = new TranslateBlockScope(target)) {
			final DocumentBuilder doc = new DocumentBuilder(pageGenerator, target);
			drive(doc, slice);
			doc.finishReplay();
		}
		BALANCE_REPLAYS.incrementAndGet();
		return true;
	}

	/** The existing replay entry point specifying a child range directly. The caller owns and closes the lease. */
	public static void bindTwoPassRange(final LayoutSource log, final long fromId, final long toId,
			final BlockBuilder target, final PageGenerator pageGenerator) {
		bindTwoPassRange(log, fromId, toId, target, pageGenerator, ReplayIntent.current());
	}

	/**
	 * For MEASURE, drives a fresh DocumentBuilder and releases temporary leases acquired during replay
	 * in finally. Does not own the lease of the caller's cell handle.
	 */
	public static void bindTwoPassRange(final LayoutSource log, final long fromId, final long toId,
			final BlockBuilder target, final PageGenerator pageGenerator, final ReplayIntent intent) {
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			throw new IllegalStateException(
					"seal済みTwoPass範囲が失われました(リースが守っているはずの範囲): [" + fromId + ", " + toId + "]");
		}
		bindTwoPassRange(slice, target, pageGenerator, intent);
	}

	/** Replays the body's leased view and the cell's immutable character slice with the same driver. */
	public static void bindTwoPassRange(final LayoutSource.ReplaySlice slice,
			final BlockBuilder target, final PageGenerator pageGenerator, final ReplayIntent intent) {
		// Trace float lifetimes with -Dfoliojet.debug.floatTrace=1 (added on 2026-08-03).
		// Used to diagnose content loss in nested floats
		// (files/fuzz-repro/nested-float-content-loss.html).
		// Read alongside acceptance (BlockBuilder), placement, and replay (Floatings) traces.
		if (DebugFlags.FLOAT_TRACE) {
			final StringBuilder where = new StringBuilder();
			final StackTraceElement[] st = new Throwable().getStackTrace();
			for (int k = 1; k < Math.min(st.length, 9); ++k) {
				where.append(' ').append(st[k].getMethodName()).append(':').append(st[k].getLineNumber());
			}
			System.err.println("[float] === 2パス再駆動 intent=" + intent + " 範囲=[" + slice.fromId() + "," + slice.toId() + "]"
					+ where);
		}
		try (slice;
				ReplayIntent.Scope replay = intent.enter();
				ScratchReplayScope scratch = ReplayIntent.current() == ReplayIntent.MEASURE ? new ScratchReplayScope() : null;
				TranslateBlockScope scope = new TranslateBlockScope(target);
				ContinuationStats.TwoPassMeasurement measurement = ContinuationStats.twoPassMeasurement(intent)) {
			final DocumentBuilder doc = new DocumentBuilder(pageGenerator, target, ReplayIntent.current());
			drive(doc, slice);
			doc.finishReplay();
		}
	}

	/**
	 * Replays the sequence of closed subtrees in [fromId, toId].
	 * The range must not contain Opaque (checked by the caller with containsOpaque).
	 *
	 * @param log           the source log
	 * @param fromId        the EventId of the first StartBlock
	 * @param toId          the EventId of the corresponding EndBlock
	 * @param rootBuilder   the destination root builder (current page context)
	 * @param pageGenerator the page generator
	 * @return true if replay occurs; false before execution if the range is incomplete
	 *         (caller contract: paths with a box fallback route false to it;
	 *         paths without one (C1c prefix) treat false as a failure)
	 */
	public static boolean replay(final LayoutSource log, final long fromId, final long toId,
			final BlockBuilder rootBuilder, final PageGenerator pageGenerator) {
		if (toId >= pageGenerator.getDeliveredEventEnd()) return false;
		java.util.ArrayDeque<ActiveReplay> active = ACTIVE_REPLAYS.get();
		if (active != null) {
			for (final ActiveReplay replay : active) {
				if (replay.source() == log && replay.fromId() == fromId && replay.toId() == toId) {
					// Reentry into a range currently being replayed (see the ACTIVE_REPLAYS comment).
					// The caller restyles/addBounds its retained box.
					return false;
				}
			}
		}
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			return false;
		}
		// Recheck the multi-column gate after capture (2026-07-27).
		// containsMulticol treats an unretained range as indeterminate and returns
		// true (immediately when indexOf<0), misclassifying a normal fallback
		// caused only by compaction losing the range as an invariant violation.
		// (Nested page breaks compact between stamping and consumption.
		// stampRanges already verified that the range was intact and had no multi-column layout.
		// The caller expects possible range loss: RootBuilder.replayFromSource
		// retains boxes and falls back to box-restyle.) After capture succeeds,
		// the range is guaranteed intact, so true here really means
		// it contains multi-column layout.
		assert !log.containsMulticol(fromId, toId) : "段組を含む範囲がソース再生されようとしました: [" + fromId + ", " + toId + "]";
		if (active == null) {
			active = new java.util.ArrayDeque<>();
			ACTIVE_REPLAYS.set(active);
		}
		final ActiveReplay replay = new ActiveReplay(log, fromId, toId);
		active.push(replay);
		try {
			try (TranslateBlockScope scope = new TranslateBlockScope(rootBuilder)) {
				final DocumentBuilder doc = new DocumentBuilder(pageGenerator, rootBuilder);
				drive(doc, slice);
				doc.finishReplay();
			}
			SUBTREE_REPLAYS.incrementAndGet();
			return true;
		} finally {
			assert active.peek() == replay;
			active.pop();
			if (active.isEmpty()) {
				ACTIVE_REPLAYS.remove();
			}
		}
	}
}
