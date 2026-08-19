package net.zamasoft.foliojet.layout.box;

/**
 * {@link PageAtomicBox}のうち、行境界の帳簿を持つときに限り自身の
 * {@code split}で行単位のページ分割(テーブル行の契約と同型)を行う
 * ボックスの印です(2026-08-10、grid行分割の導入でflex専用判定から
 * 一般化)。
 *
 * <p>
 * {@code PaginationContract.splitsInPageAxis}だけがこの印を見て
 * PageAtomicBoxの「常にatomic」を上書きする。
 * {@code isChainAtomicBoundary}側は上書きしない——チェーン継続の
 * {@code BreakPlan}がメンバーとして選ぶと、{@code split}を直接呼ぶ
 * 経路ではなくソース再生ベースの汎用継続へ迂回し、行の強制分割で
 * 作った継続itemの位置が壊れる(2026-08-07に実測)。この非対称は
 * テーブルと同じ理由の意図的なもの。
 * </p>
 */
public interface RowSplitBox {

	/**
	 * 行分割の対象か。falseなら従来のPageAtomicBox経路
	 * (丸ごと送り/visual rescue)を使う。
	 */
	boolean hasRowSplitLines();

	/**
	 * 行帳簿のスナップショットを返します(視覚順。各行は
	 * {flow先頭index, item数, 行start, 行extent})。帳簿が無ければnull。
	 * {@code RowSplitContainer}の復元時の押し下げ
	 * ({@code restoreAnchoredPageAxis}参照)が行のグループ化に使う
	 * (2026-08-19)。
	 */
	double[][] rowLedgerSnapshot();

	/**
	 * 押し下げ後の行startを帳簿へ書き戻します({@link #rowLedgerSnapshot}と
	 * 同じ順序・件数)。帳簿と実描画位置を一致させ、以後の再分割の境界探索を
	 * 狂わせないため(2026-08-19)。
	 */
	void syncRowStarts(double[] starts);
}
