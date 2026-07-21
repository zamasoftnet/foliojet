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
	 * {@code FlowBlockBox}の{@link #MULTICOL}以外のサブタイプ(例:
	 * {@code RubyBodyBox})、かつanchorと同一書字方向。{@code getClass()
	 * == FlowBlockBox.class}という完全一致判定により機械的に除外される。
	 * 2026-07-21(B5)、軸判定をサブタイプ判定より先に行うよう{@link
	 * #classify}を修正した——直交writing-modeのサブタイプ(例:
	 * 横書きroot内の縦書きRubyBodyBox)を誤って{@code FLOW_SUBTYPE}
	 * (収集許可)に分類せず{@link #ORTHOGONAL_FLOW}(収集不許可)に
	 * 分類するため(codexへの設計相談で発見)。
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
	 * LegacyTailCause.SplitStopped}へ落ちるため問題ない。
	 * </p>
	 */
	public boolean supportsPageSplitThrough(final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return switch (this) {
		case PLAIN_FLOW -> true;
		case MULTICOL -> !(mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode);
		// 2026-07-21(B5): RubyBodyBox(唯一のFLOW_SUBTYPE実装、
		// fragmentRecipe()のresolvedAlign欠落は修正済み)・RL/LR混在
		// (SAME_AXIS_DIRECTION_CHANGE、crossExtent/FragmentStateは
		// isVertical()のみに依存しRL/LRの向きを見ないため意味論上安全)を
		// 解禁する。MULTICOLと同様、Force改ページでは見送る——B3b-2で
		// force split自体はKEEP/MOVEを安全に処理するようになったが、
		// force+これらの新規admissionの組み合わせは未検証のため、まずは
		// Autoのみに限定する(将来のB3b-1と合わせて再検討する)。
		case FLOW_SUBTYPE, SAME_AXIS_DIRECTION_CHANGE ->
			!(mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode);
		default -> false;
		};
	}

	/**
	 * COLUMN経由(段組ownerより内側の子孫)のsplit-throughを許可するかを
	 * 判定します(2026-07-21新設、M6b Phase B B4、未配線)。B4の最初の
	 * sliceでは{@link #PLAIN_FLOW}のみ、かつ自動改段(Force以外)に限る
	 * ——ChatGPT Pro相談で確認、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
	 * 参照。強制COLUMNの新しいselected-frame経路は、force split
	 * hardening(B3b-2の続き)が終わるまで非許可のままにする。
	 * descendant側の{@link #MULTICOL}(段組ownerの内側にさらに現れる別の
	 * 段組)は当面{@code LegacyOpen}のbarrierとして残す。
	 */
	public boolean supportsColumnSplitThrough(final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return switch (this) {
		case PLAIN_FLOW ->
			!(mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode);
		// 2026-07-21(B5): PAGE側と同じ理由でRubyBodyBox・RL/LR混在を
		// COLUMN側でも自動改段のみ解禁する。
		case FLOW_SUBTYPE, SAME_AXIS_DIRECTION_CHANGE ->
			!(mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode);
		default -> false;
		};
	}

	/**
	 * boxをanchorFlowとの関係で分類します。
	 *
	 * @param b          分類対象(open pathの1レベル)
	 * @param anchorFlow anchor(PAGE rootまたはCOLUMN owner)の書字方向
	 */
	public static ContinuationCapability classify(final net.zamasoft.foliojet.layout.box.AbstractContainerBox b,
			final net.zamasoft.foliojet.layout.box.params.WritingMode anchorFlow) {
		if (!(b instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox)) {
			return UNSUPPORTED_BOX;
		}
		// 2026-07-21(B5): 軸判定をサブタイプ判定より先に行う(旧実装は
		// exact-classチェックが先だったため、直交writing-modeの
		// MulticolumnBlockBox/RubyBodyBox等がORTHOGONAL_FLOWではなく
		// MULTICOL/FLOW_SUBTYPEに誤分類されていた——codexへの設計相談で
		// 発見)。全FlowBlockBoxサブタイプに対し、まず軸の一致・不一致を
		// 一律に判定してから、一致する場合のみサブタイプ固有の分類へ進む。
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = ((net.zamasoft.foliojet.layout.box.impl.FlowBlockBox) b)
				.getBlockParams().flow;
		if (flow != anchorFlow) {
			return flow.isVertical() != anchorFlow.isVertical() ? ORTHOGONAL_FLOW : SAME_AXIS_DIRECTION_CHANGE;
		}
		if (b.getClass() != net.zamasoft.foliojet.layout.box.impl.FlowBlockBox.class) {
			return b instanceof net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox ? MULTICOL : FLOW_SUBTYPE;
		}
		// getColumnCount()<=1はexact-class FlowBlockBoxでは常に真
		// (AbstractContainerBox.getColumnCount()のデフォルトは1、段組は
		// MulticolumnBlockBoxという別クラスとしてのみ存在するため、この
		// 分岐は事実上到達しないが、旧実装の条件式との厳密な等価性を
		// 保つため維持する——2026-07-21のChatGPT Pro相談で確認)。
		return ((net.zamasoft.foliojet.layout.box.impl.FlowBlockBox) b).getColumnCount() <= 1 ? PLAIN_FLOW : MULTICOL;
	}
}
