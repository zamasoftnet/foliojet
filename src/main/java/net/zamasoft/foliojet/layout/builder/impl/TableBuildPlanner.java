package net.zamasoft.foliojet.layout.builder.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.AbstractBlockLevelPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * Determines the table execution plan (Incremental/Retained) at a single decision point (C4-B, 2026-07-19).
 * Replaces the single boolean from the old {@code LayoutUtils.needsIntrinsicSizing(TableBox)}, which collapsed four
 * conditions (auto column widths, non-FLOW positioning, specified page-axis size, and auto line-axis size), with a
 * type that tracks each reason. The conditions themselves are unchanged from the old implementation (behavior
 * unchanged).
 *
 * @author MIYABE Tatsuhiko
 */
public final class TableBuildPlanner {
	private TableBuildPlanner() {
	}

	/** Reasons to return row emission to the existing assemble path, decidable only after Pass B. */
	public enum RowEmissionExclusion {
		/** processing.table-row-emission is opt-in, false by default. */
		DISABLED,
		/** Outside MAIN, the order of measurement, repositioning, and page side effects cannot change. */
		NOT_MAIN,
		/** Tables with unstable row-height application in Pass C cannot use fixed h[]. */
		PASS_C_INELIGIBLE,
		/** Requires an accepting host in normal flow with MODE_PAGE_BREAK, breakDepth=-1, and no repositioning. */
		UNSUPPORTED_HOST,
		/** Cuts and frame accounting for vertical writing or an axis orthogonal to the parent are unverified in stage 1. */
		WRITING_MODE,
		/** Multiple body groups differ in avoid backtracking to the preceding group and the group subtraction order. */
		BODY_GROUPS,
		/** For rowspan, binding and break-prohibition units differ, and transferred cells also need cutting. */
		ROWSPAN,
		/** Repeated footers reserve end frames on every fragment. */
		FOOTER,
		/**
		 * Keep top captions excluded. At a nonzero start, rounding differences between the parent's overflow check and
		 * the local cut line can cause only the incomplete path to KEEP the visible group, then MOVE the entire
		 * nonleading table. This is not caption-specific; preceding content alone can trigger it, so hold the visible
		 * range pending for all emission. Bottom-only captions use the common path after complete and retained.close,
		 * then close the wrapper last.
		 */
		CAPTION,
		/** Fragment ownership of the full row-boundary array for collapse has not yet been separated. */
		COLLAPSED_BORDERS,
		/**
		 * Specified heights, including ABSOLUTE, return to the completed path (except default min=0). A numerical shadow
		 * of distributed h[] alone cannot guarantee parent fragment dimensions. In an actual fixture combining group and
		 * table heights, accounting from completed-table placement versus incomplete-table dimension updates through
		 * wrapper end differed, changing ancestor frame heights and subsequent body-text D7 coordinates.
		 */
		GROUP_PAGE_SIZE,
		/**
		 * Cell repositioning changes retention and remaining height in intra-row splitting; original h[] cannot
		 * reproduce them.
		 */
		ROW_SPLITTING,
		/**
		 * Orthogonal cells turn row MOVE into group KEEP and preserve KEEP even at page start. ROW_SPLITTING exclusion
		 * alone cannot guarantee equivalence of this branch. Independent shadow and actual Root/D7 validation have not
		 * passed yet, so B-3-1 keeps this exclusion.
		 */
		ORTHOGONAL_CELL,
		/** A negative end margin can undo splits made before the final append. */
		NEGATIVE_END_MARGIN,
		/**
		 * Interleaving bind and page breaks changes ledger registration timing for footnotes, page floats, and
		 * parallel notes.
		 */
		PAGE_SIDE_EFFECTS,
		/** Percentage sizes of replaced elements, inline blocks, tables, etc. may reference the current page during bind. */
		PAGE_DEPENDENT_CELL_CONTENT,
		/** Dimension restoration and repropagation are unverified for multi-column or specified-height/min/max ancestors. */
		COMPLEX_ANCESTOR,
		/** When float avoidance lowers initial placement, remaining capacity alone cannot rule out intra-row splitting. */
		FLOATING_HOST,
		/**
		 * Forced splitting of a completed table bypasses column splitting; synchronizing column heights alone cannot
		 * reproduce it.
		 */
		FORCED_BREAK_WITH_COLUMNS,
		/**
		 * For empty or zero-column tables, Retained handles specified dimensions on a path separate from normal row
		 * addition.
		 */
		EMPTY_TABLE
	}

	/**
	 * Inputs collected from the post-Pass-B shape and actual accepting host. A separate contract from plan() at the
	 * start.
	 * hasPageSideEffects covers footnotes, page floats, and parallel notes, including inside cells.
	 * complexAncestor covers multi-column layout and specified height/min/max in all ancestors up to the host.
	 * maySplitRows must be checked independently of rowspan presence.
	 * hostSupportsIntake is the result of checking the receiving mode, splitting capability, depth, and
	 * repositioning/text state.
	 */
	public record RowEmissionFacts(boolean main, boolean passCEligible, boolean hostSupportsIntake,
			boolean horizontalHost, int bodyGroupCount, int rowCount, int columnCount,
			boolean hasRowspan, boolean hasFooter, boolean hasTopCaption, boolean hasGroupPageSize,
			boolean maySplitRows, boolean hasOrthogonalCell, boolean hasPageSideEffects,
			boolean complexAncestor, boolean hasForcedBreak, boolean hasColumnTree) {
	}

	/**
	 * Post-Pass-B emission eligibility. Only an empty set makes the table an emission candidate.
	 * Check after table frame calculation and before markIncomplete, using values before suppressing the end margin.
	 * Allow only bottom captions. Exclude top-caption cases via CAPTION, leaving everything from placement before Pass
	 * B through parent cuts and dimension accounting to the completed path.
	 */
	public static EnumSet<RowEmissionExclusion> rowEmissionExclusionsAfterPassB(final TableBox table,
			final RowEmissionFacts facts) {
		final EnumSet<RowEmissionExclusion> reasons = EnumSet.noneOf(RowEmissionExclusion.class);
		if (!facts.main()) {
			reasons.add(RowEmissionExclusion.NOT_MAIN);
		}
		if (!facts.passCEligible()) {
			reasons.add(RowEmissionExclusion.PASS_C_INELIGIBLE);
		}
		if (!facts.hostSupportsIntake() || table.getBlockBox().getPos().getType() != PosType.FLOW) {
			reasons.add(RowEmissionExclusion.UNSUPPORTED_HOST);
		}
		if (!facts.horizontalHost() || table.getTableParams().flow.isVertical()) {
			reasons.add(RowEmissionExclusion.WRITING_MODE);
		}
		if (facts.bodyGroupCount() != 1) {
			reasons.add(RowEmissionExclusion.BODY_GROUPS);
		}
		if (facts.hasRowspan()) {
			reasons.add(RowEmissionExclusion.ROWSPAN);
		}
		if (facts.hasFooter()) {
			reasons.add(RowEmissionExclusion.FOOTER);
		}
		if (facts.hasTopCaption()) {
			reasons.add(RowEmissionExclusion.CAPTION);
		}
		if (table.getTableParams().borderCollapse != TableParams.BORDER_SEPARATE) {
			reasons.add(RowEmissionExclusion.COLLAPSED_BORDERS);
		}
		if (facts.hasGroupPageSize()) {
			reasons.add(RowEmissionExclusion.GROUP_PAGE_SIZE);
		}
		if (facts.maySplitRows()) {
			reasons.add(RowEmissionExclusion.ROW_SPLITTING);
		}
		if (facts.hasOrthogonalCell()) {
			reasons.add(RowEmissionExclusion.ORTHOGONAL_CELL);
		}
		if (table.getFrame().margin.bottom < 0) {
			reasons.add(RowEmissionExclusion.NEGATIVE_END_MARGIN);
		}
		if (facts.hasPageSideEffects()) {
			reasons.add(RowEmissionExclusion.PAGE_SIDE_EFFECTS);
		}
		if (facts.complexAncestor()) {
			reasons.add(RowEmissionExclusion.COMPLEX_ANCESTOR);
		}
		if (facts.hasForcedBreak() && facts.hasColumnTree()) {
			reasons.add(RowEmissionExclusion.FORCED_BREAK_WITH_COLUMNS);
		}
		if (facts.rowCount() == 0 || facts.columnCount() == 0) {
			reasons.add(RowEmissionExclusion.EMPTY_TABLE);
		}
		return reasons;
	}

	/** Collects inputs from the actual anonymous flow and the Pass B plan, which has not yet been released. */
	static EnumSet<RowEmissionExclusion> rowEmissionExclusionsAfterPassB(final TableBox table,
			final BlockBuilder host, final boolean passCEligible, final int bodyGroupCount,
			final boolean footer, final boolean topCaption, final int columnCount, final boolean columns,
			final TableRowGroupBox header, final List<TableRowGroupBox> groups,
			final Map<TableRowGroupBox, ? extends List<TableRowBox>> groupRows,
			final Map<TableRowBox, ? extends List<CellContent>> rowCells) {
		if (host.getPageContext() == null || !UAProps.PROCESSING_TABLE_ROW_EMISSION
				.getBoolean(host.getPageContext().getPageGenerator().getUserAgent())) {
			return EnumSet.of(RowEmissionExclusion.DISABLED);
		}
		final BreakableBuilder intake = host instanceof BreakableBuilder b ? b : null;
		boolean horizontal = true, complex = false, floating = false;
		boolean forced = intake != null && intake.breakAfter != null;
		double ancestorFrame = 0;
		for (Builder ancestor = host; ancestor != null; ancestor = ancestor.getParentBuilder()) {
			if (!(ancestor instanceof BlockBuilder block)) {
				complex = true;
				break;
			}
			floating |= block.hasLineExclusions();
			for (int i = 0; i < block.getFlowCount(); ++i) {
				final var box = block.getFlow(i).box;
				final BlockParams params = box.getBlockParams();
				horizontal &= !params.flow.isVertical();
				if (box.getType() == BoxType.PAGE) continue; // The paper's specified height is distinct from specified ancestor heights.
				if (box.getPos() instanceof AbstractBlockLevelPos pos) {
					forced |= forced(pos.pageBreakBefore) || forced(pos.pageBreakAfter);
				}
				complex |= box.getPos().getType() != PosType.FLOW || box.getColumnCount() > 1
						|| params.columns.count != 0 || !LayoutUtils.isNone(params.columns.width)
						|| params.size.getPageType(params.flow) != LengthType.AUTO
						|| params.maxSize.getPageType(params.flow) != LengthType.AUTO
						|| (params.minSize.getPageType(params.flow) != LengthType.AUTO
								&& (params.minSize.getPageType(params.flow) != LengthType.ABSOLUTE
										|| params.minSize.getPageLength(params.flow) != 0))
						|| params.aspectRatio != 0;
				ancestorFrame += Math.max(0, box.getFrame().getFrameTop())
						+ Math.max(0, box.getFrame().getFrameBottom());
			}
		}
		double headerSize = 0;
		if (header != null) {
			for (final TableRowBox row : groupRows.get(header)) headerSize += row.getPageSize();
		}
		// The current page's capacity may not hold on the next page. Stage 1 uses the capacity floor across all pages.
		// If every row fits including frames, repeated headers, and ancestor frames, the first row after continuation
		// avoids intra-row splitting and oversized-row rescue. Use current remaining capacity only for the initial first row.
		final double frame = Math.max(0, table.getFrame().getFrameTop())
				+ Math.max(0, table.getFrame().getFrameBottom());
		final double rowCapacity = BreakableBuilder.MIN_PAGE_LIMIT - ancestorFrame - frame - headerSize;
		final double firstCapacity = intake == null ? 0
				: intake.getPageLimit() - intake.getPageAxis() - frame - headerSize;
		boolean rowspan = false, groupSize = false, orthogonal = false, effects = false, pageDependent = false;
		boolean splitRows = !Double.isFinite(headerSize) || headerSize < 0
				|| !Double.isFinite(rowCapacity) || !Double.isFinite(firstCapacity);
		int rowCount = 0;
		for (final TableRowGroupBox group : groups) {
			final var params = group.getInnerTableParams();
			groupSize |= params.size.getType() != LengthType.AUTO
					|| params.maxSize.getType() != LengthType.AUTO
					|| (params.minSize.getType() != LengthType.AUTO
							&& (params.minSize.getType() != LengthType.ABSOLUTE || params.minSize.getLength() != 0));
			forced |= forced(group.getTableRowGroupPos().pageBreakBefore)
					|| forced(group.getTableRowGroupPos().pageBreakAfter);
			final List<TableRowBox> rows = groupRows.get(group);
			for (int i = 0; i < rows.size(); ++i) {
				final TableRowBox row = rows.get(i);
				if (group != header) {
					++rowCount;
					final double size = row.getPageSize();
					splitRows |= !Double.isFinite(size) || size < 0 || size > rowCapacity
							|| (i == 0 && size > firstCapacity);
					// Backtracking for avoid between rows can apply a height-minus-1-pt cut line even to the first row.
					// Even if the row fits on a page, intra-row splitting at that artificial line is unsupported.
					splitRows |= row.getTableRowPos().pageBreakBefore == PageBreakMode.AVOID
							|| row.getTableRowPos().pageBreakAfter == PageBreakMode.AVOID;
				}
				forced |= forced(row.getTableRowPos().pageBreakBefore) || forced(row.getTableRowPos().pageBreakAfter);
				for (final CellContent cell : rowCells.get(row)) {
					rowspan |= cell.rowspan > 1;
					if (cell.isExtended()) continue;
					orthogonal |= cell.getCellBox().getBlockParams().flow.isVertical();
					final var body = cell.sealedBodyOrNull();
					if (body != null && body.handle() != null && !body.handle().hasTextSlice()) {
						final var range = body.handle();
						final var source = range.source();
						if (!pageDependent) {
							// Only inspect frozen params; do not create boxes or bind body text.
							// Conservatively exclude percentages and percentage components of calc (including widths resolved against cells).
							// ViewportUnits resolves vh/vw and similar units to absolute lengths from UA settings at parse time.
							// Those absolute lengths stay unchanged during replay, so they do not depend on the current page.
							try (final var slice = source.capture(range.fromId(), range.toId())) {
								final boolean[] relative = { slice == null };
								if (slice != null) slice.replay(event -> {
									if (event instanceof net.zamasoft.foliojet.layout.fragment.LayoutSource.Replaced replaced) {
										relative[0] |= replaced.recipe().params().hasRelativeSize();
									} else if (event instanceof net.zamasoft.foliojet.layout.fragment.LayoutSource.Start start) {
										relative[0] |= start.recipe().hasPageRelativeSize();
									}
								});
								pageDependent = relative[0];
							}
						}
						// The float index includes FOOTNOTE/PAGE_*/PAGE_NOTE_* and descendants.
						// Stage 1 does not prove that moving bind timing is safe for ordinary floats or absolute positioning either.
						effects |= source.containsFloat(range.fromId(), range.toId())
								|| source.containsAbsolute(range.fromId(), range.toId())
								|| source.containsOpaque(range.fromId(), range.toId());
					}
				}
			}
		}
		final EnumSet<RowEmissionExclusion> reasons = rowEmissionExclusionsAfterPassB(table, new RowEmissionFacts(
				ReplayIntent.current() == ReplayIntent.MAIN && host.isMain(), passCEligible,
				intake != null && intake.supportsIncompleteTableIntake(), horizontal, bodyGroupCount,
				rowCount, columnCount, rowspan, footer, topCaption, groupSize, splitRows, orthogonal, effects,
				complex, forced, columns));
		if (floating) reasons.add(RowEmissionExclusion.FLOATING_HOST);
		if (pageDependent) reasons.add(RowEmissionExclusion.PAGE_DEPENDENT_CELL_CONTENT);
		return reasons;
	}

	/**
	 * Minimum visible body extent needed for initial intake and append notifications of incomplete tables (B-2b-5).
	 * Pass capacity at least as large as the cut line available to body text. A conservative capacity without
	 * subtracting positive frames/HEADER extent is acceptable. Parent addition and local cut-line subtraction can
	 * round to opposite sides of the 0.5 pt boundary, so a positive compare is insufficient. Hold pending until both
	 * addition and subtraction strictly exceed THRESHOLD. The last row skips this check and proceeds to completion.
	 */
	public static boolean hasRowEmissionOverflow(final double visibleBodySize, final double capacity) {
		return visibleBodySize > capacity + LayoutUtils.THRESHOLD
				&& visibleBodySize - capacity > LayoutUtils.THRESHOLD;
	}

	/**
	 * Whether visible rows alone determine a cut (B-2b-6, cut contract).
	 *
	 * <p>
	 * A dry run reproducing the prelude and scan body of {@code TableRowGroupBox.splitPageAxis} with <b>the same
	 * values, order, and comparisons</b>. Comparing total height ({@link #hasRowEmissionOverflow}) or subtracting
	 * sequentially from the limit can land on a different side of 0.5 pt equivalence than the completed path because
	 * of different parentheses ({@code C−(a+b)} versus {@code (C−a)−b}) (0.5 pt counterexample on 2026-09-08).
	 * </p>
	 * <ul>
	 * <li>If the whole group is KEEP ({@code compare(limit, groupPageSize) >= 0}), the cut is undetermined (more rows
	 * needed).</li>
	 * <li>Scan: for a nonfinal row, if {@code compare(limit, size) > 0}, advance with {@code limit -= size}.
	 * The stopped row uses {@code TableCutter}: {@code compare(limit, 0) < 0} means MOVE (determined), and {@code
	 * compare(limit, size) >= 0} means KEEP. <b>The implementation continues scanning after KEEP</b> ({@code pageLimit
	 * -= prevRowSize; continue} in {@code TableRowGroupBox.splitPageAxis}), so KEEP through the visible range's end
	 * leaves the cut undetermined (codex review counterexample on 2026-09-08: tiny rows [4.9,0.5,1.4,0.3,0.1,0.2,…]
	 * kept returning KEEP within the equivalence tolerance, ending with −0.4999… remaining). Otherwise (cut line
	 * crosses a row), a row split or MOVE determines the cut.</li>
	 * </ul>
	 *
	 * @param rowPageSizes  page-direction sizes of visible rows (in row order)
	 * @param groupPageSize accumulated group pageSize (the exact value compared by the completed path)
	 * @param pageLimit     cut limit passed to the group (after deducting table frames and headers)
	 */
	public static boolean cutDetermined(final double[] rowPageSizes, final double groupPageSize,
			double pageLimit) {
		if (rowPageSizes.length == 0) return false;
		if (LayoutUtils.compare(pageLimit, 0) < 0) return true;
		if (LayoutUtils.compare(pageLimit, groupPageSize) >= 0) return false;
		final int last = rowPageSizes.length - 1;
		for (int i = 0; i < rowPageSizes.length; ++i) {
			final double size = rowPageSizes[i];
			if (i < last && LayoutUtils.compare(pageLimit, size) > 0) {
				pageLimit -= size;
				continue;
			}
			if (LayoutUtils.compare(pageLimit, 0) < 0) return true;
			if (LayoutUtils.compare(pageLimit, size) >= 0) {
				// KEEP: the implementation proceeds to the next row. KEEP through the visible range's end requires more rows.
				pageLimit -= size;
				continue;
			}
			return true;
		}
		return false;
	}

	private static boolean forced(final PageBreakMode mode) {
		return mode != PageBreakMode.AUTO && mode != PageBreakMode.AVOID;
	}

	/**
	 * @param builder  builder for the context constructing the table
	 * @param tableBox target table box
	 */
	public static TableBuildPlan plan(final Builder builder, final TableBox tableBox) {
		final TableParams params = tableBox.getTableParams();
		final EnumSet<TableRetentionReason> reasons = EnumSet.noneOf(TableRetentionReason.class);
		if (!builder.isMain()) {
			reasons.add(TableRetentionReason.NESTED_LAYOUT);
		}
		if (params.layout == TableParams.LAYOUT_AUTO) {
			reasons.add(TableRetentionReason.AUTO_COLUMNS);
		}
		final boolean isFlow = tableBox.getBlockBox().getPos().getType() == PosType.FLOW;
		if (!isFlow) {
			reasons.add(TableRetentionReason.OUT_OF_FLOW);
		}
		if (params.size.getPageType(params.flow) != LengthType.AUTO) {
			reasons.add(TableRetentionReason.SPECIFIED_PAGE_SIZE);
		}
		if (params.size.getLineType(params.flow) == LengthType.AUTO) {
			reasons.add(TableRetentionReason.AUTO_LINE_SIZE);
		}
		// M6b Phase B5e (2026-07-21): route to RETAINED when the table's own writing direction
		// is orthogonal to the current open flow (horizontal ⇄ vertical). With Incremental,
		// IncrementalTableBuilder.pageBreak() bypasses the breakDepth barrier and actually reaches legacy
		// OpenChain (ContinuationCapability.ORTHOGONAL_FLOW)
		// (see the Javadoc for TableRetentionReason.ORTHOGONAL_WRITING_MODE).
		if (builder instanceof BreakableBuilder breakableBuilder
				&& breakableBuilder.getFlowBox().getBlockParams().flow.isVertical() != params.flow.isVertical()) {
			reasons.add(TableRetentionReason.ORTHOGONAL_WRITING_MODE);
		}
		if (reasons.isEmpty()) {
			return new TableBuildPlan(TableBuildPlan.Mode.INCREMENTAL, reasons);
		}
		return new TableBuildPlan(TableBuildPlan.Mode.RETAINED, reasons);
	}
}
