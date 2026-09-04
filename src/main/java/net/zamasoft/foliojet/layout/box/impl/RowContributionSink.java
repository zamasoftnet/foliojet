package net.zamasoft.foliojet.layout.box.impl;

/** row subgridが親へ行寄与と最終化処理を登録する窓口です(2026-09-03)。 */
public interface RowContributionSink {

	/**
	 * 子ローカルの{@code row}から{@code span}行を跨ぐ寄与を登録します。
	 * 親座標への変換は境界側が一度だけ行います。
	 */
	void contribute(int row, int span, double extent);

	/** 親の行が確定した後に呼ぶ処理を登録します。 */
	void whenRowsResolved(RowGeometryFinalizer finalizer);
}
