package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Determines table cuts in the page direction (C4-T1; applies the same pure-decision approach as
 * FlowCutter/LineCutter to tables).
 *
 * <p>
 * Extracts the decisions formerly made by the TableBox / TableRowGroupBox cut loops
 * (header/footer reservation, break avoidance between groups/rows, mixed vertical/horizontal writing,
 * keep-all/move-all) into pure functions detached from the box tree.
 * The loops themselves (traversal depending on child split results) remain on the box side,
 * analogous to the division between FlowContainer and FlowCutter.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TableCutter {
	private TableCutter() {
		// utility
	}

	/**
	 * Keeps everything (KEEP) at the page start; otherwise moves everything (MOVE).
	 * The common fallback for empty tables, headers/footers that do not fit, or failure to find a cut position.
	 */
	public static SplitResult keepOrMoveAll(final byte flags) {
		return (flags & IPageBreakableBox.FLAGS_FIRST) != 0 ? SplitResult.KEEP : SplitResult.MOVE;
	}

	/**
	 * Extracts a remainder of the expected type from a child split result that should be {@code Split} .
	 * Reports contract violations as {@link ContinuationInvariantViolationException}
	 * (2026-07-25; previously a raw cast exposed contract violations as an uninformative
	 * {@code ClassCastException} . Aligned with {@code TableRowBox.forcedCellRemainder} ;
	 * normal-path logic is unchanged).
	 *
	 * @param <T> expected remainder type
	 * @param result child split result
	 * @param type expected remainder type
	 * @param context caller description included in the exception message
	 * @return the remainder
	 */
	public static <T extends IPageBreakableBox> T requireSplitRemainder(final SplitResult result, final Class<T> type,
			final String context) {
		if (result instanceof SplitResult.Split(final IPageBreakableBox remainder) && type.isInstance(remainder)) {
			return type.cast(remainder);
		}
		throw new ContinuationInvariantViolationException(
				context + " must return Split(" + type.getSimpleName() + ") but returned " + result);
	}

	/**
	 * Subtracts nonbreaking portions such as headers/footers from the table's cut line.
	 *
	 * @param pageLimit cut line measured from the table's outer edge
	 * @param boxPageExtent table's outer extent in the page direction
	 * @param framePageStart frame width on the start side of the page direction
	 * @param framePageEnd frame width on the end side of the page direction
	 * @param marginPageEnd margin width on the end side of the page direction
	 * @param headerSize header row group's page extent (negative if absent)
	 * @param footerSize footer row group's page extent (negative if absent)
	 * @return the cut line available to body row groups
	 */
	public static double reserveNonBreakable(double pageLimit, final double boxPageExtent,
			final double framePageStart, final double framePageEnd, final double marginPageEnd,
			final double headerSize, final double footerSize) {
		final double over = boxPageExtent - pageLimit;
		pageLimit -= framePageStart;
		if (headerSize >= 0) {
			pageLimit -= headerSize;
		}
		if (footerSize >= 0) {
			pageLimit -= footerSize;
			pageLimit -= framePageEnd;
		} else if (over > 0 && LayoutUtils.compare(over, marginPageEnd) < 0) {
			// Cut when the boundary reaches the bottom margin.
			pageLimit -= marginPageEnd;
		}
		return pageLimit;
	}

	/**
	 * For incomplete tables, subtracts only the start frame and header, without reserving the trailing margin
	 * or end frame.
	 */
	public static double reserveIncompleteNonBreakable(double pageLimit, final double framePageStart,
			final double headerSize) {
		pageLimit -= framePageStart;
		if (headerSize >= 0) {
			pageLimit -= headerSize;
		}
		return pageLimit;
	}

	/**
	 * Break avoidance at row-group boundaries. Checks the preceding group's break-after and the current
	 * group's break-before, plus rows adjoining the boundary (the preceding group's last-row break-after
	 * and the current group's first-row break-before).
	 *
	 * @param beforeGroupBreakAfter preceding group's page-break-after
	 * @param groupBreakBefore current group's page-break-before
	 * @param beforeGroupLastRowAfter preceding group's last-row page-break-after (AUTO if no row)
	 * @param groupFirstRowBefore current group's first-row page-break-before (AUTO if no row)
	 */
	public static boolean groupBreakAvoid(final PageBreakMode beforeGroupBreakAfter, final PageBreakMode groupBreakBefore,
			final PageBreakMode beforeGroupLastRowAfter, final PageBreakMode groupFirstRowBefore) {
		return beforeGroupBreakAfter == PageBreakMode.AVOID || groupBreakBefore == PageBreakMode.AVOID
				|| beforeGroupLastRowAfter == PageBreakMode.AVOID || groupFirstRowBefore == PageBreakMode.AVOID;
	}

	/**
	 * Break avoidance at row boundaries. In addition to row break-after/before, checks avoidance imposed by
	 * row-spanning cells (rowspan) in the preceding row.
	 *
	 * <p>
	 * Normally, skips cuttable cells (page-break-inside:auto and matching writing direction), then prohibits
	 * a break if any cell extends into the next row.
	 * Only between rows 1 and 2 at the page start (i==1 and FLAGS_FIRST) is there an exception:
	 * only spanning cells with a different writing direction impose avoidance.
	 * A span with a matching direction clears avoidance while continuing the scan,
	 * faithfully preserving the old implementation's behavior.
	 * </p>
	 *
	 * @param i current row index
	 * @param pageFirst FLAGS_FIRST (page start)
	 * @param beforeRowBreakAfter preceding row's page-break-after
	 * @param rowBreakBefore current row's page-break-before
	 * @param beforeCellCuttable whether each cell in the preceding row is cuttable
	 * (inside==AUTO and matching writing direction)
	 * @param beforeCellExtended whether each cell in the preceding row extends into the next row
	 * @param beforeCellFlowMatch whether each cell in the preceding row matches the table's writing direction
	 */
	public static boolean rowBreakAvoid(final int i, final boolean pageFirst, final PageBreakMode beforeRowBreakAfter,
			final PageBreakMode rowBreakBefore, final boolean[] beforeCellCuttable, final boolean[] beforeCellExtended,
			final boolean[] beforeCellFlowMatch) {
		boolean breakAvoid = beforeRowBreakAfter == PageBreakMode.AVOID || rowBreakBefore == PageBreakMode.AVOID;
		if (!breakAvoid && (i != 1 || !pageFirst)) {
			// Break avoidance imposed by spanning cells. Boundaries crossed by rowspan act as avoid
			// (the specification in manual 4550). Only cuttable cells where the author explicitly declared
			// page-break-inside:auto (TableCellPos.breakInsideDeclaredAuto)
			// can opt out. Even after removal of the UA default cell avoid (2026-08-27),
			// rowspan blocks move together by default.
			for (int j = 0; j < beforeCellExtended.length; ++j) {
				if (beforeCellCuttable[j]) {
					continue;
				}
				if (beforeCellExtended[j]) {
					breakAvoid = true;
					break;
				}
			}
		} else if (i == 1 && pageFirst) {
			// Exception for spanning cells between rows 1 and 2 at the page start
			for (int j = 0; j < beforeCellExtended.length; ++j) {
				if (!beforeCellExtended[j]) {
					continue;
				}
				if (beforeCellFlowMatch[j]) {
					breakAvoid = false;
					continue;
				}
				// Always prohibit a break if writing directions differ.
				breakAvoid = true;
				break;
			}
		}
		return breakAvoid;
	}

	/**
	 * Keeps a row containing cells whose writing direction differs from the table before the break,
	 * without cutting or moving it (splitting mixed vertical/horizontal writing is unsupported).
	 *
	 * @param cellFlowMatch whether each cell in the current row matches the table's writing direction
	 */
	public static boolean mixedFlowKeep(final boolean[] cellFlowMatch) {
		for (final boolean match : cellFlowMatch) {
			if (!match) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Cell-fragment dimensions and frame state (C4-T2; corresponds to block {@link FragmentState} ,
	 * but calculates remaining minimum size differently:
	 * cells subtract from actual size; blocks subtract from min(specified, actual)).
	 *
	 * @param nextSize specified size of the continuation fragment
	 * @param nextMinSize minimum size of the continuation fragment
	 * @param nextFrame continuation fragment's frame (start side removed)
	 * @param prevFrame preceding fragment's frame (end side removed)
	 */
	public record CellFragmentState(net.zamasoft.foliojet.layout.box.params.Dimension nextSize,
			net.zamasoft.foliojet.layout.box.params.Dimension nextMinSize,
			net.zamasoft.foliojet.layout.part.AbsoluteRectFrame nextFrame,
			net.zamasoft.foliojet.layout.part.AbsoluteRectFrame prevFrame) {
	}

	/**
	 * Calculates cell-fragment state (pure function; unifies the roughly 30 lines × 2 of mirrored
	 * vertical/horizontal code in the former TableCellBox.splitPage).
	 *
	 * <p>
	 * In the old implementation, the Dimension.create argument order for vertical writing differed from
	 * horizontal writing and FragmentState (cross-axis specification in the width slot).
	 * It was normalized to the same convention as FragmentState (width = remaining page-direction extent).
	 * Actual size is governed by row splitting (setWidth/setHeight) and the column-width mechanism,
	 * so output is unchanged (confirmed identical by the vert-cell-specified-pagebreak cross-check;
	 * PLAN cycle 18).
	 * </p>
	 *
	 * @param vertical whether writing is vertical
	 * @param size specified size
	 * @param minSize minimum size
	 * @param frame frame before cutting
	 * @param pageExtent inner page-direction extent before cutting (width in vertical writing)
	 * @param pageLimit cut position (from the inner edge)
	 */
	public static CellFragmentState cellFragmentState(final boolean vertical,
			final net.zamasoft.foliojet.layout.box.params.Dimension size,
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize,
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame, final double pageExtent,
			final double pageLimit) {
		final net.zamasoft.foliojet.layout.box.params.Dimension nextSize, nextMinSize;
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame nextFrame, prevFrame;
		final double rest = Math.max(0, pageExtent - pageLimit);
		if (vertical) {
			nextSize = size.getWidthType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(rest, size.getHeight(),
							net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE, size.getHeightType())
					: size;
			nextMinSize = minSize.getWidthType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(rest, minSize.getHeight(),
							net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE, minSize.getHeightType())
					: minSize;
			nextFrame = frame.cut(true, false, true, true);
			prevFrame = frame.cut(true, true, true, false);
		} else {
			nextSize = size.getHeightType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(size.getWidth(), rest,
							size.getWidthType(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE)
					: size;
			nextMinSize = minSize.getHeightType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(minSize.getWidth(), rest,
							minSize.getWidthType(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE)
					: minSize;
			nextFrame = frame.cut(false, true, true, true);
			prevFrame = frame.cut(true, true, false, true);
		}
		return new CellFragmentState(nextSize, nextMinSize, nextFrame, prevFrame);
	}

	/**
	 * Table-fragment frames (C4-T2). Headers repeat in all fragments, so the continuation fragment also
	 * retains the start frame; likewise, footers make the preceding fragment retain the end frame.
	 *
	 * @param prevFrame preceding fragment's frame
	 * @param nextFrame continuation fragment's frame
	 */
	public record TableFragmentFrames(net.zamasoft.foliojet.layout.part.AbsoluteRectFrame prevFrame,
			net.zamasoft.foliojet.layout.part.AbsoluteRectFrame nextFrame) {
	}

	/**
	 * The effective frame of an incomplete table. The final remainder retains the original frame until
	 * complete().
	 * Tables with footers have a separate end-reservation contract and are not accepted as incomplete tables.
	 */
	public static net.zamasoft.foliojet.layout.part.AbsoluteRectFrame incompleteFrame(final boolean vertical,
			final boolean repeatFooter, final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame) {
		if (repeatFooter) {
			throw new IllegalStateException("Incomplete tables do not support a repeated footer");
		}
		return vertical ? frame.cut(true, true, true, false) : frame.cut(true, true, false, true);
	}

	/**
	 * Calculates table-fragment frames (pure function; the frame-cut decisions from the former splitTableBox).
	 *
	 * @param vertical whether writing is vertical
	 * @param repeatHeader whether a header row group exists (repeats in all fragments)
	 * @param repeatFooter whether a footer row group exists (repeats in all fragments)
	 * @param frame frame before cutting
	 */
	public static TableFragmentFrames tableFragmentFrames(final boolean vertical, final boolean repeatHeader,
			final boolean repeatFooter, final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame) {
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame nextFrame = repeatHeader ? frame
				: (vertical ? frame.cut(true, false, true, true) : frame.cut(false, true, true, true));
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame prevFrame = repeatFooter ? frame
				: (vertical ? frame.cut(true, true, true, false) : frame.cut(true, true, false, true));
		return new TableFragmentFrames(prevFrame, nextFrame);
	}

	/**
	 * Preliminary decision for a row cut (C4-T3; the start of the former TableRowBox.split).
	 * Either decides KEEP/MOVE or returns null to proceed to the main processing (cell splitting).
	 *
	 * @param pageFirst FLAGS_FIRST (page start)
	 * @param firstRow FLAGS_FIRST_ROW (first row or connected to it)
	 * @param pageLimit cut line (from the row's top edge)
	 * @param rowPageSize row's page extent
	 * @param rowInsideAvoid row's page-break-inside: avoid
	 * @param cellPageExtents each cell's page extent (may exceed the row due to rowspan)
	 * @param cellFlowMatch whether each cell matches the table's writing direction
	 * @param cellInsideAvoid each cell's page-break-inside: avoid
	 * @param cellCollapsedAtStart whether each cell has no top boundary and zero height (give up splitting)
	 * @param fragmentCapacity fragmentainer's (page/column) inner page-direction extent
	 * (-1 if unknown; {@code AutoBreakMode.fragmentCapacity} )
	 */
	public static SplitResult rowPreDecide(final boolean pageFirst, final boolean firstRow, final double pageLimit,
			final double rowPageSize, final boolean rowInsideAvoid, final double[] cellPageExtents,
			final boolean[] cellFlowMatch, final boolean[] cellInsideAvoid, final boolean[] cellCollapsedAtStart,
			final double fragmentCapacity) {
		if (!pageFirst) {
			// Not at the page start
			if (LayoutUtils.compare(pageLimit, 0) < 0) {
				// Below the cut line
				return SplitResult.MOVE;
			}
			if (LayoutUtils.compare(pageLimit, rowPageSize) >= 0) {
				// Above the cut line. Check every cell's height
				// to account for height contributed by spanning cells.
				boolean leave = true;
				for (final double extent : cellPageExtents) {
					if (LayoutUtils.compare(pageLimit, extent) < 0) {
						leave = false;
						break;
					}
				}
				if (leave) {
					// No move
					return SplitResult.KEEP;
				}
			}
			boolean breakAvoid = false;
			if (rowInsideAvoid) {
				// Break avoidance on the row
				breakAvoid = true;
			} else {
				for (int i = 0; i < cellFlowMatch.length; ++i) {
					// Prohibit page breaks if writing directions differ.
					if (!cellFlowMatch[i]) {
						return SplitResult.MOVE;
					}
					if (cellInsideAvoid[i]) {
						// Break avoidance on the cell
						breakAvoid = true;
					}
				}
			}
			if (breakAvoid && !firstRow) {
				return SplitResult.MOVE;
			}
			// Prefer cuts at row boundaries (2026-08-27, aligned with css-break/Chrome, paired with
			// removal of the UA default cell avoid). If the row intersected by the cut line fits
			// entirely in a fresh fragmentainer, move it whole instead of cutting inside it. Rows
			// that do not fit whole (such as a huge row in a single-row wrapper table) would need
			// an internal cut anyway and leave a large blank space behind, so cut them in place.
			if (!firstRow && fragmentCapacity > 0
					&& LayoutUtils.compare(rowPageSize, fragmentCapacity) <= 0) {
				return SplitResult.MOVE;
			}
			return null;
		}
		// At the page start
		if (LayoutUtils.compare(pageLimit, rowPageSize) >= 0) {
			// Even if a rowspan overflows, later processing cuts it.
			return SplitResult.KEEP;
		}
		// Do not move if writing directions differ.
		if (mixedFlowKeep(cellFlowMatch)) {
			return SplitResult.KEEP;
		}
		// Give up splitting if any cell has no top boundary and zero height.
		for (final boolean collapsed : cellCollapsedAtStart) {
			if (collapsed) {
				return SplitResult.KEEP;
			}
		}
		return null;
	}

	/**
	 * The position of an explicit forced page break between rows (C4-T3).
	 *
	 * @param rowGroup index of the body row group immediately before the cut
	 * @param row index of the row immediately before the cut (-1 at the group end)
	 * @param breakMode specified break kind (PAGE / COLUMN)
	 */
	public record ForceBreakAt(int rowGroup, int row, PageBreakMode breakMode) {
	}

	/**
	 * Finds the first explicit forced break in an automatic table
	 * (row/row-group page-break-before/after: page|column) occurring up to the cut line
	 * (pure function; the scan from the former BreakableBuilder.firstTableForceBreak).
	 * Stops on reaching a row beyond the cut line (automatic page breaking takes over from there).
	 *
	 * @param pageLimit cut line
	 * @param last page position of the start of the body row groups
	 * (adjusted from the table position for headers, footers, etc.)
	 * @param rowSizes page extent of each row in each group
	 * @param groupBreakBefore each group's page-break-before
	 * @param groupBreakAfter each group's page-break-after
	 * @param rowBreakBefore each row's page-break-before in each group
	 * @param rowBreakAfter each row's page-break-after in each group
	 * @return the first forced break, or null if none occurs up to the cut line
	 */
	public static ForceBreakAt firstForceBreak(final double pageLimit, double last, final double[][] rowSizes,
			final PageBreakMode[] groupBreakBefore, final PageBreakMode[] groupBreakAfter,
			final PageBreakMode[][] rowBreakBefore, final PageBreakMode[][] rowBreakAfter) {
		final int groupCount = rowSizes.length;
		PageBreakMode breakMode;
		for (int rowGroup = 0; rowGroup < groupCount; ++rowGroup) {
			final int rowCount = rowSizes[rowGroup].length;
			if (rowGroup > 0) {
				breakMode = groupBreakBefore[rowGroup];
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					// Break immediately before a row group
					return new ForceBreakAt(rowGroup - 1, -1, breakMode);
				}
			}
			for (int row = 0; row < rowCount; ++row) {
				last += rowSizes[rowGroup][row];
				if (LayoutUtils.compare(last, pageLimit) > 0) {
					// Past the cut line: automatic page breaking takes over from here.
					return null;
				}
				if (rowGroup > 0 || row > 0) {
					breakMode = rowBreakBefore[rowGroup][row];
					if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
						// Break immediately before a row
						return row - 1 >= 0 ? new ForceBreakAt(rowGroup, row - 1, breakMode)
								: new ForceBreakAt(rowGroup - 1, -1, breakMode);
					}
				}
				if (rowGroup == groupCount - 1 && row == rowCount - 1) {
					// Exit the loop at the end.
					break;
				}
				breakMode = rowBreakAfter[rowGroup][row];
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					// Break immediately after a row
					return row < rowCount - 1 ? new ForceBreakAt(rowGroup, row, breakMode)
							: new ForceBreakAt(rowGroup, -1, breakMode);
				}
			}
			if (rowGroup < groupCount - 1) {
				breakMode = groupBreakAfter[rowGroup];
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					// Break immediately after a row group
					return new ForceBreakAt(rowGroup, -1, breakMode);
				}
			}
		}
		return null;
	}

	/**
	 * Calculates row flags at the page start. Sets FLAGS_FIRST_ROW for the first row and for rows sharing
	 * a cell with it (connected by rowspan); clears FLAGS_FIRST from the second row onward.
	 *
	 * @param xflags flags already masked with FLAGS_FIRST/FLAGS_SPLIT
	 * @param i current row index
	 * @param linkedToTop whether the current row shares a cell with the first row
	 */
	public static byte firstRowFlags(byte xflags, final int i, final boolean linkedToTop) {
		if ((xflags & IPageBreakableBox.FLAGS_FIRST) == 0) {
			return xflags;
		}
		if (i > 0) {
			xflags ^= IPageBreakableBox.FLAGS_FIRST;
			if (linkedToTop) {
				xflags |= IPageBreakableBox.FLAGS_FIRST_ROW;
			}
		} else {
			xflags |= IPageBreakableBox.FLAGS_FIRST_ROW;
		}
		return xflags;
	}
}
