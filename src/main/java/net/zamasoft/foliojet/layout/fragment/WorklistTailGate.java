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
	 * 開きテキストのみ)。legacy/worklistの区別自体が無意味な
	 * ケースで、頻度計測({@link ContinuationStats#recordWorklistTerminal})
	 * にも含めない。
	 */
	NO_LEGACY_OPEN_TAIL,
	/**
	 * 未収集のlegacy開きが残り、そこに{@link ContinuationCapability
	 * #PLAIN_FLOW}以外のレベル(float・absolute・書字方向不一致
	 * 祖先等)が含まれる。改ページ契約(ARCHITECTURE.md §5.10)どおり
	 * legacy OpenChain再帰(段組貫通MOVE専用の経路)で駆動する。
	 */
	LEGACY_RECURSION,
	/**
	 * 未収集のlegacy開きが残るが、その全レベルが{@link
	 * ContinuationCapability#PLAIN_FLOW}(幹と同じ書字方向の素の
	 * FlowBlockBoxの一直線)。worklist executorで駆動する。
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
					final ContinuationCapability capability)) || capability != ContinuationCapability.PLAIN_FLOW) {
				return LEGACY_RECURSION;
			}
		}
		return WORKLIST_ELIGIBLE;
	}
}
