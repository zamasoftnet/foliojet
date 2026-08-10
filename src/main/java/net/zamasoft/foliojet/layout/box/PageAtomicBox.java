package net.zamasoft.foliojet.layout.box;

/**
 * ページ軸で構造分割しないボックスの印です(Grid G0、2026-07-31——
 * consult-codex-2026-07-31-grid.txt §1.2)。
 *
 * <p>
 * {@code page-break-inside: avoid}(ページ先頭では上書きされて内容分割
 * される)とは違い、<b>常時</b>分割不可の型付き契約。判定は
 * {@link net.zamasoft.foliojet.layout.fragment.PaginationContract}の
 * 2述語(チェーンatomic境界=内部からの自動改ページ抑止/その場の
 * 幾何学的切断可否)の両方に効く——片側だけでは真のatomicにならない。
 * ページに入らない場合は置換要素と同じ経路(丸ごと次ページへ送り、
 * それでも入らなければvisual rescueの帯状切断)へ落ちる。
 * </p>
 */
public interface PageAtomicBox {

	/**
	 * 原子契約が現在有効かを返します(2026-08-10、G6行分割で追加)。
	 *
	 * <p>
	 * 既定はtrue(常時atomic)。GridBoxはトラック配置(GridBuilder.bind)が
	 * 実際に走った場合だけtrueを返す——TwoPass不活性でG0(単一列の通常
	 * フロー)へ退行した入れ子gridは、守るべきトラック配置が存在せず、
	 * 原子扱いにすると「中身が丸ごと次ページへ沈む」だけが残る
	 * (gigazine.netの実ページで実測: #main内の.content入れ子gridが
	 * これで、外側#mainの行分割が入っても頭断片が空のままだった)。
	 * </p>
	 */
	default boolean isPageAtomicNow() {
		return true;
	}
}
