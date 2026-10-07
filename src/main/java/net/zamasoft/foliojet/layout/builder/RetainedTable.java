package net.zamasoft.foliojet.layout.builder;

/**
 * Contract for a table retained execution plan (retains the entire table before committing,
 * equivalent to {@code table-layout:auto}) to incorporate itself into the host builder
 * (A-2, 2026-07-30).
 *
 * <p>
 * Previously, every implementation of {@code Builder.addTable(TableBuilder)} hard-cast to
 * {@code RetainedTableBuilder} before calling {@code prepareLayout()}/{@code bind()}.
 * The knowledge that only Retained reached this point was an implicit caller assumption.
 * Promotes this assumption into the type system and eliminates casts.
 * </p>
 *
 * @see TableBuilder#finish(Builder) entry point for each execution plan's completion processing
 */
public interface RetainedTable extends TableBuilder, TwoPass {
	/** Source identifier of the table plan, accessible even after rows are emitted. */
	public default long getSourceAnchor() {
		return this.getTableBox().getSourceAnchor();
	}

	/**
	 * Finalizes dimensions and column widths after all rows have been read and before bind.
	 */
	public void prepareLayout();

	/**
	 * Incorporates the constructed table into the host (reruns already measured content).
	 */
	public void bind(Builder host);
}
