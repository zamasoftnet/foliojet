package net.zamasoft.foliojet.layout.box.impl;

/** The interface through which row subgrids register row contributions and finalization with the parent (2026-09-03). */
public interface RowContributionSink {

	/**
	 * Registers a contribution spanning {@code span} rows from child-local {@code row}.
	 * The boundary converts to parent coordinates exactly once.
	 */
	void contribute(int row, int span, double extent);

	/** Registers processing to run after the parent rows are finalized. */
	void whenRowsResolved(RowGeometryFinalizer finalizer);
}
