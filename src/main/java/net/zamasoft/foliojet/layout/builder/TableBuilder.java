package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;

/**
 * Builds a table.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface TableBuilder {
	public TableBox getTableBox();

	public void startInnerTable(AbstractInnerTableBox box);

	public void endInnerTable();

	public Builder newContext(AbstractContainerBox box);

	/**
	 * Finishes the table (A-2, 2026-07-30). Execution-plan completion differs by implementation:
	 * Incremental commits the remainder and ends layout; Retained binds the whole table via
	 * {@code host.addTable(this)}. Therefore, delegates to the implementation instead of having the
	 * caller branch on isIncremental (tell-don't-ask, as with {@link #prepareEnterCell}).
	 * The former {@code isIncremental()} was removed in this migration.
	 */
	public void finish(Builder host);

	/**
	 * Called just before entering a cell/caption (before newContext)
	 * (C4-C refinement, 2026-07-19). Incremental (OnePass) must close the inline context again,
	 * while Retained (TwoPass) has an independent inner builder and needs no action. Delegates this
	 * decision to the TableBuilder implementation instead of having DocumentBuilder ask isIncremental()
	 * (tell-don't-ask). Defaults to a no-op, as for Retained.
	 */
	public default void prepareEnterCell(TableBuilderHost host) {
	}

	/**
	 * Called just before entering a column/row group/row (C4-C refinement). Defaults to a no-op.
	 */
	public default void prepareEnterTrack(TableBuilderHost host) {
	}

	/**
	 * Called just after entering a column/row group/row (C4-C refinement). Defaults to a no-op.
	 */
	public default void afterEnterTrack(TableBuilderHost host) {
	}

	/**
	 * Called just after the cell/caption container builder closes (recording completion point)
	 * (E-6 increment 5a, 2026-07-24). The Retained implementation seals eligible cell bodies into
	 * "IntrinsicSizes numbers + LayoutSource range reference (+lease)" retention, releasing records.
	 * Defaults to a no-op (excludes Incremental table cells: their retention window is short, per row;
	 * reducing the peak before close is handled in a separate increment).
	 *
	 * @param cellBuilder closed cell/caption container builder
	 */
	public default void sealCellContext(Builder cellBuilder) {
	}
}
