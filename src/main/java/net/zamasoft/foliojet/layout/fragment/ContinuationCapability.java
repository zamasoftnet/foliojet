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
	 * 2026-07-22の改ページ契約(docs/history/2026-07-22-pagination
	 * -contract-consultation.md参照)により、この分類は{@link
	 * #ORTHOGONAL_FLOW}と同じく非収集(atomic)対象——収集可能に一時
	 * 解禁していた(B5b、2026-07-21)が、実装しやすさ優先の方針転換に
	 * より撤回した。{@code BreakableBuilder.startFlowBlock()}の
	 * {@code breakDepth}障壁も{@code isVertical()}だけでなく
	 * {@code WritingMode}完全一致で判定するよう同時に拡張済みのため、
	 * このボックスの祖先チェーンは実際にatomic(丸ごと収まるか丸ごと
	 * 次ページへ送られるか)になる。
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
	 * {@link #MULTICOL}はB3aで自動改ページのみ許可されていた——強制改ページで
	 * 選択されたチェーンメンバーの{@code splitForContinuation}が
	 * {@code KEEP}/{@code MOVE}を返すと、当時は{@code
	 * FlowContainer.splitPageAxis}が無条件に{@code
	 * AssertionError("force break failed")}を投げる経路があったため
	 * (`FlowContainer.java`の強制改ページ分岐で確認済み)。B3b-2
	 * (2026-07-21)でこの生の{@code AssertionError}を{@code
	 * AbstractBlockBox.splitForContinuation()}が明記する正規のKEEP/MOVE
	 * 処理へ書き換え、自動改ページ主ループ・{@code
	 * AbstractContainerBox.split()}と同型の処理に揃えたため、この障壁は
	 * 解消した(B3b-1、2026-07-21)。強制改ページ・改段いずれも、選択された
	 * チェーンメンバーが返す{@code KEEP}/{@code MOVE}/{@code Frame}は
	 * どの{@link ContinuationCapability}であっても同じ経路で処理される
	 * ため、mode依存の制限を維持する理由がなくなった。
	 * </p>
	 *
	 * <p>
	 * {@link #SAME_AXIS_DIRECTION_CHANGE}は2026-07-21(B5b)に一時解禁して
	 * いたが、2026-07-22の改ページ契約により撤回した(atomic対象、
	 * docs/history/2026-07-22-pagination-contract-consultation.md参照)。
	 * </p>
	 */
	public boolean supportsPageSplitThrough(final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return switch (this) {
		case PLAIN_FLOW, MULTICOL, FLOW_SUBTYPE -> true;
		default -> false;
		};
	}

	/**
	 * COLUMN経由(段組ownerより内側の子孫)のsplit-throughを許可するかを
	 * 判定します(2026-07-21新設、M6b Phase B B4、B3b-1で強制改段も解禁
	 * ——PAGE側の{@link #supportsPageSplitThrough}と同じ理由で、B3b-2
	 * により選択済みチェーンメンバーのKEEP/MOVE処理がmode非依存の正規
	 * 経路になったため、強制改段だけ制限する理由がなくなった)。
	 * descendant側の{@link #MULTICOL}(段組ownerの内側にさらに現れる
	 * 別の段組)は引き続き{@code LegacyOpen}のbarrierとして残す
	 * (owner自体は分類対象外、ownerの内側にさらに現れる別のMULTICOLだけ
	 * がこの分岐に到達する)。{@link #SAME_AXIS_DIRECTION_CHANGE}は
	 * {@link #supportsPageSplitThrough}と同じ理由で撤回した。
	 */
	public boolean supportsColumnSplitThrough(final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return switch (this) {
		case PLAIN_FLOW, FLOW_SUBTYPE -> true;
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
