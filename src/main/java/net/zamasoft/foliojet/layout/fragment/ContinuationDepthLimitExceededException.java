package net.zamasoft.foliojet.layout.fragment;

/**
 * 開いたブロック祖先チェーン(M6b Phase B、{@code FlowContainer.restyle}の
 * {@code OpenChain}分岐)の深さが安全閾値を超えたため、素の
 * StackOverflowErrorに到達する前に処理を中断したことを示します
 * (2026-07-20)。この分岐はまだ反復化されていない再帰であり、この例外は
 * 実装バグではなく既知の複雑度上限です(docs/PLAN.md「M6b Phase B」参照)。
 */
public class ContinuationDepthLimitExceededException extends RuntimeException {
	private static final long serialVersionUID = 0L;

	public ContinuationDepthLimitExceededException(final String message) {
		super(message);
	}
}
