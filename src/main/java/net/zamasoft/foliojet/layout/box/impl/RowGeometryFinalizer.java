package net.zamasoft.foliojet.layout.box.impl;

/** 親で確定した行幾何をrow subgridへ返す処理です(2026-09-03)。 */
@FunctionalInterface
public interface RowGeometryFinalizer {

	void finalizeRows(double[] rowHeights, double[] rowStarts, double parentRowGap);
}
