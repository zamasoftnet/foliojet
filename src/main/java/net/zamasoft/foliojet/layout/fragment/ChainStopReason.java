package net.zamasoft.foliojet.layout.fragment;

/**
 * plan選択済み(収集可能と判定された)チェーンメンバー自身の
 * {@code splitForContinuation}/{@code split}が返した{@code SplitResult}の
 * うち、{@code Frame}にならなかった理由です(2026-07-21新設、M6b Phase
 * B5c-2)。{@code ContainerCut.PlainWithChainStop}とともに、段組を
 * またぐ改ページ(改ページ契約§5.10ルール2・4)のために恒久的に必要な
 * 型であり、削除予定はない。
 */
public enum ChainStopReason {
	/** チェーンメンバーが{@code SplitResult.Keep}を返した(box全体を現在側に残す)。 */
	KEEP,
	/** チェーンメンバーが{@code SplitResult.Move}を返した(box全体を次側へ送る)。 */
	MOVE
}
