package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.segment.BarrierReason;

/**
 * Counters by continuation type (transport across page breaks; P4: quantitative basis for reducing
 * OpenTailShape, using the same firing-counter convention as TableBuildStats).
 * These provide test observations and do not affect behavior.
 */
public final class ContinuationStats {
	/** Consumption by a chain child frame (Child). */
	public static final AtomicLong CHILD_FRAMES = new AtomicLong();

	/**
	 * Number of calls to {@code ColumnsContainer.splitPageAxis()} (added 2026-07-21,
	 * M6b Phase B5d-0). The denominator for {@link #COLUMNS_LAST_COLUMN_MOVE_CANDIDATE}.
	 *
	 * <p>
	 * <b>Retirement condition (2026-07-24 E-5)</b>:
	 * Retire together with {@link #COLUMNS_LAST_COLUMN_MOVE_CANDIDATE}
	 * (it is meaningful only as a denominator; do not retain it alone).
	 * </p>
	 */
	public static final AtomicLong COLUMNS_SPLIT_ATTEMPTS = new AtomicLong();

	/**
	 * Number of times, with at least two columns in {@code ColumnsContainer}, the split result of
	 * the delegated last column (`getLastColumn()`) was "all content of that column moved to the next
	 * fragmentainer" (three-argument version: the return value is identical to the last column itself;
	 * four-argument version: the container in {@code ContainerCut.Plain} is identical to the last
	 * column itself). Added 2026-07-21, M6b Phase B5d-0. This is a superset of "MOVE of the entire
	 * multi-column layout" (it also includes the normal case where only the last column MOVEs and
	 * preceding columns stay). It has no effect on behavior (counter increment only).
	 *
	 * <p>
	 * <b>Retirement condition (2026-07-24 E-5)</b>: The original purpose (deciding whether to implement
	 * B5d proper) was closed on 2026-07-22 (development record
	 * -implementation-needed.md: zero observed cases, and the existing {@code remainder ==
	 * activeColumn} check was confirmed to handle it correctly). It remains to observe the current
	 * behavior in which {@code ContainerCut.Plain} sentinels (null/this) are interpreted by different
	 * identity comparisons at each layer (documented in E-4; see the {@code ContainerCut.Plain}
	 * Javadoc). Once Plain sentinels are typed as {@code Keep}/{@code Move} (together with removal of
	 * the legacy three-argument {@code Container
	 * .splitPageAxis} contract), it can be retired together with
	 * {@link #COLUMNS_SPLIT_ATTEMPTS} and the referencing assertions
	 * ({@code ResumeTraceGoldenTest}).
	 * </p>
	 */
	public static final AtomicLong COLUMNS_LAST_COLUMN_MOVE_CANDIDATE = new AtomicLong();

	/**
	 * Records the configured column count (equivalent to CSS {@code column-count}) of the owner
	 * (box targeted by the column break) selected for the latest column break (COLUMN).
	 * Added 2026-07-21 for test observation of nested multicol owner selection. The existing behavior
	 * where {@code BreakableBuilder.findColumnBreak()} selects the innermost owner with
	 * {@code canColumnBreak()} is unchanged; this instrumentation simply lets tests observe
	 * "which owner was actually selected."
	 */
	public static final java.util.concurrent.atomic.AtomicInteger LAST_COLUMN_OWNER_COLUMN_COUNT = new java.util.concurrent.atomic.AtomicInteger(
			-1);

	/** Consumption of OpenTailShape at the chain end (prefix already absorbed). */
	public static final AtomicLong OPEN_TAILS = new AtomicLong();

	/** Restyle of all boxes for an uncollectible break (no chain). */
	public static final AtomicLong UNCHAINED_RESTYLES = new AtomicLong();

	/**
	 * Handoff of an open paragraph (via slice transport in M3b Phase 1).
	 * Measures the cases to migrate to typed TextTail items in Phase 2/3.
	 */
	public static final AtomicLong OPEN_TEXT_HANDOFFS = new AtomicLong();

	/**
	 * Maximum OpenTailShape depth for PAGE (via RootBuilder.pageBreak)
	 * (0 = no open boxes, 1 = open text only, 2+ = moved-open nesting).
	 * The old {@code MAX_OPEN_TAIL_DEPTH} conflated PAGE/COLUMN, so COLUMN was separated on
	 * 2026-07-21 (identified in a ChatGPT Pro consultation: {@code BreakableBuilder.columnBreak()}
	 * uses a separate path that never passes through the PAGE depth guard; conflating them
	 * hides anomalies in either path).
	 */
	public static final AtomicLong MAX_PAGE_OPEN_TAIL_DEPTH = new AtomicLong();

	/**
	 * Maximum OpenTailShape depth for COLUMN (via BreakableBuilder.columnBreak,
	 * a column break within multi-column layout). Added 2026-07-21.
	 */
	public static final AtomicLong MAX_COLUMN_OPEN_TAIL_DEPTH = new AtomicLong();

	/**
	 * Increments by one each time {@code FlowContainer.restyle} descends one level of an open
	 * ancestor chain (moved-open) by box replay (restyle-chain). Added 2026-07-20,
	 * M6b Phase B: observation infrastructure B0 for converting the "split block chain" to source replay.
	 * This value should approach zero as source replay progresses. This firing counter measures the
	 * initial state and the reduction in box-restyle dependence at each stage
	 * (see design consultation*.md).
	 */
	public static final AtomicLong RESTYLE_CHAIN_FIRINGS = new AtomicLong();

	/** {@link #RESTYLE_CHAIN_FIRINGS} on the PAGE path (added 2026-07-21, B1). */
	public static final AtomicLong PAGE_RESTYLE_CHAIN_FIRINGS = new AtomicLong();

	/** {@link #RESTYLE_CHAIN_FIRINGS} on the COLUMN path (added 2026-07-21, B1). */
	public static final AtomicLong COLUMN_RESTYLE_CHAIN_FIRINGS = new AtomicLong();

	/**
	 * Number of times the worklist executor's {@code descendWorklist} cannot represent the
	 * box/container combination at an OpenChain descent target as a frame/scope and falls back to
	 * the polymorphic {@code containerBox.restyle(builder, inner)} compatibility path.
	 * Added 2026-07-30, increment 0. Renamed from {@code WORKLIST_RECURSIVE_FALLBACKS} in increment 4f:
	 * after removal of the old driver, this means "compatibility path for an unknown type," rather
	 * than "fallback to the recursive driver." Tests require zero for all known types
	 * (FlowContainer/ColumnsContainer under FlowBlockBox) and detect any future new container type
	 * silently entering this path (a WARNING is also logged on the first occurrence).
	 *
	 * <p>
	 * The old {@code LEGACY_RECURSIVE_DESCENTS} (firing count of the old recursive driver) was deleted
	 * in increment 4f because its producer, {@code RECURSIVE_DESCENDER}, was physically removed,
	 * making the counter a constant zero (the removal proof remains in
	 * design consultation*.txt and the history of {@code WorklistDescentCensusTest}).
	 * </p>
	 */
	public static final AtomicLong WORKLIST_COMPAT_FALLBACKS = new AtomicLong();

	/**
	 * Number of times the worklist driver descends a MULTICOL boundary as a nonrecursive native
	 * scope ({@code FlowContainer.MulticolRestyleScope}). Added 2026-07-30, increment 1.
	 * Used to prove that the native path is exercised ("the test actually passed through this path").
	 */
	public static final AtomicLong MULTICOL_NATIVE_DESCENTS = new AtomicLong();

	/**
	 * Stack tracking the current continuation path (PAGE/COLUMN). Added 2026-07-21, B1.
	 * Like {@link ResumeTrace#begin(String)}, it represents nested breaks with a stack,
	 * but it is always enabled regardless of debug properties (to classify observation counters).
	 *
	 * <p>
	 * 2026-07-24 (architecture review finding): Changed to ThreadLocal because a single static Deque
	 * mixes push/pop operations across concurrent conversions, causing incorrect counts and potentially
	 * failing conversions with {@code pop()} on an empty Deque (preventing crashes is an absolute requirement).
	 * </p>
	 */
	private static final ThreadLocal<ArrayDeque<Boolean>> continuationPathStack = ThreadLocal
			.withInitial(ArrayDeque::new);

	/**
	 * Starts tracking a continuation path. {@code RootBuilder.ResumeSession.resume()} and
	 * {@code BreakableBuilder.columnBreak()} pair this with {@link #endContinuationPath()}
	 * using try/finally.
	 *
	 * @param column true for the column-break (COLUMN) path, false for the page-break (PAGE) path
	 */
	public static void beginContinuationPath(final boolean column) {
		continuationPathStack.get().push(column);
	}

	/** Ends tracking started by {@link #beginContinuationPath(boolean)}. */
	public static void endContinuationPath() {
		continuationPathStack.get().pop();
	}

	private static boolean isColumnPath() {
		final Boolean top = continuationPathStack.get().peek();
		return top != null && top;
	}

	/**
	 * Called each time the OpenChain branch of {@code FlowContainer.restyle} descends one level.
	 * Increments {@link #RESTYLE_CHAIN_FIRINGS} and the separate counter for the current
	 * continuation path (PAGE/COLUMN).
	 */
	public static void recordChainFiring() {
		RESTYLE_CHAIN_FIRINGS.incrementAndGet();
		(isColumnPath() ? COLUMN_RESTYLE_CHAIN_FIRINGS : PAGE_RESTYLE_CHAIN_FIRINGS).incrementAndGet();
	}

	/**
	 * Called immediately before the worklist executor enters the compatibility fallback
	 * (see {@link #WORKLIST_COMPAT_FALLBACKS}).
	 */
	public static void recordWorklistCompatFallback() {
		WORKLIST_COMPAT_FALLBACKS.incrementAndGet();
	}

	/**
	 * Called immediately before the worklist driver descends a MULTICOL boundary as a native scope
	 * (see {@link #MULTICOL_NATIVE_DESCENTS}).
	 */
	public static void recordMulticolNativeDescent() {
		MULTICOL_NATIVE_DESCENTS.incrementAndGet();
	}

	/** Number of {@code ColumnsContainer.splitPageAxis} attempts (API consolidated in M6c-1). */
	public static void recordColumnsSplitAttempt() {
		COLUMNS_SPLIT_ATTEMPTS.incrementAndGet();
	}

	/** Number of times the entire last column is a MOVE candidate with multiple columns (API consolidated in M6c-1). */
	public static void recordLastColumnMoveCandidate() {
		COLUMNS_LAST_COLUMN_MOVE_CANDIDATE.incrementAndGet();
	}

	/**
	 * High-water mark of the number of retained {@code LayoutSource} events (maximum before compact).
	 * Added 2026-07-24, E-6 increment 1: measurements for selecting spill thresholds and targets
	 * for the spillable tape infrastructure. Does not affect behavior.
	 */
	public static final AtomicLong SOURCE_EVENT_HIGH_WATER = new AtomicLong();

	/** Observes the number of retained LayoutSource events (E-6 increment 1; retains the maximum). */
	public static void recordSourceEventRetention(final int size) {
		SOURCE_EVENT_HIGH_WATER.accumulateAndGet(size, Math::max);
	}

	/**
	 * High-water mark of retained inline text payload in {@code LayoutSource}
	 * (bytes; UTF-16 estimate = character count × 2). Added 2026-07-24, E-6 increment 3b-2.
	 * Endurance test acceptance checks use this to verify that the spill budget
	 * ({@code processing.text-spill-budget}) is respected (this value ≦ budget).
	 */
	public static final AtomicLong LIVE_TEXT_PAYLOAD_BYTES = new AtomicLong();

	/** Number of text payload spill records (E-6 increment 3b-2). */
	public static final AtomicLong SPILLED_TEXT_RECORDS = new AtomicLong();

	/** Total bytes of spilled text payload (E-6 increment 3b-2). */
	public static final AtomicLong SPILLED_TEXT_BYTES = new AtomicLong();

	/** Observes retained inline text payload (E-6 increment 3b-2; retains the maximum). */
	public static void recordLiveTextPayloadBytes(final long bytes) {
		LIVE_TEXT_PAYLOAD_BYTES.accumulateAndGet(bytes, Math::max);
	}

	/** Observes text payload spills (E-6 increment 3b-2). */
	public static void recordTextSpill(final long bytes) {
		SPILLED_TEXT_RECORDS.incrementAndGet();
		SPILLED_TEXT_BYTES.addAndGet(bytes);
	}

	// ---- E-6 increment 4a/4b (2026-07-24): firing counters for TwoPass range conversion ----

	/**
	 * Number of {@code TwoPassBlockBuilder} binds performed with {@code SourceRangeBody}
	 * (rerunning SegmentExecutor over a LayoutSource range).
	 * Added 2026-07-24, E-6 increment 4a/4b.
	 */
	public static final AtomicLong RANGE_FIRST_BINDS = new AtomicLong();

	/**
	 * Number of times sealing at recording completion (close) of a float/absolute/inline-block outside
	 * a table is eligible and switches to {@code SourceRangeBody} (E-6 increment 4a/4b).
	 * See {@link #twoPassSealRejects(TwoPassSealReject)} for the rejection breakdown.
	 */
	public static final AtomicLong TWO_PASS_SEALS_ELIGIBLE = new AtomicLong();

	/**
	 * Number of times a table caption is recorded as Opaque (not replayable).
	 * Observation for caption recipe conversion C0, 2026-08-01:
	 * consult-codex-2026-08-01-caption-recipe.txt. This should become zero with recipe recording in C1.
	 * A parent range containing this becomes ineligible due to containsOpaque;
	 * this caused the 10 TOPLEVEL cases among the 23 remaining legacy cases.
	 */
	public static final AtomicLong CAPTION_OPAQUE_RECORDS = new AtomicLong();

	/**
	 * Number of times a caption Start is rejected as the root of a replay range (or without a table context).
	 * C2 context-complete gate; always zero at C0.
	 */
	public static final AtomicLong CAPTION_ROOT_REJECTS = new AtomicLong();

	/**
	 * Number of times a range containing a caption is accepted with an established table context
	 * (C2; always zero at C0).
	 */
	public static final AtomicLong CAPTION_CONTEXT_ACCEPTS = new AtomicLong();

	/** Ineligible TwoPass seal. The caller fails conversion with an invariant exception. */
	public enum TwoPassSealReject {
		/** Missing primary source or page context. */
		NO_SOURCE,
		/** Missing anchor/end, or an empty range with measured content. */
		NO_RANGE,
		/** An event or table context cannot be replayed from a recipe. */
		OPAQUE_RANGE,
		/** Absolute positioning in the range does not match the proof of exclusive ownership. */
		ABSOLUTE_RANGE,
		/** Cannot transfer ownership of a child or execution plan to the parent range. */
		NESTED_BUILDER,
		/** The range is missing due to compact or a similar operation. */
		RANGE_NOT_INTACT
	}

	private static final Map<TwoPassSealReject, AtomicLong> TWO_PASS_SEAL_REJECTS = new EnumMap<>(
			TwoPassSealReject.class);
	static {
		for (final TwoPassSealReject r : TwoPassSealReject.values()) {
			TWO_PASS_SEAL_REJECTS.put(r, new AtomicLong());
		}
	}

	/** Root categories for the range census. */
	public enum TwoPassRootKind {
		TOPLEVEL, NESTED, GRID_ITEM, FLEX_ITEM, INCREMENTAL_CELL, INCREMENTAL_CAPTION,
		RETAINED_CELL, RETAINED_CAPTION
	}

	/** BIND corresponds to the total number of range replays. Others are supplementary observations. */
	public enum TwoPassCensusEvent {
		BIND, SEAL, MEASURE_RANGE, EMPTY_BIND
	}

	/** Construction type of a Grid/Flex item. NONE denotes a non-item. */
	public enum TwoPassItemKind { NONE, ANONYMOUS, TAKEOVER, ELEMENT }

	/** Tests assign document names per reset/snapshot unit. A null barrierReason means NONE. */
	public record TwoPassCensusKey(TwoPassRootKind rootKind,
			boolean sealAttempted, String sealOutcome, boolean measurement, BarrierReason barrierReason,
			TwoPassItemKind itemKind) {
	}

	/**
	 * Global census (static, like the existing AtomicLong counters). DirectSession conversions run on
	 * a separate thread from tests, so ThreadLocal cannot deliver counts (observed 2026-09-05: zero).
	 * Only one census can be open at a time.
	 */
	private static volatile TwoPassCensus twoPassCensus;

	/**
	 * Per-document range cross-tabulation. Enabled only during a period explicitly started by a test.
	 * Normal conversions create no maps/tags/strings and perform no extra log scans.
	 * Cross-checks independently at the same counting points without changing existing AtomicLong counters.
	 */
	public static final class TwoPassCensus implements AutoCloseable {
		private final Map<TwoPassCensusEvent, Map<TwoPassCensusKey, AtomicLong>> counts = new EnumMap<>(
				TwoPassCensusEvent.class);
		private volatile boolean measurement;

		private TwoPassCensus() {
			for (final TwoPassCensusEvent event : TwoPassCensusEvent.values()) {
				this.counts.put(event, new java.util.concurrent.ConcurrentHashMap<>());
			}
		}

		public Map<TwoPassCensusKey, Long> snapshot(final TwoPassCensusEvent event) {
			final Map<TwoPassCensusKey, Long> result = new HashMap<>();
			this.counts.get(event).forEach((key, count) -> result.put(key, count.get()));
			return Map.copyOf(result);
		}

		private void reset() {
			this.counts.values().forEach(Map::clear);
		}

		@Override
		public void close() {
			twoPassCensus = null;
		}
	}

	/** Surrounds a synchronous DirectSession conversion. Calls the existing reset() for each document. */
	public static TwoPassCensus beginTwoPassCensus() {
		if (twoPassCensus != null) {
			throw new IllegalStateException("TwoPass census is already active");
		}
		final TwoPassCensus census = new TwoPassCensus();
		twoPassCensus = census;
		return census;
	}

	/** Census phase scope corresponding to replay intent. ReplayIntent propagates the intent itself. */
	public static final class TwoPassMeasurement implements AutoCloseable {
		private final TwoPassCensus census;
		private final boolean previous;

		private TwoPassMeasurement(final TwoPassCensus census, final ReplayIntent intent) {
			this.census = census;
			this.previous = census.measurement;
			census.measurement |= intent == ReplayIntent.MEASURE;
		}

		@Override
		public void close() {
			this.census.measurement = this.previous;
		}
	}

	public static TwoPassMeasurement twoPassMeasurement(final ReplayIntent intent) {
		final TwoPassCensus census = twoPassCensus;
		return census == null ? null : new TwoPassMeasurement(census, intent);
	}

	/** Diagnostic tag passed from builder to DeferredBind. Retains no boxes or logs. */
	public static final class TwoPassCensusTag {
		private final TwoPassCensus census;
		private TwoPassRootKind rootKind = TwoPassRootKind.TOPLEVEL;
		private boolean attempted;
		private String outcome = "NOT_ATTEMPTED";
		private BarrierReason barrier;
		private TwoPassItemKind itemKind = TwoPassItemKind.NONE;

		private TwoPassCensusTag(final TwoPassCensus census) {
			this.census = census;
		}

		public void rootKind(final TwoPassRootKind kind) {
			this.rootKind = kind;
		}

		public void itemKind(final TwoPassItemKind kind) {
			this.itemKind = kind;
		}

		public void seal(final boolean attempted, final String outcome, final BarrierReason barrier) {
			this.attempted = attempted;
			this.outcome = outcome;
			this.barrier = barrier;
			this.record(TwoPassCensusEvent.SEAL);
		}

		public void record(final TwoPassCensusEvent event) {
			final TwoPassCensusKey key = new TwoPassCensusKey(this.rootKind, this.attempted,
					this.outcome, this.census.measurement, this.barrier, this.itemKind);
			this.census.counts.get(event).computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
		}
	}

	public static TwoPassCensusTag newTwoPassCensusTag() {
		final TwoPassCensus census = twoPassCensus;
		return census == null ? null : new TwoPassCensusTag(census);
	}

	/** Counts range bind (SourceRangeBody) firings (E-6 increment 4a/4b). */
	public static void recordTwoPassRangeBind() {
		RANGE_FIRST_BINDS.incrementAndGet();
	}

	/**
	 * Number of empty-body seals (DP increment 2, 2026-07-30).
	 * Number of builders with both an empty source range and no measured content that switch
	 * to {@code ReplayBody.Empty} on close.
	 */
	public static final AtomicLong TWO_PASS_EMPTY_SEALS = new AtomicLong();

	/** Number of empty-body binds (no-op; DP increment 2). */
	public static final AtomicLong TWO_PASS_EMPTY_BINDS = new AtomicLong();

	/**
	 * Number of sealed (already counted as eligible) builders absorbed into the parent's range conversion,
	 * releasing their lease without binding (DP increment 3, 2026-07-30). T1 accounting for detecting
	 * a 1:1 seal:bind ratio observes {@code TWO_PASS_SEALS_ELIGIBLE == TWO_PASS_RANGES_CONSUMED +
	 * TWO_PASS_SEALS_SUBSUMED + TWO_PASS_SEALS_ABANDONED}. The handle state machine enforces the guarantee.
	 */
	public static final AtomicLong TWO_PASS_SEALS_SUBSUMED = new AtomicLong();

	/** Number of handles consumed in actual layout. Accounting includes consumption through exceptions. */
	public static final AtomicLong TWO_PASS_RANGES_CONSUMED = new AtomicLong();

	/** Number of independent event replays without a primary source. */
	public static final AtomicLong TWO_PASS_REPLAY_ONLY_BINDS = new AtomicLong();

	/** Number of handles discarded without replay. Includes bodies acquired during temporary measurement. */
	public static final AtomicLong TWO_PASS_SEALS_ABANDONED = new AtomicLong();

	/** Number of table cells among discarded handles. */
	public static final AtomicLong CELL_RANGE_SEALS_ABANDONED = new AtomicLong();

	/** Counts absorption into the parent's range conversion (DP increment 3). */
	public static void recordTwoPassSealSubsumed() {
		TWO_PASS_SEALS_SUBSUMED.incrementAndGet();
	}

	/**
	 * Number of sealed table cells (already counted in CELL_RANGE_SEALS) absorbed into the parent's
	 * range conversion, releasing their lease without binding (table absorption = codex increment 5,
	 * 2026-07-30). Completion of cell lease accounting is observed as
	 * {@code CELL_RANGE_SEALS == CELL_RANGE_BINDS +
	 * CELL_RANGE_SEALS_SUBSUMED + CELL_RANGE_SEALS_ABANDONED}.
	 */
	public static final AtomicLong CELL_RANGE_SEALS_SUBSUMED = new AtomicLong();

	/** Counts absorbed table cell seals (table absorption = codex increment 5). */
	public static void recordCellRangeSealSubsumed() {
		CELL_RANGE_SEALS_SUBSUMED.incrementAndGet();
	}

	/** Counts empty-body seals (DP increment 2). */
	public static void recordTwoPassEmptySeal() {
		TWO_PASS_EMPTY_SEALS.incrementAndGet();
	}

	/** Counts empty-body binds (no-op; DP increment 2). */
	public static void recordTwoPassEmptyBind() {
		TWO_PASS_EMPTY_BINDS.incrementAndGet();
	}

	/** Counts eligible seals (E-6 increment 4a/4b). */
	public static void recordTwoPassSealEligible() {
		TWO_PASS_SEALS_ELIGIBLE.incrementAndGet();
	}

	/** Counts ineligible seals with reasons (E-6 increment 4a/4b). */
	public static void recordTwoPassSealReject(final TwoPassSealReject reason) {
		TWO_PASS_SEAL_REJECTS.get(reason).incrementAndGet();
	}

	// ---- E-6 increment 5a (2026-07-24): firing counters for table cell (CellContent) range conversion ----

	/**
	 * Number of times a Retained table cell's seal at close is eligible and {@code CellContent}
	 * switches to retaining "IntrinsicSizes values + SourceRange (+lease)"
	 * (added 2026-07-24, E-6 increment 5a). Cell sealing goes through
	 * {@code TwoPassBlockBuilder.sealBodyForRangeBind}, so this is a subset of
	 * {@link #TWO_PASS_SEALS_ELIGIBLE}. The rejection breakdown is also counted in the same
	 * {@link #twoPassSealRejects(TwoPassSealReject)}.
	 */
	public static final AtomicLong CELL_RANGE_SEALS = new AtomicLong();

	/**
	 * Number of sealed cell binds (SegmentExecutor range execution after column widths are finalized)
	 * (E-6 increment 5a). A subset of {@link #RANGE_FIRST_BINDS}. To detect a 1:1 lease ratio
	 * (leaks permanently clamp compact), this must always equal {@link #CELL_RANGE_SEALS};
	 * DisplayListGoldenTest enforces this.
	 */
	public static final AtomicLong CELL_RANGE_BINDS = new AtomicLong();

	/** Counts cell range seals (E-6 increment 5a). */
	public static void recordCellRangeSeal() {
		CELL_RANGE_SEALS.incrementAndGet();
	}

	/** Counts range binds of sealed cells (E-6 increment 5a). */
	public static void recordCellRangeBind() {
		CELL_RANGE_BINDS.incrementAndGet();
	}

	// ---- E-6 increment 5b-2 (2026-07-24): firing counters for table Pass C (sequential row binds) ----

	/**
	 * Number of Retained tables whose bindRows runs Pass B/C (scratch measurement of all cells →
	 * finalize row heights → sequential row binds). Added 2026-07-24, E-6 increment 5b-2.
	 * Eligibility fails closed per table: all real cells are converted to ranges (or Empty),
	 * there are no captions, and all cells can be cloned for measurement
	 * ({@code RetainedTableBuilder.isRowSequentialBindEligible}).
	 */
	public static final AtomicLong TABLE_PASS_C_TABLES = new AtomicLong();

	/**
	 * Number of ineligible Retained tables falling back to the conventional bindRows path
	 * (batch bind of all cells before row-height calculation).
	 * E-6 increment 5b-2; contributes to the eligibility-rate denominator.
	 */
	public static final AtomicLong TABLE_LEGACY_BINDROWS = new AtomicLong();

	/**
	 * Number of scratch measurements in Pass B (row measurement), E-6 increment 5b-2.
	 * In a Pass C table, row heights read only these measurements, and no bound cell body trees exist
	 * (measurement trees are discarded after collecting values). This observes "zero cell body trees
	 * retained during Pass B." RetentionHighWaterReportTest verifies firings at real-cell scale.
	 */
	public static final AtomicLong TABLE_PASS_B_CELL_MEASURES = new AtomicLong();

	/** Counts tables processed by the Pass B/C path (E-6 increment 5b-2). */
	public static void recordTablePassC() {
		TABLE_PASS_C_TABLES.incrementAndGet();
	}

	/** Counts tables falling back to the conventional bindRows path (E-6 increment 5b-2). */
	public static void recordTableLegacyBindRows() {
		TABLE_LEGACY_BINDROWS.incrementAndGet();
	}

	/** Counts Pass B cell scratch measurements (E-6 increment 5b-2). */
	public static void recordTablePassBCellMeasure() {
		TABLE_PASS_B_CELL_MEASURES.incrementAndGet();
	}

	/** Number of seal rejections for {@code reason} (E-6 increment 4a/4b). */
	public static long twoPassSealRejects(final TwoPassSealReject reason) {
		return TWO_PASS_SEAL_REJECTS.get(reason).get();
	}

	/** Number of open text slice transports (M3b; API consolidated in M6c-1). */
	public static void recordOpenTextHandoff() {
		OPEN_TEXT_HANDOFFS.incrementAndGet();
	}

	/** Observes the owner's column count at a column break (API consolidated in M6c-1). */
	public static void recordLastColumnOwnerColumnCount(final int columnCount) {
		LAST_COLUMN_OWNER_COLUMN_COUNT.set(columnCount);
	}

	/** Counts chain child frame (Child) consumption (API consolidated in M6c-1). */
	public static void recordChildFrame() {
		CHILD_FRAMES.incrementAndGet();
	}

	/** Counts restyles outside chains (API consolidated in M6c-1). */
	public static void recordUnchainedRestyle() {
		UNCHAINED_RESTYLES.incrementAndGet();
	}

	/** Counts open tail consumption (API consolidated in M6c-1). */
	public static void recordOpenTail() {
		OPEN_TAILS.incrementAndGet();
	}

	/**
	 * Counts how the collectible prefix scan in {@code RootBuilder.pageBreak()} classifies
	 * each level (added 2026-07-21, B1). {@link ContinuationCapability#PLAIN_FLOW} normally
	 * does not appear here because it continues the scan. Counts reasons that <b>stop</b> the scan
	 * (or empty if the scan never stops before exhausting the chain).
	 */
	private static final Map<ContinuationCapability, AtomicLong> CAPABILITY_SCAN_STOPS = new EnumMap<>(
			ContinuationCapability.class);
	static {
		for (final ContinuationCapability c : ContinuationCapability.values()) {
			CAPABILITY_SCAN_STOPS.put(c, new AtomicLong());
		}
	}

	/** Number of times {@code reason} stops the prefix scan. */
	public static long capabilityScanStops(final ContinuationCapability reason) {
		return CAPABILITY_SCAN_STOPS.get(reason).get();
	}

	/** Records a scan stop reason (always other than {@code PLAIN_FLOW}). */
	public static void recordCapabilityScanStop(final ContinuationCapability reason) {
		CAPABILITY_SCAN_STOPS.get(reason).incrementAndGet();
	}

	/**
	 * Counts how the relative open path scan for COLUMN continuation classifies each level
	 * (added 2026-07-21, M6b Phase B4-Step3; wired as of 2026-07-25).
	 * {@link #CAPABILITY_SCAN_STOPS}/{@link #capabilityScanStops} are PAGE-only counters
	 * (despite their generic names, only the PAGE side currently calls them), so COLUMN uses
	 * separate counters. A single document commonly has both PAGE and COLUMN continuation paths
	 * (e.g., column breaks within multi-column layout); shared counters would let COLUMN
	 * contributions invalidate the expectations of existing PAGE-only tests.
	 */
	private static final Map<ContinuationCapability, AtomicLong> COLUMN_CAPABILITY_SCAN_STOPS = new EnumMap<>(
			ContinuationCapability.class);
	static {
		for (final ContinuationCapability c : ContinuationCapability.values()) {
			COLUMN_CAPABILITY_SCAN_STOPS.put(c, new AtomicLong());
		}
	}

	/** Records a COLUMN scan stop reason (always other than {@code PLAIN_FLOW}). */
	public static void recordColumnCapabilityScanStop(final ContinuationCapability reason) {
		COLUMN_CAPABILITY_SCAN_STOPS.get(reason).incrementAndGet();
	}


	/**
	 * Maximum consecutive <b>automatic page breaks without progress</b> (added 2026-07-27).
	 * Observes livelock where "only page breaks repeat while the state stays the same."
	 */
	public static final AtomicLong MAX_STALLED_AUTO_BREAK_RUN = new AtomicLong();

	/** Number of times {@link #STALLED_AUTO_BREAK_LIMIT} is reached (added 2026-07-27). */
	public static final AtomicLong STALLED_AUTO_BREAK_ALARMS = new AtomicLong();

	/**
	 * Safety threshold for {@link #guardBreakProgress} (added 2026-07-27).
	 *
	 * <p>
	 * <b>Based on measurements</b>: Across all 873 tests, the maximum number of consecutive
	 * <b>automatic</b> page breaks with unchanged state was five
	 * ({@code FloatSplitCommitSmokeTest}). Forced page breaks reached 97, so the guard applies only
	 * to automatic page breaks: forced breaks mean "the author specified the number of pages" and
	 * must not be judged by progress. This threshold is more than six times the measured maximum,
	 * so reaching it strongly indicates an implementation livelock.
	 * </p>
	 *
	 * <p>
	 * <b>Why this is needed</b>: Each iteration of this livelock allocates one blank PDF page.
	 * Pages are retained permanently in {@code PDFWriterImpl.pageOutputs}, so heap usage grows
	 * monotonically and exhausts even 4 GB. Moreover, the no-progress deadline in
	 * {@code AbstractUserAgent.checkAbort} <b>does not fire</b> because it counts "a page was emitted"
	 * as progress (observed 2026-07-27: about 45,000 pages and OOM from a 1.6 KB document).
	 * </p>
	 */
	public static final int STALLED_AUTO_BREAK_LIMIT = 32;

	private ContinuationStats() {
		// counters
	}

	/**
	 * Checks whether automatic page breaks make progress, warns and returns true when repetitions
	 * of the same state reach the safety threshold ({@link #STALLED_AUTO_BREAK_LIMIT}).
	 * Added 2026-07-27. On 2026-07-29, the design changed from throwing an exception to abandoning
	 * the page break: the caller continues layout in place and returns output even if it overflows.
	 *
	 * @param stalledRun repetitions with unchanged state since the preceding automatic page break
	 * @return true if the page break should be abandoned
	 */
	public static boolean guardBreakProgress(final int stalledRun) {
		MAX_STALLED_AUTO_BREAK_RUN.accumulateAndGet(stalledRun, Math::max);
		if (stalledRun >= STALLED_AUTO_BREAK_LIMIT) {
			STALLED_AUTO_BREAK_ALARMS.incrementAndGet();
			final String message = "auto page break repeated " + stalledRun
					+ " times without any progress (same break target and flow depth, no new source events or table rows, floats "
					+ "carrying no less to the next page, and the page cursor repeats or keeps growing in nested breaks); the layout "
					+ "is livelocked, so page breaking is abandoned and the content is laid out in place (it may overflow the page)";
			java.util.logging.Logger.getLogger(ContinuationStats.class.getName()).warning(message);
			// **Return "abandon the page break" instead of an exception** (2026-07-29).
			//
			// Livelocks that reach this point do exist
			// (floats structurally unable to reach the escape route in branch table 5 of
			// `FloatSplitPlan.classify`; see `開発メモ`). Previously, this threw
			// {@code ContinuationInvariantViolationException},
			// but that is a <b>conversion failure</b>, and {@code ARCHITECTURE.md} §5.13 defines
			// conversion failure as "always an engine defect";
			// a document with a broken type area cannot be excluded on that basis.
			//
			// The same §5.13 also requires that "even for a document containing boxes that do not fit
			// on the sheet, the engine <b>must return output, either by allowing overflow
			// or by moving them to the next page</b>." Therefore, favor **returning output**.
			//
			// The threshold (32) is far above the longest legitimate run measured (five), but it does not rule out
			// false positives by itself: what RootBuilder counts as progress does. On 2026-10-09 rubydoc-api showed
			// one: a 47000pt float broken page by page after the end of the input kept the input position, the cursor
			// and the target, so 32 breaks that each placed a page of the float abandoned the rest of the document.
			// RootBuilder now counts a float carrying less to the next page as progress, as it counts input events
			// and table rows. A lower threshold (2) suppresses legitimate page breaks as well (observed:
			// `FloatTableTest` regressed from 4 pages to 3).
			return true;
		}
		return false;
	}

	/**
	 * Records the depth of the open ancestor chain (added 2026-07-21;
	 * a single implementation shared by PAGE/COLUMN paths).
	 *
	 * <p>
	 * 2026-07-30 (legacy recursion removal = increment 4c): The old {@code guardOpenDepth} threw a
	 * typed exception at depth 64 to stop OpenChain recursion in FlowContainer.restyle before a
	 * StackOverflowError. Once the worklist executor became the sole driver and OpenChain descent
	 * became nonrecursive, this guard became solely a <b>spurious cause of crashes</b>.
	 * The exception, alarms, and threshold ({@code ContinuationDepthLimitExceededException}/
	 * {@code PAGE/COLUMN_OPEN_DEPTH_ALARMS}/64) were retired, leaving only maximum depth recording
	 * for observation (codex consultation
	 * consult-codex-2026-07-30-increment4-removal-spec.txt §3).
	 * </p>
	 *
	 * @param openDepth depth of the open ancestor chain ({@link OpenShape#depth()})
	 * @param column    true for the column-break (COLUMN) path, false for the page-break (PAGE) path
	 */
	public static void recordOpenDepth(final int openDepth, final boolean column) {
		(column ? MAX_COLUMN_OPEN_TAIL_DEPTH : MAX_PAGE_OPEN_TAIL_DEPTH).accumulateAndGet(openDepth, Math::max);
	}

	public static void reset() {
		final TwoPassCensus census = twoPassCensus;
		if (census != null) {
			census.reset();
		}
		CHILD_FRAMES.set(0);
		COLUMNS_SPLIT_ATTEMPTS.set(0);
		COLUMNS_LAST_COLUMN_MOVE_CANDIDATE.set(0);
		LAST_COLUMN_OWNER_COLUMN_COUNT.set(-1);
		OPEN_TAILS.set(0);
		SOURCE_EVENT_HIGH_WATER.set(0);
		LIVE_TEXT_PAYLOAD_BYTES.set(0);
		SPILLED_TEXT_RECORDS.set(0);
		SPILLED_TEXT_BYTES.set(0);
		UNCHAINED_RESTYLES.set(0);
		OPEN_TEXT_HANDOFFS.set(0);
		MAX_PAGE_OPEN_TAIL_DEPTH.set(0);
		MAX_COLUMN_OPEN_TAIL_DEPTH.set(0);
		RESTYLE_CHAIN_FIRINGS.set(0);
		PAGE_RESTYLE_CHAIN_FIRINGS.set(0);
		COLUMN_RESTYLE_CHAIN_FIRINGS.set(0);
		WORKLIST_COMPAT_FALLBACKS.set(0);
		MULTICOL_NATIVE_DESCENTS.set(0);
		MAX_STALLED_AUTO_BREAK_RUN.set(0);
		STALLED_AUTO_BREAK_ALARMS.set(0);
		RANGE_FIRST_BINDS.set(0);
		TWO_PASS_EMPTY_SEALS.set(0);
		TWO_PASS_EMPTY_BINDS.set(0);
		TWO_PASS_SEALS_SUBSUMED.set(0);
		TWO_PASS_RANGES_CONSUMED.set(0);
		TWO_PASS_REPLAY_ONLY_BINDS.set(0);
		TWO_PASS_SEALS_ABANDONED.set(0);
		CELL_RANGE_SEALS_ABANDONED.set(0);
		CELL_RANGE_SEALS_SUBSUMED.set(0);
		TWO_PASS_SEALS_ELIGIBLE.set(0);
		CAPTION_OPAQUE_RECORDS.set(0);
		CAPTION_ROOT_REJECTS.set(0);
		CAPTION_CONTEXT_ACCEPTS.set(0);
		CELL_RANGE_SEALS.set(0);
		CELL_RANGE_BINDS.set(0);
		TABLE_PASS_C_TABLES.set(0);
		TABLE_LEGACY_BINDROWS.set(0);
		TABLE_PASS_B_CELL_MEASURES.set(0);
		for (final AtomicLong counter : TWO_PASS_SEAL_REJECTS.values()) {
			counter.set(0);
		}
		for (final AtomicLong counter : CAPABILITY_SCAN_STOPS.values()) {
			counter.set(0);
		}
		for (final AtomicLong counter : COLUMN_CAPABILITY_SCAN_STOPS.values()) {
			counter.set(0);
		}
	}

}
