package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Resolves line-axis sizes, insets, and margins of absolutely positioned boxes.
 * Implements SPEC CSS2.1 10.3.7 (the constraint start + margin + size + margin + end = available)
 * as a pure function without touching boxes. Represents auto with {@link LayoutUtils#NONE}.
 * Resolves again if max/min clamping changes the size (as in the old implementation's state loop).
 *
 * @author MIYABE Tatsuhiko
 */
public final class AbsoluteSizing {
	/**
	 * Input. Represents auto with NONE.
	 *
	 * @param available       Containing block's line-axis size (including padding)
	 * @param size            Specified size (adjusted for box-sizing; auto=NONE)
	 * @param maxSize         Maximum size (none=NONE)
	 * @param minSize         Minimum size (resolved value, equivalent to 0 when absent)
	 * @param insetStart      Line-start inset (auto=NONE)
	 * @param insetEnd        Line-end inset (auto=NONE)
	 * @param marginStart     Resolved start margin
	 * @param marginEnd       Resolved end margin
	 * @param marginStartAuto Whether the start margin is specified as auto
	 * @param marginEndAuto   Whether the end margin is specified as auto
	 * @param frameExtent     Total line-axis frame extent (margin+border+padding)
	 * @param minContent      Min-content size
	 * @param maxContent      Max-content size
	 */
	public record Input(double available, double size, double maxSize, double minSize, double insetStart,
			double insetEnd, double marginStart, double marginEnd, boolean marginStartAuto, boolean marginEndAuto,
			double frameExtent, double minContent, double maxContent) {
	}

	/**
	 * Resolved result.
	 *
	 * @param size        Line-axis size
	 * @param insetStart  Start inset (definite value)
	 * @param insetEnd    End inset (definite value)
	 * @param marginStart Start margin (NONE if auto remains unresolved)
	 * @param marginEnd   End margin (NONE if auto remains unresolved)
	 */
	public record Result(double size, double insetStart, double insetEnd, double marginStart, double marginEnd) {
	}

	private AbsoluteSizing() {
		// utility
	}

	/**
	 * Page-axis input (equivalent to CSS2.1 10.6.4). Represents auto with NONE.
	 * Unlike the line axis ({@link Input}), this does not involve shrink-to-fit (min/max-content);
	 * it uses the actual content size (contentSize) directly.
	 *
	 * @param available   Containing block's page-axis size (including padding)
	 * @param size        Specified size (auto=NONE; the caller adjusts for box-sizing later)
	 * @param maxSize     Maximum size (none=NONE)
	 * @param minSize     Minimum size (resolved value)
	 * @param insetStart  Page-start inset (horizontal writing=top / vertical writing=left; auto=NONE)
	 * @param insetEnd    Page-end inset (horizontal writing=bottom / vertical writing=right; auto=NONE)
	 * @param marginStart Start margin (auto=NONE)
	 * @param marginEnd   End margin (auto=NONE)
	 * @param contentSize Actual page-axis content size
	 * @param frameExtent Total page-axis border+padding extent
	 */
	public record PageInput(double available, double size, double maxSize, double minSize, double insetStart,
			double insetEnd, double marginStart, double marginEnd, double contentSize, double frameExtent) {
	}

	/**
	 * Page-axis result.
	 *
	 * @param size        Page-axis size
	 * @param insetStart  Start inset (definite value)
	 * @param marginStart Start margin (definite value)
	 * @param marginEnd   End margin (definite value)
	 */
	public record PageResult(double size, double insetStart, double marginStart, double marginEnd) {
	}

	/**
	 * Resolves page-axis sizes, insets, and margins
	 * (consolidates the two mirrored vertical/horizontal sections of about 100 lines each in the old
	 * AbsoluteBlockBox.finishLayout; a faithful port, preserving the dangling-else "over-constrained" behavior).
	 *
	 * @param in Input
	 * @return Resolved result
	 */
	public static PageResult resolvePage(final PageInput in) {
		double size = in.size();
		double start = 0;
		double marginStart = 0, marginEnd = 0;
		for (int state = 0; state < 2; ++state) {
			marginStart = in.marginStart();
			marginEnd = in.marginEnd();
			start = in.insetStart();
			double end = in.insetEnd();
			if (!LayoutUtils.isNone(start) && !LayoutUtils.isNone(end) && !LayoutUtils.isNone(size)) {
				// Over-specified: absorb through auto margins.
				if (LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
					marginStart = marginEnd = (in.available() - start - end - size - in.frameExtent()) / 2.0;
				}
				if (LayoutUtils.isNone(marginStart) && !LayoutUtils.isNone(marginEnd)) {
					marginStart = in.available() - start - end - size - marginEnd - in.frameExtent();
				}
				if (!LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
					marginEnd = in.available() - start - end - size - marginStart - in.frameExtent();
				} else {
					// Over-constrained (preserves the dangling-else behavior of the old implementation).
					end = 0;
				}
			} else {
				if (LayoutUtils.isNone(marginStart)) {
					marginStart = 0;
				}
				if (LayoutUtils.isNone(marginEnd)) {
					marginEnd = 0;
				}
				if (LayoutUtils.isNone(size)) {
					if (LayoutUtils.isNone(start) && LayoutUtils.isNone(end)) {
						start = 0;
						size = in.contentSize();
					} else if (LayoutUtils.isNone(start)) {
						size = in.contentSize();
						start = in.available() - end - size - marginStart - marginEnd - in.frameExtent();
					} else if (LayoutUtils.isNone(end)) {
						size = in.contentSize();
						end = in.available() - start - size - marginStart - marginEnd - in.frameExtent();
					} else {
						size = in.available() - start - end - marginStart - marginEnd - in.frameExtent();
					}
				} else {
					if (LayoutUtils.isNone(end)) {
						if (LayoutUtils.isNone(start)) {
							start = 0;
						}
						end = in.available() - start - size - marginStart - marginEnd - in.frameExtent();
					} else {
						start = in.available() - end - size - marginStart - marginEnd - in.frameExtent();
					}
				}
			}
			switch (state) {
			case 0:
				if (!LayoutUtils.isNone(in.maxSize()) && size > in.maxSize()) {
					size = in.maxSize();
					continue;
				}
				state = 1;
			case 1:
				if (size < in.minSize()) {
					size = in.minSize();
					continue;
				}
				state = 2;
				break;
			}
		}
		return new PageResult(size, start, marginStart, marginEnd);
	}

	/**
	 * Resolves line-axis sizes, insets, and margins.
	 *
	 * @param in Input
	 * @return Resolved result
	 */
	public static Result resolve(final Input in) {
		double size = in.size();
		double start = 0, end = 0;
		double marginStart = 0, marginEnd = 0;
		for (int state = 0; state < 2; ++state) {
			start = in.insetStart();
			end = in.insetEnd();
			if (!LayoutUtils.isNone(start) && !LayoutUtils.isNone(end) && !LayoutUtils.isNone(size)) {
				// Over-specified: absorb through auto margins.
				marginStart = in.marginStartAuto() ? LayoutUtils.NONE : in.marginStart();
				marginEnd = in.marginEndAuto() ? LayoutUtils.NONE : in.marginEnd();
				if (LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
					marginStart = marginEnd = (in.available() - start - end - size - in.frameExtent()) / 2.0;
				}
				if (LayoutUtils.isNone(marginStart) && !LayoutUtils.isNone(marginEnd)) {
					marginStart = in.available() - start - end - size - in.frameExtent();
				}
				if (!LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
					marginEnd = in.available() - start - end - size - in.frameExtent();
				} else {
					// Over-constrained (preserves the dangling-else behavior of the old implementation).
					end = 0;
				}
			} else {
				marginStart = in.marginStart();
				marginEnd = in.marginEnd();
				if (LayoutUtils.isNone(size)) {
					if (!LayoutUtils.isNone(start) && !LayoutUtils.isNone(end)) {
						size = in.available() - start - end - in.frameExtent();
					} else {
						size = in.maxContent();
						final double limit = in.available() - in.frameExtent();
						if (LayoutUtils.isNone(start) && LayoutUtils.isNone(end)) {
							size = Sizing.fitContent(in.minContent(), size, limit);
							start = end = 0;
						} else if (LayoutUtils.isNone(start)) {
							// Ledger #2 resolved (2026-07-17): removed the old vertical-writing variant,
							// fitContent(minContent - inset, …, limit), and standardized on
							// the 10.3.7 form, fitContent(minContent, …, limit - inset).
							size = Sizing.fitContent(in.minContent(), size, limit - end);
							start = in.available() - end - size - in.frameExtent();
						} else {
							size = Sizing.fitContent(in.minContent(), size, limit - start);
							end = in.available() - start - size - in.frameExtent();
						}
					}
				} else {
					if (LayoutUtils.isNone(end)) {
						if (LayoutUtils.isNone(start)) {
							start = 0;
						}
						end = in.available() - start - size - in.frameExtent();
					} else {
						start = in.available() - end - size - in.frameExtent();
					}
				}
			}
			switch (state) {
			case 0:
				if (!LayoutUtils.isNone(in.maxSize()) && size > in.maxSize()) {
					size = in.maxSize();
					continue;
				}
				state = 1;
			case 1:
				if (size < in.minSize()) {
					size = in.minSize();
					continue;
				}
				state = 2;
				break;
			}
		}
		return new Result(size, start, end, marginStart, marginEnd);
	}
}
