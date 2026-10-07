package net.zamasoft.foliojet.layout.box.impl;

/** Returns row geometry finalized by the parent to a row subgrid (2026-09-03). */
@FunctionalInterface
public interface RowGeometryFinalizer {

	void finalizeRows(double[] rowHeights, double[] rowStarts, double parentRowGap);
}
