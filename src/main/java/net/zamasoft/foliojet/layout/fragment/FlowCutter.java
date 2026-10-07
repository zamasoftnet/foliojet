package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Split decisions for flow containers (M4-A2). Extracts the decisions from
 * FlowContainer.splitPageAxis as pure functions that do not touch boxes.
 *
 * <p>
 * Note: "attempt to split a child" in the main loop mutates the child on success.
 * Complete three-phase separation (a pure selection phase) requires side-effect-free child
 * splitting (M6 fragmentation). Here, only pre-loop and opportunity-scan decisions are made pure;
 * the execution loop skeleton remains in FlowContainer.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlowCutter {
	/**
	 * Result of the split decision before entering the main loop. Each value retains the cut line
	 * used during execution (the old implementation's distinction between pageLimit / prevPageSize).
	 */
	public sealed interface PreDecision {
		/** Cuts at the head (cutHead). */
		record CutHead(double atLimit) implements PreDecision {
		}

		/** Keeps everything on the preceding page (only floats are considered for splitting). */
		record KeepFloats(double atLimit) implements PreDecision {
		}

		/** Moves everything to the next page (no float splitting). */
		record MoveAll() implements PreDecision {
		}

		/** Moves everything to the next page (also considers splitting floats). */
		record MoveWithFloats(double atLimit) implements PreDecision {
		}

		/** Cuts at the tail (cutTail). */
		record CutTail(double atLimit) implements PreDecision {
		}

		/** Proceeds to the main loop (cut line already adjusted if necessary). */
		record Proceed(double adjustedPageLimit) implements PreDecision {
		}
	}

	private FlowCutter() {
		// utility
	}

	/**
	 * Makes the pre-loop split decision. Preserves the direction of comparison operators in the
	 * old implementation exactly (do not move when the cut line coincides with an edge:
	 * the policy of minimizing page breaks).
	 *
	 * @param pageLimit     distance from the box's inner top edge to the cut line
	 * @param pageSize      box page-axis size (including normal flow overflowing the box)
	 * @param pageInnerSize box inner page-axis size (including normal flow overflowing the box)
	 * @param frameStart    frame width at the page-axis start
	 * @param flags         bitwise OR of IPageBreakableBox.FLAGS_*
	 * @param hasFlows      whether normal-flow children exist
	 * @return decision result
	 */
	public static PreDecision preDecide(final double pageLimit, final double pageSize, final double pageInnerSize,
			final double frameStart, final byte flags, final boolean hasFlows) {
		if (LayoutUtils.compare(pageLimit, 0) <= 0) {
			// When the cut line is at or above the inner top edge
			// ** Use <= to avoid moving when the cut line coincides with the top edge **
			if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
				// Cut at the head
				return new PreDecision.CutHead(pageLimit);
			}
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				// When at the start of the page
				if (LayoutUtils.compare(frameStart, 0) > 0) {
					// Cut if there is a top edge
					return new PreDecision.CutHead(pageLimit);
				}
				// Keep on the preceding page
				return new PreDecision.KeepFloats(pageLimit);
			}
			// Move to the next page
			return new PreDecision.MoveAll();
		}
		if ((flags & (IPageBreakableBox.FLAGS_SPLIT | IPageBreakableBox.FLAGS_LAST)) == 0
				&& LayoutUtils.compare(pageLimit, pageSize) >= 0) {
			// When an automatic page break's cut line is at or below the inner bottom edge
			// ** Use >= to avoid moving when the cut line coincides with the bottom edge **
			// Keep on the preceding page
			return new PreDecision.KeepFloats(pageLimit);
		}
		double adjusted = pageLimit;
		if ((flags & (IPageBreakableBox.FLAGS_SPLIT | IPageBreakableBox.FLAGS_LAST)) == 0
				&& LayoutUtils.compare(pageLimit, pageInnerSize) >= 0) {
			adjusted = pageInnerSize - LayoutUtils.THRESHOLD * 2;
		}
		if (!hasFlows) {
			// When there is no normal flow
			if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
				// Cut
				return new PreDecision.CutTail(pageLimit);
			}
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				// Keep if there is no height
				if (LayoutUtils.compare(pageInnerSize, 0) > 0) {
					return new PreDecision.CutTail(pageLimit);
				}
				return new PreDecision.KeepFloats(pageLimit);
			}
			// Move to the next page if there is height
			if ((flags & IPageBreakableBox.FLAGS_LAST) != 0 || LayoutUtils.compare(pageSize, 0) > 0) {
				return new PreDecision.MoveWithFloats(pageLimit);
			}
			return new PreDecision.KeepFloats(pageLimit);
		}
		return new PreDecision.Proceed(adjusted);
	}

	/**
	 * Result of pushback due to an inter-block page-break prohibition (avoid).
	 *
	 * @param resumeIndex  index at which to resume the loop (assumes the loop's ++i)
	 * @param newPageLimit cut line after pushback
	 */
	public record AvoidPushback(int resumeIndex, double newPageLimit) {
	}

	/**
	 * Checks inter-block page-break prohibitions and returns a pushback target if needed.
	 * Walks backward through touching preceding blocks (stacked without gaps); if avoid is specified,
	 * raises the cut line to a safe boundary. A splittable floating box crossing the cut line
	 * lifts the prohibition (preserves the old implementation's behavior exactly).
	 *
	 * @param i                  current flow index
	 * @param pageLimit          current cut line
	 * @param flowPageStarts     page-axis start of each flow
	 * @param flowPageExtents    page-axis size of each flow
	 * @param avoidBefore        break-before:avoid of each flow
	 * @param avoidAfter         break-after:avoid of each flow
	 * @param flowPageEndFrames  page-axis end frame width of each flow (blocks only)
	 * @param floatPageStarts    page-axis start of each float (null if none)
	 * @param floatPageExtents   page-axis size of each float
	 * @param floatUncut         whether each float is unsplittable (replaced element or avoid)
	 * @return AvoidPushback if pushback is needed, otherwise null
	 */
	public static AvoidPushback avoidPushback(final int i, final double pageLimit, final double[] flowPageStarts,
			final double[] flowPageExtents, final boolean[] avoidBefore, final boolean[] avoidAfter,
			final double[] flowPageEndFrames, final double[] floatPageStarts, final double[] floatPageExtents,
			final boolean[] floatUncut) {
		int beforeFlows = 1;
		boolean breakAvoid = avoidBefore[i];
		int beforeIndex = i - 1;
		// Check that page breaks are prohibited between touching blocks
		for (int j = i - 1; j >= 0; --j) {
			final double beforeBottom = flowPageStarts[j] + flowPageExtents[j];
			if (LayoutUtils.compare(beforeBottom, flowPageStarts[i]) < 0) {
				if (j == i - 1) {
					breakAvoid = false;
				}
				break;
			}
			beforeFlows++;
			beforeIndex = j;
			if (avoidAfter[j]) {
				breakAvoid = true;
			}
		}
		if (breakAvoid && floatPageStarts != null) {
			// Make a page break when there is a splittable floating box.
			// Invariant: a splittable crossing float cancels keep backtracking
			// (splitFloatings splits the float independently); an unsplittable
			// (replaced element / page-break-inside:avoid = floatUncut)
			// crossing float does not prevent keep backtracking. Enforced by FlowCutterTest and
			// the paired integration fixtures float-split-in-chain (cuttable) /
			// float-uncut-before-prefix (uncut)
			for (int k = 0; k < floatPageStarts.length; ++k) {
				if (LayoutUtils.compare(floatPageStarts[k], pageLimit) >= 0) {
					breakAvoid = false;
					break;
				}
				if (LayoutUtils.compare(floatPageStarts[k] + floatPageExtents[k], pageLimit) <= 0) {
					continue;
				}
				if (floatUncut[k]) {
					continue;
				}
				breakAvoid = false;
				break;
			}
		}
		if (!breakAvoid) {
			return null;
		}
		assert beforeFlows >= 2;
		final double newPageLimit = flowPageStarts[beforeIndex] - LayoutUtils.THRESHOLD * 2
				+ flowPageExtents[beforeIndex] - flowPageEndFrames[beforeIndex];
		return new AvoidPushback(i - beforeFlows, newPageLimit);
	}

	/**
	 * Post-loop decision when the main loop finds no split point.
	 * Exactly reproduces the branches at 889-919 of the old implementation.
	 *
	 * @param flags          bitwise OR of IPageBreakableBox.FLAGS_*
	 * @param lastOrphan     index immediately after the last flow that fits before the cut line
	 * @param pageInnerSize  box inner page-axis size
	 * @param lastFlowBottom bottom position of the last flow
	 * @param prevPageSize   original (unadjusted) cut line
	 * @return decision result (CutTail / KeepFloats / MoveWithFloats)
	 */
	public static PreDecision tailDecide(final byte flags, final int lastOrphan, final double pageInnerSize,
			final double lastFlowBottom, final double prevPageSize) {
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
			// Cut
			return new PreDecision.CutTail(prevPageSize);
		}
		if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
			// Start of the page (ignore FLAGS_LAST)
			if (LayoutUtils.compare(pageInnerSize, lastFlowBottom) > 0) {
				// Split a box taller than its natural height
				return new PreDecision.CutTail(prevPageSize);
			}
			return new PreDecision.KeepFloats(prevPageSize);
		}
		if (lastOrphan == 0) {
			// Move to the next page if nothing is above the cut line
			return new PreDecision.MoveWithFloats(prevPageSize);
		}
		// Cut at the tail
		return new PreDecision.CutTail(prevPageSize);
	}

	/**
	 * Calculated flags for one step of the automatic page-break main loop
	 * (splitPageAxis two-phase separation, increment 1, 2026-08-01).
	 *
	 * @param splitLine    cut line in this flow's local coordinates (pageLimit - the flow's pageAxis)
	 * @param positionMask mask derived from the flow's physical position (formerly lflags;
	 *                      FIRST = flow touches the start, LAST = at the end and the automatic
	 *                      page break does not target this container itself)
	 * @param splitFlags   AND of positionMask and outer flags (formerly xflags;
	 *                      effective flags passed to split())
	 */
	public record StepFlags(double splitLine, byte positionMask, byte splitFlags) {
	}

	/**
	 * Calculates flags for each step of the automatic page-break main loop
	 * (pure extraction of FlowContainer.splitPageAxis 872-884).
	 *
	 * <p>
	 * positionMask clears bits from 0xFF, so bits other than FIRST/LAST (e.g., FLAGS_SPLIT) always pass.
	 * This property is needed to let the corresponding outer flags pass unchanged through the
	 * AND operation in splitFlags.
	 * </p>
	 *
	 * @param pageLimit             current cut line (already pushed back after pushback)
	 * @param flowPageAxis          page-axis start position of the flow
	 * @param index                 flow index
	 * @param flowCount             total flow count
	 * @param autoBreakTargetsOwner whether the automatic page break targets this container itself
	 * @param outerFlags            bitwise OR of FLAGS_* passed from outside
	 * @return calculated step flags
	 */
	public static StepFlags stepFlags(final double pageLimit, final double flowPageAxis, final int index,
			final int flowCount, final boolean autoBreakTargetsOwner, final byte outerFlags) {
		byte positionMask = (byte) 0xFF;
		if (LayoutUtils.compare(flowPageAxis, 0) > 0) {
			// Do not keep on the preceding page if the box's top is away from the page's top
			positionMask ^= IPageBreakableBox.FLAGS_FIRST;
		}
		if (autoBreakTargetsOwner || index != flowCount - 1) {
			// Handle the current flow or an intermediate flow freely
			positionMask ^= IPageBreakableBox.FLAGS_LAST;
		}
		return new StepFlags(pageLimit - flowPageAxis, positionMask, (byte) (positionMask & outerFlags));
	}

	/**
	 * Resolution of a Keep observation (two-phase separation, increment 3, 2026-08-01).
	 */
	public enum KeepResolution {
		/** End of the page: finalize with the entire container kept in this fragment. */
		KEEP_ALL,
		/** Not pulled along: proceed to inspect the next flow. */
		EXAMINE_NEXT,
		/** Pulled along by a page-break prohibition (index&lt;lastOrphan): convert to Move. */
		TREAT_AS_MOVE
	}

	/**
	 * Resolution rule when a flow observes Keep (stays on this side without splitting) in the main loop.
	 * Captures the most counterintuitive behavior, "Keep turns into Move when pulled along,"
	 * as a pure function.
	 *
	 * @param index      current flow index
	 * @param lastOrphan index immediately after the last flow that fits before the cut line
	 * @param splitFlags effective flags for this step ({@link StepFlags#splitFlags})
	 * @return resolution result
	 */
	public static KeepResolution resolveKeep(final int index, final int lastOrphan, final byte splitFlags) {
		if ((splitFlags & IPageBreakableBox.FLAGS_LAST) != 0) {
			// If keeping at the end of the page, keep everything
			return KeepResolution.KEEP_ALL;
		}
		if (index >= lastOrphan) {
			// Not pulled along by a page-break prohibition
			return KeepResolution.EXAMINE_NEXT;
		}
		// Pull along with the following box
		return KeepResolution.TREAT_AS_MOVE;
	}

	/**
	 * Resolution of a Move observation (unsplittable; move everything)
	 * (two-phase separation, increment 4, 2026-08-01).
	 */
	public sealed interface MoveResolution {
		/**
		 * The result for the entire container is final (CutHead/CutTail/KeepFloats/MoveAll;
		 * the cut line is the unadjusted prevPageSize).
		 */
		record Terminal(PreDecision action) implements MoveResolution {
		}

		/**
		 * Restart from lastOrphan, ignoring page-break prohibitions
		 * (restore the cut line to the restart value after preDecide).
		 *
		 * @param nextIndex next index actually inspected (=lastOrphan)
		 */
		record RestartIgnoringAvoid(int nextIndex) implements MoveResolution {
		}

		/**
		 * Preserves avoid at boundaries while applying a rescue split inside the last box
		 * of an avoid chain that cannot fit even in an empty fragmentainer.
		 *
		 * @param index         index of the flow to rescue-split
		 * @param fallbackIndex index at which to restart ignoring avoid if rescue fails
		 */
		record RelaxInside(int index, int fallbackIndex) implements MoveResolution {
		}

		/** Pushback due to an inter-block page-break prohibition. */
		record Pushback(int resumeIndex, double newPageLimit) implements MoveResolution {
		}

		/** Transfers this flow and all following flows to the next fragment (partition finalized). */
		record Partition() implements MoveResolution {
		}
	}

	/**
	 * Resolution rule when a flow observes Move (unsplittable; move everything) in the main loop
	 * (pure extraction of the old FlowContainer.splitPageAxis 1026-1065).
	 *
	 * <p>
	 * Physical FIRST (positionMask) differs from outer FIRST (outerFlags): the former says whether
	 * the flow touches the container's start; the latter says whether the container is at the page's
	 * start from its parent's perspective.
	 * </p>
	 *
	 * @param positionMask physical position mask for this step ({@link StepFlags#positionMask})
	 * @param outerFlags   bitwise OR of FLAGS_* passed from outside
	 * @param index        current flow index
	 * @param lastOrphan   index immediately after the last flow that fits before the cut line
	 * @param ignoreAvoid  whether rerunning with page-break prohibitions ignored
	 * @param relaxInsideIndex index of the last box whose internal restriction can be relaxed
	 *                          to preserve the avoid chain (-1 if none)
	 * @param prevPageSize unadjusted cut line (used to execute terminal actions)
	 * @param pageLimit    current cut line (used for the pushback decision)
	 * @param fragmentCapacity page-axis capacity of an empty fragmentainer
	 * @param flowPageStarts    measurements passed to {@link #avoidPushback}
	 * @param flowPageExtents   same as above
	 * @param avoidBefore       same as above
	 * @param avoidAfter        same as above
	 * @param flowPageEndFrames same as above
	 * @param floatPageStarts   same as above
	 * @param floatPageExtents  same as above
	 * @param floatUncut        same as above
	 * @return resolution result
	 */
	public static MoveResolution resolveMove(final byte positionMask, final byte outerFlags, final int index,
			final int lastOrphan, final boolean ignoreAvoid, final int relaxInsideIndex, final double prevPageSize,
			final double pageLimit, final double fragmentCapacity,
			final double[] flowPageStarts, final double[] flowPageExtents, final boolean[] avoidBefore,
			final boolean[] avoidAfter, final double[] flowPageEndFrames, final double[] floatPageStarts,
			final double[] floatPageExtents, final boolean[] floatUncut) {
		if ((positionMask & IPageBreakableBox.FLAGS_FIRST) != 0) {
			// Start of the box (physical FIRST)
			if ((outerFlags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
				// Forced split
				return new MoveResolution.Terminal(new PreDecision.CutHead(prevPageSize));
			}
			if ((outerFlags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				// Start of the page
				if (index < lastOrphan) {
					// As a last resort, before relaxing boundary avoid, relax break-inside of
					// the last box in a chain that cannot fit even in an empty fragmentainer
					if (relaxInsideIndex == lastOrphan
							&& avoidChainExceedsCapacity(index, relaxInsideIndex, fragmentCapacity, flowPageStarts,
									flowPageExtents)) {
						return new MoveResolution.RelaxInside(relaxInsideIndex, lastOrphan);
					}
					// Normally, ignore page-break prohibitions
					return new MoveResolution.RestartIgnoringAvoid(lastOrphan);
				}
				if ((outerFlags & IPageBreakableBox.FLAGS_LAST) != 0) {
					// Cut if at the tail
					return new MoveResolution.Terminal(new PreDecision.CutTail(prevPageSize));
				}
				// Keep on the preceding page
				return new MoveResolution.Terminal(new PreDecision.KeepFloats(prevPageSize));
			}
			// Move everything, unless the ledger contains a splittable float crossing the cut line;
			// then partition (Partition: move the child, while splitFloatings independently
			// splits the float and keeps its head fragment on this page). MoveAll here
			// relocates the box to the next page together with the float (head fragment)
			// already laid out on this page, pushing the entire body text to the next page
			// (observed on three news sites including pc.watch.impress.co.jp, 2026-08-10).
			// Triggered by effectively float-only in-flow content plus a first child returning Move.
			// An unsplittable (replaced element / page-break-inside:avoid = floatUncut) crossing
			// float still does not prevent MoveAll, matching the invariant of
			// avoidPushback
			if (hasCrossingCuttableFloat(pageLimit, floatPageStarts, floatPageExtents, floatUncut)) {
				return new MoveResolution.Partition();
			}
			return new MoveResolution.Terminal(new PreDecision.MoveAll());
		}
		if (!ignoreAvoid && index > 0 && index <= lastOrphan) {
			// Only for the second and later elements in a box: inter-block page-break prohibition
			final AvoidPushback pushback = avoidPushback(index, pageLimit, flowPageStarts, flowPageExtents,
					avoidBefore, avoidAfter, flowPageEndFrames, floatPageStarts, floatPageExtents, floatUncut);
			if (pushback != null) {
				return new MoveResolution.Pushback(pushback.resumeIndex(), pushback.newPageLimit());
			}
		}
		return new MoveResolution.Partition();
	}

	/**
	 * Whether the container's float ledger has a cuttable float that crosses the cut line: it starts before
	 * {@code pageLimit} and ends after it, and is neither replaced, a rescue fragment nor
	 * {@code page-break-inside: avoid}. Such a float is split by {@code splitFloatings} and its head stays in this
	 * fragmentainer, so moving the flows that follow it still makes progress.
	 *
	 * @param pageLimit        the cut line, from the container's start
	 * @param floatPageStarts  float starts (null when there are no floats)
	 * @param floatPageExtents float extents
	 * @param floatUncut       floats that cannot be cut
	 * @return true if such a float exists
	 */
	public static boolean hasCrossingCuttableFloat(final double pageLimit, final double[] floatPageStarts,
			final double[] floatPageExtents, final boolean[] floatUncut) {
		if (floatPageStarts == null) {
			return false;
		}
		for (int k = 0; k < floatPageStarts.length; ++k) {
			if (LayoutUtils.compare(floatPageStarts[k], pageLimit) >= 0) {
				continue;
			}
			if (LayoutUtils.compare(floatPageStarts[k] + floatPageExtents[k], pageLimit) <= 0) {
				continue;
			}
			if (floatUncut[k]) {
				continue;
			}
			return true;
		}
		return false;
	}

	/** Checks whether the entire avoid chain cannot fit even when moved to an empty fragmentainer. */
	private static boolean avoidChainExceedsCapacity(final int firstIndex, final int lastIndex,
			final double fragmentCapacity, final double[] flowPageStarts, final double[] flowPageExtents) {
		if (!(fragmentCapacity > 0) || firstIndex < 0 || lastIndex < firstIndex
				|| lastIndex >= flowPageStarts.length || lastIndex >= flowPageExtents.length) {
			return false;
		}
		final double chainExtent = flowPageStarts[lastIndex] + flowPageExtents[lastIndex]
				- flowPageStarts[firstIndex];
		return LayoutUtils.compare(chainExtent, fragmentCapacity) > 0;
	}

	/**
	 * Returns the index immediately after the last flow that fits before the cut line
	 * (0 = no flows fit, length = all flows fit).
	 *
	 * @param flowBottoms effective bottom position of each flow
	 * @param pageLimit   cut line
	 * @return index immediately after the last flow that can stay on the preceding page
	 */
	public static int lastOrphan(final double[] flowBottoms, final double pageLimit) {
		int lastOrphan;
		for (lastOrphan = flowBottoms.length - 1; lastOrphan >= 0; --lastOrphan) {
			if (LayoutUtils.compare(flowBottoms[lastOrphan], pageLimit) <= 0) {
				break;
			}
		}
		return lastOrphan + 1;
	}
}
