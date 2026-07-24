package net.zamasoft.foliojet.layout.fragment;

/**
 * ブロックfloat専用のページ方向切断結果です(2026-07-24新設、排除域A-3a)。
 * {@link SplitResult}との違いはSplitのケースのみ——残余boxを即時構築せず、
 * 構築材料({@link PreparedFloatFragment})のまま返します。
 * Keep/Moveの意味は{@link SplitResult}と同一。Frame(チェーン継続)は
 * floatでは起きないため存在しない。
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface FloatFragmentSplit {
	/** 全体を前のフラグメントに残します。 */
	FloatFragmentSplit KEEP = new Keep();

	/** 全体を次のフラグメントへ送ります。 */
	FloatFragmentSplit MOVE = new Move();

	record Keep() implements FloatFragmentSplit {
	}

	record Move() implements FloatFragmentSplit {
	}

	/**
	 * 内部で切断しました。前断片のmutationは実行済みで、残余boxの構築
	 * 材料を運びます(構築は受け側が一度だけ{@code materialize})。
	 *
	 * @param fragment 継続断片の材料
	 */
	record Prepared(PreparedFloatFragment fragment) implements FloatFragmentSplit {
	}
}
