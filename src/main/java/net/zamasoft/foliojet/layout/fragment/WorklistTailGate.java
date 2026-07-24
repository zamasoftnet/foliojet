package net.zamasoft.foliojet.layout.fragment;

/**
 * 残存tail({@link ResumeTail.LegacyOpen})をworklist executorで駆動するか
 * どうかの適格判定です(2026-07-24、B6検証インフラ退役時に
 * {@code PlainFlowTailProgram}のshadow予測から実行に必要な判定だけを
 * 引き継いだ——広範囲検証(12+414+591文書・3層検証すべてlegacyと完全
 * 一致)を経て`foliojet.openTailExecutor`切替スイッチは撤去済み。
 * 適格なら常にworklist executor、不適格なら従来のlegacy再帰
 * ({@code FlowContainer.restyle()}の{@code OpenChain}再帰)が使われる)。
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
	 * tailが{@link ResumeTail.LegacyOpen}ではない(完全収集済み、または
	 * 終端が開きテキストのみ)。legacy/worklistの区別自体が無意味な
	 * ケースで、頻度計測({@link ContinuationStats#recordWorklistTerminal})
	 * にも含めない。
	 */
	NO_LEGACY_OPEN_TAIL,
	/**
	 * tailはLegacyOpenだが、残存tailに{@link ContinuationCapability
	 * #PLAIN_FLOW}以外のレベル(ruby・float・absolute・書字方向不一致
	 * 祖先等)が含まれる。改ページ契約(ARCHITECTURE.md §5.10)どおり
	 * legacy OpenChain再帰(段組貫通MOVE専用の経路)で駆動する。
	 */
	LEGACY_RECURSION,
	/**
	 * tailはLegacyOpenで、残存tailの全レベルが{@link
	 * ContinuationCapability#PLAIN_FLOW}(幹と同じ書字方向の素の
	 * FlowBlockBoxの一直線)。worklist executorで駆動する。
	 */
	WORKLIST_ELIGIBLE;

	/**
	 * {@code program.tail()}から適格性を判定します。判定基準は退役前の
	 * {@code PlainFlowTailProgram.compile()}の{@code allPlainFlow}と同一。
	 */
	public static WorklistTailGate of(final ContinuationProgram program) {
		if (!(program.tail() instanceof ResumeTail.LegacyOpen legacyOpen)) {
			return NO_LEGACY_OPEN_TAIL;
		}
		final OpenPathSnapshot snapshot = program.snapshot();
		for (int i = legacyOpen.firstOpenPathIndex(); i < snapshot.depth(); ++i) {
			if (!(snapshot.levels().get(i).role() instanceof OpenPathSnapshot.OpenLevelRole.Ancestor(
					final ContinuationCapability capability)) || capability != ContinuationCapability.PLAIN_FLOW) {
				return LEGACY_RECURSION;
			}
		}
		return WORKLIST_ELIGIBLE;
	}

	/**
	 * program({@code ResumeTail})を経由せず、snapshot+検証済みopen path形
	 * ({@link ContinuationValidator.PathShape}=first-classに歩けたframe数と
	 * 終端{@link OpenShape})から適格性を直接導出します(2026-07-24、E-3
	 * 増分3。docs/consultations/consult-e3-single-source-codex.md §3)。
	 *
	 * <p>
	 * 旧{@link #of(ContinuationProgram)}との対応: {@code ResumeTail}が
	 * {@code LegacyOpen}になるのは未収集レベルが残る場合、すなわち
	 * {@code firstOpenPathIndex < snapshot.depth()}の場合に限られ、その
	 * {@code LegacyOpen.firstOpenPathIndex}は{@code PathShape
	 * .firstOpenPathIndex()}と同一の値である(PAGE compilerでは
	 * frame数、COLUMN compilerでは1+チェーンframe数がそのまま
	 * firstOpenPathIndexになる)。したがって両判定は全入力で一致する——
	 * 一致は{@code WorklistTailGateTest}の新旧比較で固定している。
	 * </p>
	 */
	public static WorklistTailGate of(final OpenPathSnapshot snapshot,
			final ContinuationValidator.PathShape pathShape) {
		return ofUncompiledLevels(snapshot, pathShape.firstOpenPathIndex());
	}

	/**
	 * 未収集レベル{@code [firstOpenPathIndex, snapshot.depth())}のroleだけ
	 * から判定する共通実装です(有界ループ。分類はsnapshot捕捉時に一度だけ
	 * 計算済みの{@link OpenPathSnapshot.OpenLevelDescriptor#role()}を読む)。
	 */
	private static WorklistTailGate ofUncompiledLevels(final OpenPathSnapshot snapshot, final int firstOpenPathIndex) {
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
