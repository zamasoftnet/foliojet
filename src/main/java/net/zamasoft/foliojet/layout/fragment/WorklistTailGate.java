package net.zamasoft.foliojet.layout.fragment;

/**
 * 残存tail(未収集のlegacy開き)をworklist executorで駆動するかどうかの
 * 適格判定です(2026-07-24、B6検証インフラ退役時に
 * {@code PlainFlowTailProgram}のshadow予測から実行に必要な判定だけを
 * 引き継いだ——広範囲検証(12+414+591文書・3層検証すべてlegacyと完全
 * 一致)を経て`foliojet.openTailExecutor`切替スイッチは撤去済み。
 * 適格なら常にworklist executor、不適格なら従来のlegacy再帰
 * ({@code FlowContainer.restyle()}の{@code OpenChain}再帰)が使われる)。
 * E-3増分6でprogram({@code ResumeTail})経由の旧判定を撤去し、snapshot+
 * 検証済みopen path形({@link ContinuationValidator.PathShape})からの
 * 直接導出のみを残した(旧判定との全ケース一致は増分3〜5の新旧比較
 * テストで確認済み)。
 *
 * <p>
 * 「plain flowか」の判定は{@link ContinuationCapability#classify}をここで
 * 再計算せず、{@link OpenPathSnapshot.OpenLevelDescriptor#role()}(破断時に
 * 一度だけ実boxへ対して計算済みの分類結果)をそのまま読む——既存の分類
 * ロジックと将来ずれるリスクを避けるため(B6a0設計の踏襲)。
 * </p>
 */
public enum WorklistTailGate {
	/**
	 * 未収集レベルが残っていない(完全収集済みで、残るのは終端の
	 * 開きテキストのみ)。legacy/worklistの区別自体が無意味なケース。
	 */
	NO_LEGACY_OPEN_TAIL,
	/**
	 * 未収集のlegacy開きが残り、そこに{@link ContinuationCapability
	 * #PLAIN_FLOW}・{@link ContinuationCapability#MULTICOL}以外のレベル
	 * (書字方向不一致祖先・{@code UNSUPPORTED_BOX}等)が含まれる。
	 * 改ページ契約(ARCHITECTURE.md §5.10)の障壁により通常は到達しない
	 * 設計だが、gateとしてはfail closed(legacy OpenChain再帰)へ倒す。
	 *
	 * <p>
	 * 経緯: 2026-07-25(F-4)時点ではMULTICOLもここへ落ちていた
	 * (「収集不可かつ貫通可」の唯一の分類のため、段組レベルは必ず
	 * 未収集tail側に残る——436文書実測で3件: columns-float・page-first・
	 * probe-nested)。2026-07-30の増分1でworklist executorがMULTICOLを
	 * native scope({@code FlowContainer.MulticolRestyleScope})として
	 * 降下できることをlegacy駆動とのバイト等価で証明し
	 * ({@code MulticolWorklistScopeTest})、増分2で残存許可を
	 * {@code PLAIN_FLOW || MULTICOL}へ拡張した(codex相談
	 * consult-codex-2026-07-30-multicol-descent-proof.txt §5)。
	 * </p>
	 */
	LEGACY_RECURSION,
	/**
	 * 未収集のlegacy開きが残るが、その全レベルが{@link
	 * ContinuationCapability#PLAIN_FLOW}または{@link
	 * ContinuationCapability#MULTICOL}(幹と同じ書字方向)。worklist
	 * executorで駆動する(MULTICOLはnative scope降下——増分2で許可)。
	 */
	WORKLIST_ELIGIBLE;

	/**
	 * snapshot+検証済みopen path形({@link ContinuationValidator.PathShape}
	 * =first-classに歩けたframe数と終端{@link OpenShape})から適格性を
	 * 直接導出します(2026-07-24、E-3増分3。判定基準は退役前の
	 * {@code PlainFlowTailProgram.compile()}の{@code allPlainFlow}と同一)。
	 * 未収集レベル{@code [firstOpenPathIndex, snapshot.depth())}のroleだけを
	 * 有界ループで読む。
	 */
	public static WorklistTailGate of(final OpenPathSnapshot snapshot,
			final ContinuationValidator.PathShape pathShape) {
		final int firstOpenPathIndex = pathShape.firstOpenPathIndex();
		if (firstOpenPathIndex >= snapshot.depth()) {
			return NO_LEGACY_OPEN_TAIL;
		}
		for (int i = firstOpenPathIndex; i < snapshot.depth(); ++i) {
			if (!(snapshot.levels().get(i).role() instanceof OpenPathSnapshot.OpenLevelRole.Ancestor(
					final ContinuationCapability capability))
					|| (capability != ContinuationCapability.PLAIN_FLOW
							&& capability != ContinuationCapability.MULTICOL)) {
				return LEGACY_RECURSION;
			}
		}
		return WORKLIST_ELIGIBLE;
	}
}
