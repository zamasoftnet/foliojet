package net.zamasoft.foliojet.layout.fragment;

/**
 * plan選択済み(収集可能と判定された)チェーンメンバー自身の
 * {@code splitForContinuation}/{@code split}が返した{@code SplitResult}の
 * うち、{@code Frame}にならなかった理由です(2026-07-21新設、M6b Phase
 * B5c-2)。
 */
public enum ChainStopReason {
	/** チェーンメンバーが{@code SplitResult.Keep}を返した(box全体を現在側に残す)。 */
	KEEP,
	/** チェーンメンバーが{@code SplitResult.Move}を返した(box全体を次側へ送る)。 */
	MOVE
}
