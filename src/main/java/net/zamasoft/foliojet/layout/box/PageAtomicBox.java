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
}
