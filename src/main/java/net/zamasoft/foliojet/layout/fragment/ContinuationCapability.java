package net.zamasoft.foliojet.layout.fragment;

/**
 * {@code RootBuilder.pageBreak()}の収集可能プレフィックス事前検分
 * (collectable)が、祖先チェーンの各レベルをどう判定したかの理由です
 * (2026-07-21新設、M6b Phase B B1)。
 *
 * <p>
 * 挙動は従来と完全に同一({@link #PLAIN_FLOW}のみが収集可能、それ以外は
 * すべてプレフィックススキャンを停止させる)。このenumは「なぜ収集
 * できなかったか」を可視化するための分類であり、承認条件そのものは
 * 変更しない(ChatGPT Pro相談で得たB1のスコープ、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-full-fix.md)。
 * </p>
 */
public enum ContinuationCapability {
	/** plain {@code FlowBlockBox}、ルートと同一書字方向——収集可能。 */
	PLAIN_FLOW,

	/** 段組({@code MulticolumnBlockBox}、{@code column-count>1}相当)。 */
	MULTICOL,

	/**
	 * {@code FlowBlockBox}の{@link #MULTICOL}以外のサブタイプ
	 * (例: {@code RubyBodyBox})。{@code getClass() == FlowBlockBox.class}
	 * という完全一致判定により機械的に除外される。
	 */
	FLOW_SUBTYPE,

	/**
	 * ルートと同じ軸(横書き/縦書き)だが、方向が異なる
	 * (例: {@code vertical-rl}祖先の途中に{@code vertical-lr})。
	 * 実際の内部切断可否({@code FlowContainer.splitPageAxis})・自動
	 * 改ページ障壁({@code BreakableBuilder.startFlowBlock})はどちらも
	 * {@code isVertical()}の一致しか見ないため、この分類は「切断自体は
	 * できるのに事前検分だけが不必要に厳しい」ケース。
	 */
	SAME_AXIS_DIRECTION_CHANGE,

	/** ルートと軸そのものが異なる(横書き⇄縦書き)。 */
	ORTHOGONAL_FLOW,

	/** {@code FlowBlockBox}ではない箱(表・浮動要素等)。 */
	UNSUPPORTED_BOX;

	/**
	 * 既存の{@code collectable}判定と同義(このenumのみtrue、mode非依存)。
	 * B0.5〜B2時点の挙動をそのまま表す——B3以降の実際の収集許可判定には
	 * {@link #supportsPageSplitThrough}を使う。
	 */
	public boolean isCollectable() {
		return this == PLAIN_FLOW;
	}

	/**
	 * PAGE経由のsplit-throughを許可するかを判定します(2026-07-21新設、
	 * M6b Phase B B3)。「対象が何であるか」(このenum自体)と「現在の
	 * 破断modeで収集を許すか」を分離する——ChatGPT Pro相談で確認、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b3-multicol-split-through.md参照。
	 *
	 * <p>
	 * {@link #MULTICOL}は{@code BreakMode.ForceBreakMode}では許可しない。
	 * 強制改ページで選択されたチェーンメンバーの{@code
	 * splitForContinuation}が{@code KEEP}/{@code MOVE}を返すと、
	 * {@code FlowContainer.splitPageAxis}が無条件に
	 * {@code AssertionError("force break failed")}を投げる経路がある
	 * ため(`FlowContainer.java`の強制改ページ分岐で確認済み)。自動改ページ
	 * では、収集できなかった場合は安全に{@code
	 * ResumeProgram.LegacyTailCause.SplitStopped}へ落ちるため問題ない。
	 * </p>
	 */
	public boolean supportsPageSplitThrough(final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return switch (this) {
		case PLAIN_FLOW -> true;
		case MULTICOL -> !(mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode);
		default -> false;
		};
	}

	/**
	 * boxをrootFlowとの関係で分類します。
	 *
	 * @param b        分類対象(祖先チェーンの1レベル)
	 * @param rootFlow ルートボックスの書字方向
	 */
	public static ContinuationCapability classify(final net.zamasoft.foliojet.layout.box.AbstractContainerBox b,
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow) {
		if (!(b instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox)) {
			return UNSUPPORTED_BOX;
		}
		if (b.getClass() != net.zamasoft.foliojet.layout.box.impl.FlowBlockBox.class) {
			return b instanceof net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox ? MULTICOL : FLOW_SUBTYPE;
		}
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = ((net.zamasoft.foliojet.layout.box.impl.FlowBlockBox) b)
				.getBlockParams().flow;
		if (flow != rootFlow) {
			return flow.isVertical() != rootFlow.isVertical() ? ORTHOGONAL_FLOW : SAME_AXIS_DIRECTION_CHANGE;
		}
		// getColumnCount()<=1はexact-class FlowBlockBoxでは常に真
		// (AbstractContainerBox.getColumnCount()のデフォルトは1、段組は
		// MulticolumnBlockBoxという別クラスとしてのみ存在するため、この
		// 分岐は事実上到達しないが、旧実装の条件式との厳密な等価性を
		// 保つため維持する——2026-07-21のChatGPT Pro相談で確認)。
		return ((net.zamasoft.foliojet.layout.box.impl.FlowBlockBox) b).getColumnCount() <= 1 ? PLAIN_FLOW : MULTICOL;
	}
}
