package net.zamasoft.foliojet.layout.rescue;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Observation counters for visual rescue splitting (introduced 2026-07-25, increment 4).
 * Follows the same test-observation-only, behavior-neutral approach as {@code ContinuationStats} .
 *
 * <p>
 * Increment 8 (2026-07-25) removed the reason breakdown, box-kind breakdown, and {@code report()}
 * used for corpus measurement in increments 4/5, retaining only <b>three counters with readers that serve
 * as permanent safety nets</b>. The retention criterion is lasting production value;
 * all three provide that value as <b>detectors</b>.
 * </p>
 *
 * <ul>
 * <li>{@link #CANDIDATES} — <b>Nonintrusiveness detector</b>.
 * {@code VisualRescueSplitTest.testNormalDocumentNeverReachesTheRescuePoint} enforces that normal documents
 * never reach the point of nonprogress ({@code == 0}). A nonzero value means rescue decisions are leaking
 * into normal paths.
 * </li>
 * <li>{@link #SLICES} — Number of times the planner says slicing is possible.
 * Its difference from {@link #CANDIDATES} counts cases that reached nonprogress but were not rescued
 * (absolute positioning, minimum useful slice limits, etc.).</li>
 * <li>{@link #ENABLED_SLICES} — <b>Detector for fixtures missing their target</b>.
 * Counts actual fragment creation and enforces that layout changes do not silently route
 * {@code EnduranceTest} 's endurance fixture through the normal path
 * (if zero, the endurance test is exercising nothing).</li>
 * </ul>
 */
public final class RescueStats {

	private RescueStats() {
		// unused
	}

	/**
	 * Number of times the nonprogress condition is reached: at fragment start, unsplittable, and still
	 * overflowing.
	 */
	public static final AtomicLong CANDIDATES = new AtomicLong();

	/** Number of decisions returning {@link RescueDecision.Slice}. */
	public static final AtomicLong SLICES = new AtomicLong();

	/** Number of times fragments (head/tail) are actually created. */
	public static final AtomicLong ENABLED_SLICES = new AtomicLong();

	/**
	 * Records a decision at the point of nonprogress (observation only; no effect on behavior).
	 *
	 * @param decision decision result
	 * @return {@code decision} unchanged (to keep caller code short)
	 */
	public static RescueDecision record(final RescueDecision decision) {
		CANDIDATES.incrementAndGet();
		if (decision instanceof RescueDecision.Slice) {
			SLICES.incrementAndGet();
		}
		return decision;
	}

	private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(RescueStats.class.getName());
	private static final java.util.concurrent.atomic.AtomicBoolean WARNED = new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * Records actual fragment creation.
	 *
	 * <p>
	 * Rescue splitting is an intentional fail-open, but previously notified nobody
	 * (design review 2026-09-02 §1-6).
	 * Logs a WARNING only once per process to avoid flooding sweep output.
	 * </p>
	 */
	public static void recordEnabled() {
		ENABLED_SLICES.incrementAndGet();
		if (WARNED.compareAndSet(false, true)) {
			LOG.warning("rescue slicing was used to make progress at a non-advancing break;"
					+ " the layout continues with a fallback fragment (reported once per process)");
		}
	}

	public static void reset() {
		CANDIDATES.set(0);
		SLICES.set(0);
		ENABLED_SLICES.set(0);
	}
}
