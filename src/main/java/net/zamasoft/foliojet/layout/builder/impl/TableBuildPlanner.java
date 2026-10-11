package net.zamasoft.foliojet.layout.builder.impl;

import java.util.EnumSet;

import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.builder.Builder;

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
