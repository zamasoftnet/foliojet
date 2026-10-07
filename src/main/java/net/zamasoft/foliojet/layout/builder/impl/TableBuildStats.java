package net.zamasoft.foliojet.layout.builder.impl;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Instrumentation for table construction characteristics (P2-1; §5.2b preservation contract for table builder unification).
 *
 * <p>
 * Makes the characteristics that must survive the OnePass/TwoPass replacement observable:
 * routing, streaming retention bounds, rowspan cuts, and fragment counts.
 * Like SegmentReplayCoverageTest, this prevents vacuous green results: golden agreement alone
 * cannot detect fixed layout degrading to full retention. Characterization tests use these
 * counters to lock down the paths and bounded memory use.
 * </p>
 */
public final class TableBuildStats {
	/**
	 * Number of OnePass (Incremental = can commit early) builds. Primarily table-layout:fixed,
	 * but not exclusively; see {@link TableRetentionReason}
	 * (correction on 2026-07-19: "OnePass=fixed" was inaccurate).
	 */
	public static final AtomicLong ONE_PASS_BUILDS = new AtomicLong();

	/**
	 * Number of TwoPass (Retained = retains the entire table before committing) builds.
	 * Primarily table-layout:auto, but not exclusively: non-FLOW placement, a specified page-axis size,
	 * an auto line-axis size, and nested measurement passes also route here
	 * (correction on 2026-07-19: "TwoPass=auto" was inaccurate).
	 */
	public static final AtomicLong TWO_PASS_BUILDS = new AtomicLong();

	/**
	 * Maximum number of row buffers held concurrently by OnePass (Incremental) (high-water mark).
	 * Evidence that streaming (bounded memory) has not degraded to full retention:
	 * only the vicinity of unclosed rowspans is retained. **However, a row-group with an absolute height
	 * (`<tbody style="height:...">`) has a known limitation: it retains every row until the row-group closes**
	 * (discovered on 2026-07-19; see CSS-SUPPORT.md. An unbounded growth path other than table-layout:auto).
	 */
	public static final AtomicLong ONE_PASS_ROW_HIGH_WATER = new AtomicLong();

	/** Number of cuts of cells connected by rowspan (cutRowspanCells). */
	public static final AtomicLong ROWSPAN_CUTS = new AtomicLong();

	/** Number of table fragments created (splitTableBox). */
	public static final AtomicLong TABLE_FRAGMENTS = new AtomicLong();

	// ---- E-6 increment 1 (2026-07-24): observation counters for the spillable tape infrastructure ----
	// Measurement infrastructure for choosing spill thresholds and targets. Only reads and updates maxima,
	// with no effect on behavior (design consultation §3-1).

	/**
	 * High-water mark of row count (header+body+footer) per Retained (full-table retention) table.
	 * E-6 increment 1 (2026-07-24): measurements for choosing spill thresholds and targets; no effect on behavior.
	 */
	public static final AtomicLong RETAINED_ROW_HIGH_WATER = new AtomicLong();

	/**
	 * High-water mark of actual cell count per Retained table (CellContent with a TwoPassBlockBuilder,
	 * excluding filler slots for rowspan/colspan continuations).
	 * E-6 increment 1 (2026-07-24): measurements for choosing spill thresholds and targets; no effect on behavior.
	 */
	public static final AtomicLong RETAINED_CELL_HIGH_WATER = new AtomicLong();

	/**
	 * High-water mark of logical cell slots (rows × columns) per Retained table.
	 * Corresponds to the O(R×C) retention bound for row/cell Maps, collapsed border arrays, etc.
	 * E-6 increment 1 (2026-07-24): measurements for choosing spill thresholds and targets; no effect on behavior.
	 */
	public static final AtomicLong RETAINED_CELL_SLOT_HIGH_WATER = new AtomicLong();

	/**
	 * High-water mark of repeated header (thead) rows per Retained table. Must remain on the heap even
	 * after adding spill support (repeated on each page, so they cannot be streamed to tape).
	 * E-6 increment 1 (2026-07-24): measurements for choosing spill thresholds and targets; no effect on behavior.
	 */
	public static final AtomicLong RETAINED_REPEATED_HEADER_ROW_HIGH_WATER = new AtomicLong();

	/**
	 * High-water mark of repeated footer (tfoot) rows per Retained table (same as above).
	 * E-6 increment 1 (2026-07-24): measurements for choosing spill thresholds and targets; no effect on behavior.
	 */
	public static final AtomicLong RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER = new AtomicLong();

	/**
	 * High-water mark of distinct (starting column, colspan) constraints held by AutoColumnWidths per
	 * Retained table (Uspan, worst case O(C²)). Used to estimate the size of column statistics that stay
	 * on the heap even after adding spill support.
	 * E-6 increment 1 (2026-07-24): measurements for choosing spill thresholds and targets; no effect on behavior.
	 */
	public static final AtomicLong RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER = new AtomicLong();

	/**
	 * Counts of routing to Retained by reason ({@link TableRetentionReason}). A table with multiple
	 * reasons increments each reason once, so the sum can exceed {@link #TWO_PASS_BUILDS}.
	 * E-6 increment 1 (2026-07-24): measurements for choosing spill thresholds and targets; no effect on behavior.
	 */
	private static final Map<TableRetentionReason, AtomicLong> RETENTION_REASON_COUNTS = new EnumMap<>(
			TableRetentionReason.class);
	static {
		for (final TableRetentionReason reason : TableRetentionReason.values()) {
			RETENTION_REASON_COUNTS.put(reason, new AtomicLong());
		}
	}

	/**
	 * High-water mark of TwoPassBlockBuilder nesting depth (consecutive TwoPassBlockBuilders on the
	 * layoutStack chain, including itself).
	 * E-6 increment 1 (2026-07-24): measurements for choosing spill thresholds and targets; no effect on behavior.
	 */
	public static final AtomicLong TWO_PASS_NEST_DEPTH_HIGH_WATER = new AtomicLong();

	/** Maximum concurrent leases for one LayoutSource (counts leases separately even with the same fromId). */
	public static final AtomicLong SOURCE_LEASE_HIGH_WATER = new AtomicLong();

	/** Maximum number of Events retained by LayoutSource, including retention for reasons other than leases. */
	public static final AtomicLong SOURCE_RETAINED_EVENT_HIGH_WATER = new AtomicLong();

	/** Maximum nextId - oldest lease fromId. Measures how long the oldest watermark stalls, in EventId units. */
	public static final AtomicLong SOURCE_OLDEST_WATERMARK_LAG_HIGH_WATER = new AtomicLong();

	/** Oldest watermark when the maximum lag above was first observed. -1 if no lease has been observed. */
	public static final AtomicLong SOURCE_OLDEST_WATERMARK_AT_HIGH_WATER = new AtomicLong(-1);

	/** Observation only. Does not throttle or force release based on a retention limit. */
	public static synchronized void reportSourceRetention(final long leases, final long events,
			final long oldestWatermark, final long nextId) {
		SOURCE_LEASE_HIGH_WATER.accumulateAndGet(leases, Math::max);
		SOURCE_RETAINED_EVENT_HIGH_WATER.accumulateAndGet(events, Math::max);
		if (leases > 0) {
			final long lag = Math.max(0, nextId - oldestWatermark);
			if (lag > SOURCE_OLDEST_WATERMARK_LAG_HIGH_WATER.get()) {
				SOURCE_OLDEST_WATERMARK_LAG_HIGH_WATER.set(lag);
				SOURCE_OLDEST_WATERMARK_AT_HIGH_WATER.set(oldestWatermark);
			}
		}
	}

	private TableBuildStats() {
		// stats
	}

	/**
	 * Reports the OnePass retained row count (keeps the maximum).
	 */
	public static void reportRowRetention(final int retained) {
		ONE_PASS_ROW_HIGH_WATER.accumulateAndGet(retained, Math::max);
	}

	/**
	 * Reports the retention profile of a Retained table (one table at prepareLayout;
	 * E-6 increment 1, updates maxima only).
	 *
	 * @param rows              row count (header+body+footer)
	 * @param cells             actual cell count (excluding continuation slots)
	 * @param slots             logical cell slot count (rows × columns)
	 * @param headerRows        repeated header row count
	 * @param footerRows        repeated footer row count
	 * @param colspanConstraints distinct (starting column, colspan) constraint count (Uspan)
	 */
	public static void reportRetainedTableShape(final int rows, final int cells, final long slots, final int headerRows,
			final int footerRows, final int colspanConstraints) {
		RETAINED_ROW_HIGH_WATER.accumulateAndGet(rows, Math::max);
		RETAINED_CELL_HIGH_WATER.accumulateAndGet(cells, Math::max);
		RETAINED_CELL_SLOT_HIGH_WATER.accumulateAndGet(slots, Math::max);
		RETAINED_REPEATED_HEADER_ROW_HIGH_WATER.accumulateAndGet(headerRows, Math::max);
		RETAINED_REPEATED_FOOTER_ROW_HIGH_WATER.accumulateAndGet(footerRows, Math::max);
		RETAINED_COLSPAN_CONSTRAINT_HIGH_WATER.accumulateAndGet(colspanConstraints, Math::max);
	}

	/**
	 * Records reasons for routing to Retained (E-6 increment 1). Call exactly once per table
	 * from TableBuilderLifecycle.start, where routing occurs. Do not count inside
	 * TableBuildPlanner.plan, which is also called for queries.
	 */
	public static void recordRetentionReasons(final Set<TableRetentionReason> reasons) {
		for (final TableRetentionReason reason : reasons) {
			RETENTION_REASON_COUNTS.get(reason).incrementAndGet();
		}
	}

	/** Count of Retained routings due to {@code reason} (E-6 increment 1). */
	public static long retentionReasonCount(final TableRetentionReason reason) {
		return RETENTION_REASON_COUNTS.get(reason).get();
	}

	/** Reports TwoPassBlockBuilder nesting depth (E-6 increment 1; keeps the maximum). */
	public static void reportTwoPassNestDepth(final int depth) {
		TWO_PASS_NEST_DEPTH_HIGH_WATER.accumulateAndGet(depth, Math::max);
	}
}
