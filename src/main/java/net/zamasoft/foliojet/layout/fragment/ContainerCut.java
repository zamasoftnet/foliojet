package net.zamasoft.foliojet.layout.fragment;

/**
 * コンテナのページ方向切断の結果です(C1d-C)。
 *
 * <p>
 * 従来の {@code Container} 返し(null=KEEP / this=MOVE / 他=残余)の
 * 契約に、継続フレームの伝播({@link WithFrame})を加えた形。
 * BreakPlan なしの legacy 呼び出しでは {@link Plain} のみが返ります。
 * </p>
 */
public sealed interface ContainerCut {
	/**
	 * 従来契約の結果です(container: null=KEEP / 自身=MOVE / 他=残余)。
	 */
	record Plain(net.zamasoft.foliojet.layout.box.content.Container container) implements ContainerCut {
	}

	/**
	 * plan選択済みチェーンメンバー自身が{@code Keep}/{@code Move}を返した
	 * ことを明示的に運ぶ結果です(2026-07-21新設、M6b Phase B5c-2)。
	 * 強制改ページ分岐に加え、自動改ページ主ループでも「nextBoxが
	 * チェーンメンバー単体だけを含む」(force-branchと同型でcontainer-
	 * identity比較では判別できない)場合に限って付与される(2026-07-22
	 * のB5c-2 Step3再挑戦で適用済み——{@code FlowContainer.splitPageAxis}
	 * の最終return参照)。段組をまたぐ改ページ(改ページ契約§5.10
	 * ルール2・4)のために恒久的に必要な型であり、削除予定はない。
	 */
	record PlainWithChainStop(net.zamasoft.foliojet.layout.box.content.Container container, ChainStopReason reason)
			implements ContainerCut {
	}

	/**
	 * 切断がチェーンを貫通し、残余コンテナに加えて継続フレームが
	 * 返されました(チェーン子はボックスとして運搬されない)。
	 */
	record WithFrame(net.zamasoft.foliojet.layout.box.content.Container container,
			Continuation.ContinuationFrame frame) implements ContainerCut {
	}
}
