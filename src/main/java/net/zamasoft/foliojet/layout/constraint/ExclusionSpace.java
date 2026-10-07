package net.zamasoft.foliojet.layout.constraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Set of float exclusion bands currently active in one context (a formatting context) (added 2026-07-23, P0 stage 1
 * of making exclusion spaces ConstraintSpace inputs, based on the design in `design consultation`).
 *
 * <p>
 * Immutable value type. Reproduces the ordering contract currently held by {@code
 * net.zamasoft.foliojet.layout.builder.impl
 * .BlockBuilder.floatings} (a mutable {@code List}): ascending pageEnd, insertion order for ties ({@code
 * BlockBuilder.FLOAT_COMP}, stable sort). At this stage, only the value type reproduces it; no consumer is
 * connected yet. Switching actual consumers (multicol avoidance, clear, addBound, TextBuilder, float placement) to
 * these queries belongs to later increments. This class itself changes no behavior.
 * </p>
 */
public final class ExclusionSpace {
	public static final ExclusionSpace EMPTY = new ExclusionSpace(List.of());

	/** Ascending {@code end} of {@link FloatExclusion#pageSpan} (ties by ascending {@link FloatExclusion#order}). */
	private final List<FloatExclusion> ascendingByPageEnd;

	private ExclusionSpace(List<FloatExclusion> ascendingByPageEnd) {
		this.ascendingByPageEnd = ascendingByPageEnd;
	}

	/**
	 * Constructs in bulk from a list already sorted by ascending {@code pageSpan.end} (insertion order for ties)
	 * (2026-07-23; resolves O(N²) noted in codex review). {@code BlockBuilder.floatings} is already stably sorted by
	 * {@code FLOAT_COMP}, so an O(N) copy suffices instead of inserting each element through {@link #plus}. The caller
	 * is responsible for the ordering contract (checked by assert).
	 */
	public static ExclusionSpace copyOfSorted(final List<FloatExclusion> ascendingByPageEnd) {
		if (ascendingByPageEnd.isEmpty()) {
			return EMPTY;
		}
		assert isAscendingByPageEnd(ascendingByPageEnd) : "list must be sorted by pageSpan.end ascending";
		return new ExclusionSpace(List.copyOf(ascendingByPageEnd));
	}

	private static boolean isAscendingByPageEnd(final List<FloatExclusion> list) {
		for (int i = 1; i < list.size(); ++i) {
			if (list.get(i - 1).pageSpan().end() > list.get(i).pageSpan().end()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns a new {@code ExclusionSpace} with {@code exclusion} added, leaving this instance unchanged. Inserts
	 * after equal-valued entries while retaining ascending {@code pageSpan.end} order (same result as {@code
	 * BlockBuilder} appending then stably sorting).
	 */
	public ExclusionSpace plus(FloatExclusion exclusion) {
		if (exclusion == null) {
			throw new IllegalArgumentException("exclusion must not be null");
		}
		final List<FloatExclusion> next = new ArrayList<>(this.ascendingByPageEnd.size() + 1);
		final double newEnd = exclusion.pageSpan().end();
		boolean inserted = false;
		for (final FloatExclusion existing : this.ascendingByPageEnd) {
			if (!inserted && existing.pageSpan().end() > newEnd) {
				next.add(exclusion);
				inserted = true;
			}
			next.add(existing);
		}
		if (!inserted) {
			next.add(exclusion);
		}
		return new ExclusionSpace(Collections.unmodifiableList(next));
	}

	/**
	 * Merges two immutable snapshots in {@code pageSpan.end, order} order. Both inputs are already sorted, so creates
	 * a new snapshot in O(N+M) without individual insertions.
	 */
	public ExclusionSpace mergedWith(final ExclusionSpace other) {
		if (other == null) {
			throw new IllegalArgumentException("other must not be null");
		}
		if (this.isEmpty()) {
			return other;
		}
		if (other.isEmpty()) {
			return this;
		}
		final List<FloatExclusion> merged = new ArrayList<>(this.size() + other.size());
		int i = 0, j = 0;
		while (i < this.ascendingByPageEnd.size() && j < other.ascendingByPageEnd.size()) {
			final FloatExclusion a = this.ascendingByPageEnd.get(i);
			final FloatExclusion b = other.ascendingByPageEnd.get(j);
			final int endOrder = Double.compare(a.pageSpan().end(), b.pageSpan().end());
			if (endOrder < 0 || endOrder == 0 && a.order() <= b.order()) {
				merged.add(a);
				++i;
			} else {
				merged.add(b);
				++j;
			}
		}
		merged.addAll(this.ascendingByPageEnd.subList(i, this.ascendingByPageEnd.size()));
		merged.addAll(other.ascendingByPageEnd.subList(j, other.ascendingByPageEnd.size()));
		return copyOfSorted(merged);
	}

	/** Whether the set is empty. */
	public boolean isEmpty() {
		return this.ascendingByPageEnd.isEmpty();
	}

	/** Number of retained exclusion bands. */
	public int size() {
		return this.ascendingByPageEnd.size();
	}

	/**
	 * View in ascending {@code pageSpan.end} order (insertion order for ties). For consumers scanning forward, such as
	 * {@code TextBuilder.locateLine}.
	 */
	public List<FloatExclusion> ascendingByPageEnd() {
		return this.ascendingByPageEnd;
	}

	/**
	 * View in descending {@code pageSpan.end} order (most recently added first for ties). For consumers scanning
	 * backward from the end, such as multicol avoidance in {@code BlockBuilder.startFlowBlock} and {@code addBound}
	 * (same order as the existing {@code for (i = floatings.size() - 1; i >= 0; --i)}).
	 */
	public List<FloatExclusion> descendingByPageEnd() {
		final List<FloatExclusion> reversed = new ArrayList<>(this.ascendingByPageEnd);
		Collections.reverse(reversed);
		return Collections.unmodifiableList(reversed);
	}

	/**
	 * Narrows {@code lineBand} using only floats whose bands contain {@code pageAxis} (start at or before {@code
	 * pageAxis} and end after it) (2026-10-05). Used for page-float sets: bottom bands start later, so selecting only
	 * by end as in {@link #narrowLineBandForMulticol} would narrow even boxes that fit before the band.
	 */
	public AxisSpan narrowLineBandAt(final double pageAxis, final AxisSpan lineBand) {
		return this.narrowLineBandOver(pageAxis, pageAxis, lineBand);
	}

	/**
	 * Narrows {@code lineBand} with float bands intersecting {@code [pageStart, pageEnd]} (2026-10-05). Used by boxes
	 * in independent formatting contexts to avoid bands intersecting their guaranteed occupied range (from frame start
	 * through minimum size). If {@code pageEnd == pageStart}, equivalent to {@link #narrowLineBandAt}.
	 */
	public AxisSpan narrowLineBandOver(final double pageStart, final double pageEnd, final AxisSpan lineBand) {
		double lineStart = lineBand.start();
		double lineEnd = lineBand.end();
		for (final FloatExclusion exclusion : this.ascendingByPageEnd) {
			if (exclusion.pageSpan().end() <= pageStart || exclusion.pageSpan().start() > pageEnd) {
				continue;
			}
			switch (exclusion.side()) {
			case START:
				lineStart = Math.max(lineStart, exclusion.lineSpan().end());
				break;
			case END:
				lineEnd = Math.min(lineEnd, exclusion.lineSpan().start());
				break;
			}
		}
		return new AxisSpan(lineStart, lineEnd);
	}

	/** Earliest end among floats ending after {@code pageAxis} (NaN if none; 2026-10-05). */
	public double nextPageEndAfter(final double pageAxis) {
		for (final FloatExclusion exclusion : this.ascendingByPageEnd) {
			if (exclusion.pageSpan().end() > pageAxis) {
				return exclusion.pageSpan().end();
			}
		}
		return Double.NaN;
	}

	/** Latest end among floats whose bands contain {@code pageAxis} (NaN if none; 2026-10-05). */
	public double bandEndAt(final double pageAxis) {
		double end = Double.NaN;
		for (final FloatExclusion exclusion : this.ascendingByPageEnd) {
			if (exclusion.pageSpan().end() > pageAxis && exclusion.pageSpan().start() <= pageAxis) {
				end = Double.isNaN(end) ? exclusion.pageSpan().end() : Math.max(end, exclusion.pageSpan().end());
			}
		}
		return end;
	}

	/**
	 * Narrows {@code lineBand} by bands occupied by floats, using the same rules as multicol avoidance in {@code
	 * BlockBuilder.startFlowBlock} (added 2026-07-23 for P0 Step3 shadow comparison; a one-to-one port of the existing
	 * `BlockBuilder` loop). To preserve behavior, comparison operators and scan order must match the existing loop
	 * exactly.
	 *
	 * <p>
	 * Stop when reaching {@code pageAxis} or earlier (the float bottom is at or before the page-axis start), just like
	 * the existing loop's {@code break}.
	 * </p>
	 */
	public AxisSpan narrowLineBandForMulticol(final double pageAxis, final AxisSpan lineBand) {
		double lineStart = lineBand.start();
		double lineEnd = lineBand.end();
		for (int i = this.ascendingByPageEnd.size() - 1; i >= 0; --i) {
			final FloatExclusion exclusion = this.ascendingByPageEnd.get(i);
			if (exclusion.pageSpan().end() <= pageAxis) {
				break;
			}
			switch (exclusion.side()) {
			case START:
				lineStart = Math.max(lineStart, exclusion.lineSpan().end());
				break;
			case END:
				lineEnd = Math.min(lineEnd, exclusion.lineSpan().start());
				break;
			}
		}
		return new AxisSpan(lineStart, lineEnd);
	}

	/**
	 * Finds the float targeted by clear using the same rules as clear processing in {@code
	 * BlockBuilder.startFlowBlock} (added 2026-07-23 for P0 Step3 shadow comparison; a one-to-one port of the existing
	 * loop).
	 *
	 * <p>
	 * Compare {@code pageEnd} relative to {@code marginStart} by subtracting it, as in existing code (arithmetically
	 * equivalent to adding it back to {@code pageStart}, but preserve the same subtraction order and site to reproduce
	 * floating-point rounding exactly). Return only the first match in descending order. Once {@code pageEnd <=
	 * pageStart} (a float before the current page is reached), stop searching and return {@code null} as no match.
	 * </p>
	 */
	public FloatExclusion findClearBoundary(final double pageStart, final double marginStart, final ClearMode clear) {
		for (int i = this.ascendingByPageEnd.size() - 1; i >= 0; --i) {
			final FloatExclusion exclusion = this.ascendingByPageEnd.get(i);
			final double pageEnd = exclusion.pageSpan().end() - marginStart;
			if (pageEnd <= pageStart) {
				return null;
			}
			switch (clear) {
			case START:
				if (exclusion.side() == FloatSide.START) {
					return exclusion;
				}
				break;
			case END:
				if (exclusion.side() == FloatSide.END) {
					return exclusion;
				}
				break;
			case BOTH:
				return exclusion;
			default:
				throw new IllegalStateException();
			}
		}
		return null;
	}

	/**
	 * Reproduces the rules of {@code BlockBuilder.addBound} (replaced elements and tables avoiding floats in flow)
	 * (added 2026-07-23 for P0 Step3 shadow comparison; a one-to-one port of the existing loop).
	 *
	 * <p>
	 * Preserves the existing structure that applies two independent rules in one scan: (1) find the boundary float
	 * according to {@code clear} (stop immediately if found); (2) otherwise narrow the line band with START/END floats
	 * (on START, reset {@code xMarginStart} to 0 and stop, faithfully reproducing the asymmetric existing behavior
	 * described in the original code comment).
	 * </p>
	 */
	public BoundAvoidance findBoundAvoidance(final double pageStart, final double lineSize, final double lineStop,
			final double marginAdjust, final ClearMode clear) {
		double xMarginStart = 0, lineEnd = lineStop;
		for (int i = this.ascendingByPageEnd.size() - 1; i >= 0; --i) {
			final FloatExclusion exclusion = this.ascendingByPageEnd.get(i);
			final double pageEnd = exclusion.pageSpan().end() - marginAdjust;
			if (pageStart >= pageEnd) {
				return new BoundAvoidance(null, 0, xMarginStart, lineEnd);
			}
			switch (clear) {
			case NONE:
				break;
			case START:
				if (exclusion.side() == FloatSide.START) {
					return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
				}
				break;
			case END:
				if (exclusion.side() == FloatSide.END) {
					return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
				}
				break;
			case BOTH:
				return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
			default:
				throw new IllegalStateException();
			}
			switch (exclusion.side()) {
			case START:
				// Check whether it fits beside the float symmetrically with END (2026-08-10). Previously,
				// start-side floats unconditionally triggered clearing, always moving content down;
				// tables dropped to the float bottom even with enough width (cocoon.apache.org's
				// roughly 630 pt left navigation plus a width:100% table moved the whole body to page 2).
				// Chrome places them together at the top if the float right edge (178 px) and table left edge
				// (193 px) do not interfere (observed). If they do not fit, move down as before.
				if (LayoutUtils.compare(lineEnd - exclusion.lineSpan().end(), lineSize) < 0) {
					return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
				}
				xMarginStart = Math.max(xMarginStart, exclusion.lineSpan().end());
				break;
			case END:
				if (LayoutUtils.compare(exclusion.lineSpan().start() - xMarginStart, lineSize) < 0) {
					lineEnd = lineStop;
					return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
				}
				lineEnd = Math.min(lineEnd, exclusion.lineSpan().start());
				break;
			default:
				throw new IllegalStateException();
			}
		}
		return new BoundAvoidance(null, 0, xMarginStart, lineEnd);
	}

	/**
	 * Result of {@link #findBoundAvoidance} (added 2026-07-23). Non-null {@code clearingExclusion} is the float
	 * defining the clear boundary, and {@code clearPageEnd} is its margin-adjusted pageEnd. If {@code
	 * clearingExclusion} is null, no clear-induced boundary movement occurs; {@code xMarginStart}/{@code lineEnd} are
	 * the narrowing result to use directly.
	 */
	public record BoundAvoidance(FloatExclusion clearingExclusion, double clearPageEnd, double xMarginStart,
			double lineEnd) {
	}

	/**
	 * Reproduces one line-band scan of {@code TextBuilder.locateLine} (added 2026-07-23 for P0 Step3 shadow
	 * comparison; a one-to-one port of the existing loop). Unlike the other three consumers, scans in {@code
	 * ascendingByPageEnd} order, matching the existing {@code for (i = 0; i <
	 * floatings.size(); ++i)}.
	 *
	 * <p>
	 * Of the four queries, only this one inspects {@code shape-outside} shapes ({@link FloatExclusion#lineSpanAt})
	 * (2026-08-29). css-shapes-1 §4.1 limits shape effects to wrapping inline content; float placement and avoidance
	 * by BFC-establishing blocks keep using margin boxes (as does Chrome), so the other three queries continue reading
	 * rectangular {@code lineSpan}.
	 * </p>
	 */
	public LineScan scanLineBand(final double pageStart, final double lineHeight, final double lineStart0,
			final double lineEnd0) {
		FloatExclusion startExclusion = null, endExclusion = null;
		double lineStart = lineStart0;
		double lineEnd = lineEnd0;
		boolean maxPageSizeSet = false;
		double maxPageSize = 0;
		for (final FloatExclusion exclusion : this.ascendingByPageEnd) {
			if (LayoutUtils.compare(pageStart, exclusion.pageSpan().end()) >= 0) {
				continue;
			}
			if (LayoutUtils.compare(exclusion.pageSpan().start(), pageStart + lineHeight) >= 0) {
				maxPageSizeSet = true;
				maxPageSize = exclusion.pageSpan().start() - pageStart;
				break;
			}
			// shape-outside (2026-08-29): extent occupied by the shape over the band's full line height.
			// Without a shape, use the margin box as before. A float whose shape does not intersect
			// the band does not narrow this line (lines may enter empty space above/below a circle).
			final AxisSpan band = exclusion.lineSpanAt(pageStart, pageStart + lineHeight);
			if (band == null) {
				continue;
			}
			switch (exclusion.side()) {
			case START:
				final double tempStart = band.end();
				if (LayoutUtils.compare(tempStart, lineStart) >= 0) {
					startExclusion = exclusion;
					lineStart = tempStart;
				}
				continue;
			case END:
				final double tempEnd = band.start();
				if (LayoutUtils.compare(tempEnd, lineEnd) <= 0) {
					endExclusion = exclusion;
					lineEnd = tempEnd;
				}
				continue;
			default:
				throw new IllegalStateException();
			}
		}
		return new LineScan(startExclusion, endExclusion, lineStart, lineEnd, maxPageSizeSet, maxPageSize);
	}

	/**
	 * Scans all entries for page floats without stopping at exclusions that start in the future.
	 *
	 * <p>
	 * Ordinary floats obey the invariant that registered exclusions start at or before the current position, so {@link
	 * #scanLineBand} stops at the first future start. Bottom floats placed at page end start in the future when
	 * registered, so separate only their set into this scan. Leave ordinary-float scan order, comparisons, and early
	 * exits unchanged.
	 * </p>
	 */
	public LineScan scanLineBandFully(final double pageStart, final double lineHeight, final double lineStart0,
			final double lineEnd0) {
		FloatExclusion startExclusion = null, endExclusion = null;
		double lineStart = lineStart0;
		double lineEnd = lineEnd0;
		boolean maxPageSizeSet = false;
		double maxPageSize = 0;
		for (final FloatExclusion exclusion : this.ascendingByPageEnd) {
			if (LayoutUtils.compare(pageStart, exclusion.pageSpan().end()) >= 0) {
				continue;
			}
			if (LayoutUtils.compare(exclusion.pageSpan().start(), pageStart + lineHeight) >= 0) {
				final double candidate = exclusion.pageSpan().start() - pageStart;
				if (!maxPageSizeSet || LayoutUtils.compare(candidate, maxPageSize) < 0) {
					maxPageSizeSet = true;
					maxPageSize = candidate;
				}
				continue;
			}
			final AxisSpan band = exclusion.lineSpanAt(pageStart, pageStart + lineHeight);
			if (band == null) {
				continue;
			}
			switch (exclusion.side()) {
			case START:
				final double tempStart = band.end();
				if (LayoutUtils.compare(tempStart, lineStart) >= 0) {
					startExclusion = exclusion;
					lineStart = tempStart;
				}
				continue;
			case END:
				final double tempEnd = band.start();
				if (LayoutUtils.compare(tempEnd, lineEnd) <= 0) {
					endExclusion = exclusion;
					lineEnd = tempEnd;
				}
				continue;
			default:
				throw new IllegalStateException();
			}
		}
		return new LineScan(startExclusion, endExclusion, lineStart, lineEnd, maxPageSizeSet, maxPageSize);
	}

	/**
	 * Result of {@link #scanLineBand} (added 2026-07-23). If {@code maxPageSizeSet} is false, the caller must not
	 * update the equivalent of {@code TextBuilder.maxPageSize}. Existing code updates only when the scan reaches that
	 * branch; otherwise, it carries the previous outer retry-loop iteration's value forward unchanged.
	 */
	public record LineScan(FloatExclusion startExclusion, FloatExclusion endExclusion, double lineStart,
			double lineEnd, boolean maxPageSizeSet, double maxPageSize) {
	}

	/**
	 * Reproduces the rules of {@code BlockBuilder.addStartFloat}/{@code addEndFloat} for finding new float placement
	 * (added 2026-07-23, the last P0 Step3 consumer; a one-to-one port of the existing loop). Those methods duplicate
	 * exactly the same algorithm, so this query can also be shared.
	 *
	 * <p>
	 * On a clear boundary, update only {@code pageStart} and return immediately, preserving the current {@code
	 * startExclusion}/{@code endExclusion}/{@code lineStart}/{@code lineEnd}.
	 * </p>
	 */
	public FloatPlacementScan scanFloatPlacementBand(final double pageStartIn, final double lineStart0,
			final double lineEnd0, final ClearMode clear) {
		double pageStart = pageStartIn;
		FloatExclusion startExclusion = null, endExclusion = null;
		double lineStart = lineStart0, lineEnd = lineEnd0;
		for (int i = this.ascendingByPageEnd.size() - 1; i >= 0; --i) {
			final FloatExclusion exclusion = this.ascendingByPageEnd.get(i);
			final double pageEnd = exclusion.pageSpan().end();
			if (LayoutUtils.compare(pageStart, pageEnd) >= 0) {
				break;
			}
			switch (clear) {
			case NONE:
				break;
			case START:
				if (exclusion.side() == FloatSide.START) {
					pageStart = pageEnd;
					return new FloatPlacementScan(startExclusion, endExclusion, lineStart, lineEnd, pageStart);
				}
				break;
			case END:
				if (exclusion.side() == FloatSide.END) {
					pageStart = pageEnd;
					return new FloatPlacementScan(startExclusion, endExclusion, lineStart, lineEnd, pageStart);
				}
				break;
			case BOTH:
				pageStart = pageEnd;
				return new FloatPlacementScan(startExclusion, endExclusion, lineStart, lineEnd, pageStart);
			default:
				throw new IllegalStateException();
			}
			switch (exclusion.side()) {
			case START:
				final double tempStart = exclusion.lineSpan().end();
				if (LayoutUtils.compare(tempStart, lineStart) >= 0) {
					startExclusion = exclusion;
					lineStart = tempStart;
				}
				continue;
			case END:
				final double tempEnd = exclusion.lineSpan().start();
				if (LayoutUtils.compare(tempEnd, lineEnd) <= 0) {
					endExclusion = exclusion;
					lineEnd = tempEnd;
				}
				continue;
			default:
				throw new IllegalStateException();
			}
		}
		return new FloatPlacementScan(startExclusion, endExclusion, lineStart, lineEnd, pageStart);
	}

	/**
	 * Result of {@link #scanFloatPlacementBand} (added 2026-07-23). {@code pageStart} is the value updated when the
	 * clear condition matches; the caller always adopts it.
	 */
	public record FloatPlacementScan(FloatExclusion startExclusion, FloatExclusion endExclusion, double lineStart,
			double lineEnd, double pageStart) {
	}
}
