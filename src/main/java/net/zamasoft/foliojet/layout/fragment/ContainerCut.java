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
	 * 切断がチェーンを貫通し、残余コンテナに加えて継続フレームが
	 * 返されました(チェーン子はボックスとして運搬されない)。
	 */
	record WithFrame(net.zamasoft.foliojet.layout.box.content.Container container,
			Continuation.ContinuationFrame frame) implements ContainerCut {
	}
}
