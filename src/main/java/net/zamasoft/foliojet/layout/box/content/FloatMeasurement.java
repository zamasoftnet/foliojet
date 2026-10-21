package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Pure data that fixes the measurements needed to decide pagination for a single float
 * (added 2026-07-24, exclusion area P2, P2-1; development log and design consultation §2.1).
 *
 * <p>
 * An adjacent extension to {@code FlowContainer.FloatMeasurements} (three parallel arrays
 * for FlowCutter). Copies the inputs used by each branch of {@link Floatings#splitPageAxis}
 * into one read-only record per float. ordinal is not a list index but a <b>stable ordinal
 * at collection time</b> (lesson from the addBound incident: codex design §2.5, separating
 * ordinal from list index). The {@code splitPageAxis} loop mutates indices with remove/--i,
 * so the two must not be confused.
 * </p>
 *
 * @param ordinal         stable ordinal at collection time (zero-based, original order)
 * @param serial          {@link BoxHolder#serial} (identifier inherited by the SPLIT remainder)
 * @param box             box identity (verification anchor at commit time)
 * @param pageStart       measured page-axis start ({@code Floating.pageAxis})
 * @param pageEnd         measured page-axis end ({@code pageStart + pageExtent})
 * @param pageExtent      measured page-axis size in the owner's writing direction
 * @param sameWritingAxis whether the owner and float have the same actual writing axis (vertical/horizontal).
 *                        Always true for REPLACED, which is atomic and bypasses the axis check
 * @param fragmentHead    whether physically at the fragment start
 *                        ({@code LayoutUtils.compare(pageStart, 0) <= 0}).
 *                        "first" in the branch table is this AND {@code FLAGS_FIRST}
 *                        (flags vary per call, so are not fixed here)
 * @param moveToNext      one-time transfer determined at placement by intersection with the 2-D bottom band
 * @param monolithic      placement found that splitting makes no progress
 *                        (2026-09-17, {@code FloatBlockBox#splitMakesNoProgress})
 * @param boxType         {@link BoxType#BLOCK} or {@link BoxType#REPLACED}
 * @param pageBreakInside {@code page-break-inside} for BLOCK; null for REPLACED, where it does not apply
 * @param notShrunk       the float is the remainder of a fragment-start split and has not shrunk below its extent
 *                        before that split ({@code FloatBlockBox#fragmentStartSplitExtent}, 2026-10-18). Splitting
 *                        it again counts as no progress only when it is "first" again; the branch table applies that
 *                        (2026-10-19, triage §83)
 */
public record FloatMeasurement(
		int ordinal,
		int serial,
		IFloatBox box,
		double pageStart,
		double pageEnd,
		double pageExtent,
		boolean sameWritingAxis,
		boolean fragmentHead,
		boolean moveToNext,
		BoxType boxType,
		PageBreakMode pageBreakInside,
		boolean monolithic,
		boolean notShrunk) {

	/**
	 * Collects measurements from a placed float (read-only; affects neither {@code floating}
	 * nor its box).
	 *
	 * @param ordinal   stable ordinal at collection time
	 * @param floating  the target float
	 * @param ownerFlow the writing direction of the owner (the box of the container holding the float)
	 *                  that determines the page axis
	 * @return the measurement record
	 */
	public static FloatMeasurement of(final int ordinal, final Floatings.Floating floating,
			final WritingMode ownerFlow) {
		final double pageExtent = occupiedPageExtent(floating.box, ownerFlow);
		final BoxType boxType = floating.box.getType();
		final boolean sameWritingAxis;
		final PageBreakMode pageBreakInside;
		if (boxType == BoxType.BLOCK) {
			final var params = ((AbstractContainerBox) floating.box).getBlockParams();
			sameWritingAxis = sameWritingAxis(ownerFlow, floating.box);
			pageBreakInside = params.pageBreakInside;
		} else {
			sameWritingAxis = true;
			pageBreakInside = null;
		}
		final var block = floating.box instanceof net.zamasoft.foliojet.layout.box.impl.FloatBlockBox f ? f : null;
		return new FloatMeasurement(ordinal, floating.serial, floating.box, floating.pageAxis,
				floating.pageAxis + pageExtent, pageExtent, sameWritingAxis,
				LayoutUtils.compare(floating.pageAxis, 0) <= 0, floating.moveToNext, boxType, pageBreakInside,
				block != null && block.splitMakesNoProgress(),
				block != null && !Double.isNaN(block.fragmentStartSplitExtent()) && splitMadeNoProgress(pageExtent,
						block.fragmentStartSplitExtent(), block.fragmentStartSplitGrew()));
	}

	/**
	 * Whether a float split at a fragment start made no progress, judged from the remainder's occupied extent against
	 * the extent before that split (2026-10-21). The same size is no progress. A larger remainder is too once a
	 * remainder of the same float has grown before: a box moved whole into the remainder can be laid out again at
	 * its content (RowSplitContainer grows a moved flex whose items run past the page), which makes the remainder
	 * larger although the split left content on the page (FIT seed 30125121, triage §92: a float with a 119pt
	 * vertical-rl column flex in vertical-lr grew from 396.2pt to 557.7pt and was placed off the paper whole).
	 * Allowing one growth keeps the guard against remainders rebuilt larger every time (wild seed 24521670: 102.1pt,
	 * then 108.25pt on every page), at the cost of one more split.
	 *
	 * @param extent     occupied extent of the remainder now
	 * @param before     occupied extent before the split that made it
	 * @param grewBefore whether a remainder of this float has already grown
	 */
	public static boolean splitMadeNoProgress(final double extent, final double before, final boolean grewBefore) {
		final int c = LayoutUtils.compare(extent, before);
		return c == 0 || (c > 0 && grewBefore);
	}

	/**
	 * Combines {@code FIRST} up to the parent with the float's own fragment-start status.
	 *
	 * <p>
	 * In a nested content box, a float at local start 0 is not at the page/column start
	 * unless that box itself is first in its parent. Placement and splitting must both
	 * use this combination to agree on {@code first} for the same float (2026-09-04).
	 * </p>
	 */
	public static boolean isFragmentStart(final boolean ancestorsFirst, final boolean fragmentHead) {
		return ancestorsFirst && fragmentHead;
	}

	/**
	 * Checks the occupied end of an indivisible float using the painted-sliver rule.
	 * Only overflow below 1 pt counts as fitting; exactly 1 pt or more moves the float
	 * (2026-08-10/2026-09-04). Branch table 1 for splittable floats does not use this tolerance
	 * and continues to use {@link LayoutUtils#compare}.
	 */
	public static boolean fitsPageUnsplittable(final double pageEnd, final double pageLimit) {
		return pageEnd - pageLimit < 1.0;
	}

	/**
	 * Returns the size a float <b>actually occupies on the page axis</b> (added 2026-07-28).
	 *
	 * <p>
	 * {@code getPageExtent()} reports only <b>box geometry</b>. For floats with an explicit
	 * page-axis size ({@code width} in vertical writing, {@code height} in horizontal writing),
	 * content exceeding that size was <b>counted as 0</b> even when drawn outside the box with
	 * {@code overflow:visible}. This satisfied branch table 1 (entirely before the cut line)
	 * in {@link FloatSplitPlan#classify}, <b>leaving content laid out beyond the paper without
	 * splitting</b> (local/shrink/strict-149858-min.html).
	 * </p>
	 *
	 * <p>
	 * This is the same correction already applied to normal flow by
	 * {@code FlowContainer.computeFlowBottoms()} using {@code Math.max(内寸, getContentSize())}
	 * (the first argument is the inner size);
	 * only floats lacked it. Taking {@code max} ensures that even unused size that paints nothing
	 * is scheduled for splitting as before. Suppressing splits when painting is smaller
	 * than geometry is the responsibility of {@code BreakableBuilder.paintsNothingBeyondPage()}.
	 * </p>
	 *
	 * @param box       the target float
	 * @param ownerFlow the owner's writing direction that determines the page axis
	 * @return the larger of the geometric size and the painting extent
	 */
	public static double occupiedPageExtent(final net.zamasoft.foliojet.layout.box.IFloatBox box,
			final WritingMode ownerFlow) {
		return Math.max(box.getPageExtent(ownerFlow), box.paintedPageExtent(ownerFlow));
	}

	/**
	 * Returns whether the owner and float contents share the actual writing axis (vertical/horizontal; 2026-09-04).
	 * A float whose block flow runs the other way on the same axis is cut as before: from 85272cf8 to 2026-10-09 it
	 * was kept whole like an orthogonal one, which left one larger than the page off the page. Only column
	 * balancing keeps it whole ({@code FlowContainer.balancePageSizeFloor}).
	 */
	public static boolean sameWritingAxis(final WritingMode ownerFlow, final IFloatBox box) {
		if (box.getType() != BoxType.BLOCK) {
			return true;
		}
		final WritingMode floatFlow = ((AbstractContainerBox) box).getBlockParams().flow;
		return ownerFlow.isVertical() == floatFlow.isVertical();
	}

	/**
	 * Returns whether the float cannot split along the page axis (2026-09-04).
	 *
	 * <p>
	 * The single entry point for using the same predicate during placement and fragmentation.
	 * BLOCK is indivisible if its writing axis differs from the owner's, or if it has
	 * {@code break-inside:avoid} and is not first. REPLACED/RESCUE are always atomic.
	 * </p>
	 *
	 * @param boxType         the target box type
	 * @param sameWritingAxis whether the actual writing axes of the owner and float match
	 * @param pageBreakInside BLOCK's break-inside; null for other box types
	 * @param first           true to treat the box as physically at the fragment start
	 */
	public static boolean isUnsplittable(final BoxType boxType, final boolean sameWritingAxis,
			final PageBreakMode pageBreakInside, final boolean first) {
		switch (boxType) {
		case BLOCK:
			return !sameWritingAxis || (pageBreakInside == PageBreakMode.AVOID && !first);
		case REPLACED:
		case RESCUE:
			return true;
		default:
			throw new IllegalStateException(boxType.toString());
		}
	}
}
