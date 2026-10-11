package net.zamasoft.foliojet.layout.box.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.DebugFlags;

/**
 * A pure plan for float pagination (added 2026-07-24, exclusion area P2, P2-2;
 * the type from design consultation §2.1).
 *
 * <p>
 * Copies only the classification part of the branch table (development log) for
 * {@link Floatings#splitPageAxis} into a pure function ({@link #classify}).
 * Includes no destructive box splitting: {@link FloatItemPlan.SplitOnCommit} marks
 * "call {@code split} exactly once at commit time" and <b>does not predict</b> its result
 * (Keep/Move/Split). This follows codex design §2.1: do not extend P2 to make all
 * {@code IPageBreakableBox.split} implementations pure.
 * </p>
 *
 * <p>
 * Since P2-3, {@link Floatings#splitPageAxis} is driven by this plan
 * (planDirect, then commit in ordinal order). Each container independently plans and commits
 * child-flow floats at runtime (FlowContainer's typed recursive aggregation, P2-4).
 * The unused plan hierarchy (a field for child-flow plans) was removed on 2026-10-04;
 * add it when needed.
 * </p>
 *
 * @param expectedSource the target {@link Floatings} (identity anchor)
 * @param pageLimit      the cut line (owner coordinates)
 * @param flags          a snapshot of {@code IPageBreakableBox.FLAGS_*}
 * @param ownerFlow      the owner's writing direction
 * @param direct         plans for directly held floats (in stable ordinal order)
 */
public record FloatSplitPlan(
		Floatings expectedSource,
		double pageLimit,
		byte flags,
		WritingMode ownerFlow,
		List<FloatItemPlan> direct) {

	/**
	 * A destination plan for one float. {@code Keep}/{@code Move} apply to a single float,
	 * distinct from KeepAll/MoveAll in {@link FloatSplitResult}, which apply to the entire ledger.
	 * See the {@code SplitResult} Javadoc for the terminology map of the split-result type family.
	 */
	public sealed interface FloatItemPlan {
		/** A measurement snapshot of the target float. */
		FloatMeasurement expected();

		/** Keeps the float in the original fragment (branch table 1 and first in the 4→5 fall-through). */
		public record Keep(FloatMeasurement expected) implements FloatItemPlan {
		}

		/** Sends the whole float to the next fragment (branch table 2 and non-first in the 4→5 fall-through). */
		public record Move(FloatMeasurement expected) implements FloatItemPlan {
		}

		/**
		 * Calls {@code splitFloatFragment(serial, innerLimit, DEFAULT, splitFlags)}
		 * exactly once at commit time (branch table 3). Since A-3a-2, the remainder box is not
		 * built immediately: its material, {@code PreparedFloatFragment}, is materialized once
		 * when attached to the receiving Floating. Does not predict the result (Keep/Move/Prepared).
		 *
		 * @param expected   a measurement snapshot of the target float
		 * @param innerLimit the cut line in float coordinates ({@code pageLimit - pageStart};
		 *                   the frame is subtracted inside the split)
		 * @param splitFlags {@code FLAGS_FIRST} (physical first) or {@code FLAGS_SPLIT}
		 */
		public record SplitOnCommit(FloatMeasurement expected, double innerLimit, byte splitFlags)
				implements FloatItemPlan {
		}

		/**
		 * Marks a visual rescue split at commit time
		 * (added 2026-07-25, increment 7; recommendation §5; development log).
		 *
		 * <p>
		 * Replaces only branch table 5's "first: Keep with overflow allowed": an indivisible float
		 * at the fragment start that still overflows, <b>the only non-progress point currently
		 * drawn with overflow</b>. Commit puts the head fragment in the source ledger
		 * and the tail fragment in the remainder ledger. The tail undergoes normal float
		 * placement again in the next fragment.
		 * </p>
		 *
		 * <p>
		 * Unlike {@link SplitOnCommit}, this <b>fully predicts the result</b>:
		 * rescue does not touch the original box (it cuts only geometry), and the decision
		 * uses the pure function in {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}.
		 * </p>
		 *
		 * @param expected a measurement snapshot of the target float
		 * @param slice    the interval to cut out (with guaranteed progress)
		 */
		public record RescueOnCommit(FloatMeasurement expected,
				net.zamasoft.foliojet.layout.rescue.RescueDecision.Slice slice) implements FloatItemPlan {
			public RescueOnCommit {
				if (slice == null) {
					throw new IllegalArgumentException("slice");
				}
				if (slice.lastFragment()) {
					// An interval without a tail is not a meaningful rescue (a non-progress point
					// presupposes that it still overflows).
					throw new IllegalArgumentException("残余のない救済: " + slice);
				}
			}
		}
	}

	/**
	 * Creates a pure plan for directly held floats only (read-only;
	 * affects neither {@code source} nor any of its boxes).
	 *
	 * @param source    the target {@link Floatings}
	 * @param ownerFlow the owner's writing direction
	 * @param pageLimit the cut line (owner coordinates)
	 * @param flags     {@code IPageBreakableBox.FLAGS_*}
	 * @return the pure plan
	 */
	public static FloatSplitPlan planDirect(final Floatings source, final WritingMode ownerFlow, final double pageLimit,
			final byte flags) {
		final List<FloatMeasurement> measurements = source.measure(ownerFlow);
		final List<FloatItemPlan> direct = new ArrayList<>(measurements.size());
		for (final FloatMeasurement measurement : measurements) {
			direct.add(classify(measurement, pageLimit, flags));
		}
		return new FloatSplitPlan(source, pageLimit, flags, ownerFlow, Collections.unmodifiableList(direct));
	}

	/**
	 * The pure-function version of branch-table classification. Corresponds one-to-one with
	 * the branches in the {@link Floatings#splitPageAxis} loop (branch table 1, 2, 3, 4→5).
	 * Explicitly reproduces the 4→5 case fall-through as well (BLOCK with avoid and non-first,
	 * or a mismatched writing axis, falls through to the same processing as REPLACED).
	 *
	 * @param m         measurements of the target float
	 * @param pageLimit the cut line (owner coordinates)
	 * @param flags     {@code IPageBreakableBox.FLAGS_*}
	 * @return the destination plan
	 */
	public static FloatItemPlan classify(final FloatMeasurement m, final double pageLimit, final byte flags) {
		final FloatItemPlan plan = classify0(m, pageLimit, flags);
		if (DebugFlags.FLOAT_TRACE) {
			System.err.println("[float-classify] " + plan.getClass().getSimpleName() + " el="
					+ (m.box().getParams() == null ? "-" : m.box().getParams().element) + " start=" + m.pageStart()
					+ " end=" + m.pageEnd() + " extent=" + m.pageExtent() + " limit=" + pageLimit + " flags=" + flags
					+ " head=" + m.fragmentHead() + " type=" + m.boxType() + " mono=" + m.monolithic());
		}
		return plan;
	}

	private static FloatItemPlan classify0(final FloatMeasurement m, final double pageLimit, final byte flags) {
		final boolean first = FloatMeasurement.isFragmentStart(
				(flags & IPageBreakableBox.FLAGS_FIRST) != 0, m.fragmentHead());
		if (m.moveToNext()) {
			// Placement already determined intersection with the 2-D bottom band. Do not revert to Keep
			// at the physical page edge; send it to the next fragment exactly once in this split.
			return new FloatItemPlan.Move(m);
		}
		if (LayoutUtils.compare(m.pageEnd(), pageLimit) <= 0) {
			// Branch table 1: Entirely before the cut line (preserve the existing comparison)
			return new FloatItemPlan.Keep(m);
		}
		if (!first && LayoutUtils.compare(pageLimit, m.pageStart()) < 0) {
			// Branch table 2: Entirely after the cut line
			return new FloatItemPlan.Move(m);
		}
		// monolithic: If the placement progress check found that a float does not shrink when split, even a splittable
		// BLOCK falls through to branch table 5/5-R (rescue split or leave overflowing; 2026-09-17).
		if (!m.monolithic()
				&& !FloatMeasurement.isUnsplittable(m.boxType(), m.sameWritingAxis(), m.pageBreakInside(), first)) {
			// Branch table 3: Mark for a single split at commit time (do not predict the result)
			final byte splitFlags = first ? IPageBreakableBox.FLAGS_FIRST : IPageBreakableBox.FLAGS_SPLIT;
			return new FloatItemPlan.SplitOnCommit(m, pageLimit - m.pageStart(), splitFlags);
		}
		if (FloatMeasurement.fitsPageUnsplittable(m.pageEnd(), pageLimit)) {
			// Branch table 4→5: Only indivisible floats may leave a painted sliver below 1 pt.
			return new FloatItemPlan.Keep(m);
		}
		switch (m.boxType()) {
		case BLOCK:
		case REPLACED:
		case RESCUE:
			// Branch table 5: Keep with overflow allowed if first; otherwise move the whole float.
			if (!first) {
				return new FloatItemPlan.Move(m);
			}
			// Branch table 5-R (2026-07-25, rescue splitting, increment 7): "Fragment start,
			// indivisible, still overflowing" is the only non-progress point for floats that falls through
			// to drawing with overflow (recommendation §1 and §5). If rescue is possible, cut geometrically
			// instead of Keep.
			final FloatItemPlan rescue = rescue(m, pageLimit);
			return rescue != null ? rescue : new FloatItemPlan.Keep(m);
		default:
			throw new IllegalStateException(m.box().toString());
		}
	}

	/**
	 * Returns a rescue split plan for a float at a non-progress point
	 * (or {@code null} if no rescue applies). The decision itself is centralized in the pure
	 * function of {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}
	 * (recommendation §4). This method only determines how to obtain the original box,
	 * original size, and consumed amount.
	 *
	 * @param m         measurements of the target float (first and overflow already established)
	 * @param pageLimit the cut line (owner coordinates), also the fragmentainer capacity,
	 *                  since first means physically at the fragment start
	 */
	private static FloatItemPlan rescue(final FloatMeasurement m, final double pageLimit) {
		final double sourcePageExtent;
		final double offset;
		if (m.box() instanceof net.zamasoft.foliojet.layout.rescue.VisualRescueFloatBox fragment) {
			// Continuation of an already rescued fragment (do not create fragments of fragments)
			sourcePageExtent = fragment.getSourcePageExtent();
			offset = fragment.getOffset();
		} else {
			sourcePageExtent = m.pageExtent();
			offset = 0;
		}
		final double available = pageLimit - m.pageStart();
		final net.zamasoft.foliojet.layout.rescue.RescueDecision decision = net.zamasoft.foliojet.layout.rescue.RescueStats
				.record(net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner.planInFragmentainer(
						m.box().getPos().getType(), true, pageLimit, available, sourcePageExtent, offset));
		if (!(decision instanceof net.zamasoft.foliojet.layout.rescue.RescueDecision.Slice slice)) {
			return null;
		}
		if (!net.zamasoft.foliojet.layout.rescue.RescuePolicy.isEnabled()) {
			// Test-only injection point (for comparison with previous behavior). Always enabled in production.
			return null;
		}
		if (slice.lastFragment()) {
			// The calling condition (still overflowing) excludes this case. Do not rescue, as a precaution.
			return null;
		}
		// Runtime progress check (recommendation §5). Duplicates the planner invariant, but
		// the absence of infinite loops is an absolute requirement, so enforce it at runtime too.
		if (!(slice.nextOffset() > offset) || !(sourcePageExtent - slice.nextOffset() > 0)) {
			return null;
		}
		net.zamasoft.foliojet.layout.rescue.RescueStats.recordEnabled();
		return new FloatItemPlan.RescueOnCommit(m, slice);
	}
}
