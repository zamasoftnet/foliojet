package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.TableBuilder;

/**
 * Lifecycle adapter that centralizes selection, start, and end of
 * IncrementalTableBuilder/RetainedTableBuilder (C4 preparation, 2026-07-19;
 * delegates routing decisions to {@link TableBuildPlanner} in C4-B).
 *
 * <p>
 * No calculations or box operations changed. This only moved the builder selection
 * conditions scattered through DocumentBuilder, and the isIncremental() check plus
 * cast to IncrementalTableBuilder at the end, into this class.
 * </p>
 *
 * <p>
 * <b>Final form of C4 (settled in the external ChatGPT Pro design review on 2026-07-19;
 * see development plan "C4" for details)</b>: Unifying table builders does not mean merging
 * them into a single row algorithm. Apart from the fixed/auto column-width policies,
 * the essential difference is the execution plan: either commit early (Incremental),
 * or retain the entire table (or row-group) before committing (Retained).
 * These retention lifetimes and commit points must not be unified. Mathematically,
 * three requirements cannot all hold: distribute row heights that depend on the entire table
 * exactly according to compatibility rules, emit completed pages immutably as soon as ready,
 * and consume input once with bounded memory.
 * The parts that can be unified are the computational kernels for column widths, cell measurement,
 * rowspan distribution, border resolution, and fragmentation; these are already shared as
 * RowLayoutEngine/CellContent/CollapsedBorderRules and others. Develop this class toward a
 * settled thin layer that selects between the two execution plans (C4-C: isOnePass() was renamed
 * to isIncremental() on 2026-07-19, with no behavior change. Replacing the DocumentBuilder-side
 * branch itself with a tell-don't-ask structure equivalent to TableBuildSession requires a design
 * decision to expose DocumentBuilder's private inline context operations
 * (closeInlines/endContainer/startContainer), so that work goes into a separate design cycle).
 * </p>
 */
public final class TableBuilderLifecycle {
	private TableBuilderLifecycle() {
	}

	/**
	 * At the start of a table, selects a builder according to the execution plan chosen by
	 * {@link TableBuildPlanner}, calls {@link IncrementalTableBuilder#startLayout} if needed,
	 * and returns the builder.
	 */
	public static TableBuilder start(Builder builder, TableBox tableBox) {
		final TableBuildPlan plan = TableBuildPlanner.plan(builder, tableBox);
		if (plan.mode() == TableBuildPlan.Mode.RETAINED) {
			TableBuildStats.TWO_PASS_BUILDS.incrementAndGet();
			// E-6 increment 1 (2026-07-24): counts by Retained reason (observation only; no behavior change).
			TableBuildStats.recordRetentionReasons(plan.reasons());
			return new RetainedTableBuilder(builder, tableBox);
		}
		// Incremental (equivalent to table-layout:fixed)
		TableBuildStats.ONE_PASS_BUILDS.incrementAndGet();
		final IncrementalTableBuilder fixedTableBuilder = new IncrementalTableBuilder(tableBox);
		fixedTableBuilder.startLayout((RootBuilder) builder);
		return fixedTableBuilder;
	}

	/**
	 * Handles the end of a table. tableBuilder itself knows how to terminate each execution plan
	 * (A-2, 2026-07-30: replaced the old isIncremental() branch + cast with tell-don't-ask).
	 */
	public static void finish(TableBuilder tableBuilder, Builder builder) {
		tableBuilder.finish(builder);
	}
}
