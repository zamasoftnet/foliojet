package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * 改ページ契約(ARCHITECTURE.md §5.10、2026-07-22確定)の書字方向まわりの
 * 判定を1箇所に宣言します(2026-07-30新設)。
 *
 * <p>
 * それまで「どのボックスがatomic(丸ごと収まるか、丸ごと次ページへ送られる
 * か)か」の判定は、{@code BreakableBuilder.startFlowBlock()}の
 * {@code breakDepth}障壁と{@code FlowContainer.splitPageAxis()}の軸比較という
 * <b>2箇所の書字方向比較の偶然の一致</b>として実装されており、契約を
 * 問い合わせる型が存在しなかった。CSS Grid/Flexbox(断片化なし=atomic)の
 * ような新しいレイアウトを足すときは、書字方向比較を増やすのではなく
 * ここに判定を足すこと。
 * </p>
 *
 * <p>
 * <b>2つの述語は意図的に強さが違う。</b>
 * {@link #isChainAtomicBoundary}は{@link WritingMode}の完全一致
 * (RL⇄LRの方向違いも境界)で、自動改ページがそのボックスの内部から
 * 始まること自体を抑止する(コミット{@code 77eef99}、
 * {@link ContinuationCapability#SAME_AXIS_DIRECTION_CHANGE}と同じ扱い)。
 * 一方{@link #splitsInPageAxis}は軸(縦/横)だけの比較で、
 * 「その場での幾何学的切断に意味があるか」を判定する——同軸の方向違い
 * (RL⇄LR)はページ軸の幾何が同一なので、切断の実行自体は可能なまま
 * 残している(上流の障壁が自動改ページを抑止するため、実文書でこの差が
 * 現れるのは強制改ページ等の限定経路のみ)。この非対称は出荷済みの
 * 挙動であり、統一する場合はgolden差分の全数レビューを伴うこと。
 * </p>
 *
 * @see ContinuationCapability 祖先チェーン収集(open chain)側の分類
 */
public final class PaginationContract {
	private PaginationContract() {
		// 静的ユーティリティ
	}

	/**
	 * 親フローの書字方向{@code outer}に対し、子ブロックの書字方向
	 * {@code inner}がチェーン継続のatomic境界になるかを判定します。
	 *
	 * <p>
	 * 境界の内側では自動改ページを開始しない(§5.10ルール3——absolute・
	 * 書字方向が幹と食い違うボックスはatomic)。{@code WritingMode}は
	 * enumなので参照比較で足りる。{@code isVertical()}だけの比較では
	 * 軸が同じRL/LRの違いを見逃す。
	 * </p>
	 */
	public static boolean isChainAtomicBoundary(final WritingMode outer, final WritingMode inner) {
		return outer != inner;
	}

	/**
	 * ページ進行軸が{@code vertical}(縦書きか)の文脈で、書字方向
	 * {@code inner}の子ブロックをその場で切断できるか(切断がページ軸の
	 * 幾何として意味を持つか)を判定します。
	 *
	 * <p>
	 * 軸が食い違う子({@link ContinuationCapability#ORTHOGONAL_FLOW}相当)は
	 * 切断せず、置換要素と同じatomic経路(丸ごと残すか丸ごと次ページ)へ
	 * 落とす。
	 * </p>
	 */
	public static boolean splitsInPageAxis(final boolean vertical, final WritingMode inner) {
		return vertical == inner.isVertical();
	}
}
