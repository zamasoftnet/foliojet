package net.zamasoft.foliojet.layout.fragment;

import java.util.List;
import java.util.Optional;

/**
 * {@link ResumeTail.LegacyOpen}だけを対象にした読み取り専用のshadow予測
 * です(2026-07-22新設、B6a0——codex設計相談で確定、
 * {@code docs/history/2026-07-22-b6a0-plain-flow-tail-shadow-design.md}
 * 参照)。
 *
 * <p>
 * {@link ContinuationProgram#snapshot()}(破断時に一度だけ観測した
 * {@code flowStack}のスナップショット)のうち、{@link ResumeTail
 * .LegacyOpen#firstOpenPathIndex()}以降(=first-classにコンパイル
 * できなかった残存tail)だけを切り出す。recipeのinstantiateや
 * コンテナ変更はしない——{@code ContinuationProgram}から読むだけの
 * 純粋な変換であり、legacy executor(現行の`FlowContainer.restyle()`が
 * 行う`OpenChain`再帰)の実行を一切変えない。
 * </p>
 *
 * <p>
 * 「plain flowか」の判定は{@link ContinuationCapability#classify}を
 * ここで再計算せず、{@link OpenPathSnapshot.OpenLevelDescriptor#role()}
 * (破断時に一度だけ実boxへ対して計算済みの分類結果、{@code
 * OpenPathSnapshot}の不変条件どおり再分類しない)をそのまま読む——
 * 軸判定をサブタイプ判定より先に行うといった{@code classify}の細かい
 * 分岐をここで独自に再実装すると、既存の分類ロジックと将来ずれる
 * リスクがある。
 * </p>
 *
 * <p>
 * 改ページ契約(ARCHITECTURE.md §5.10)のもとでは、この残存tailが
 * 表現しうる形状は「幹と同じ書字方向の素の{@code FlowBlockBox}の
 * 一直線」({@link ContinuationCapability#PLAIN_FLOW})だけのはずである
 * (ruby・float・absolute・書字方向不一致はPhase 2でatomic化済み、表は
 * 行境界を除き対象外)。{@link #allPlainFlow()}はこの予測が実際に
 * 成り立つかを表す——成り立たない場合(audit対象、例: 直交writing-mode
 * の表がsplit-throughを試みた等)は、この一手では削除も強制もせず、
 * 統計へ記録するだけに留める(codexの助言どおり「いきなり削除」では
 * なくreachability検証から始める)。
 * </p>
 *
 * @param levels       残存tailの各レベル(index 0 = firstOpenPathIndex)。
 *                     終端の開きテキスト自体はこのlevelsに含まれない
 *                     ({@link ResumeTail.LegacyOpen#openDepth()}は
 *                     このlevelsの個数+1(終端テキスト分)に等しい)。
 * @param allPlainFlow 全レベルの{@code role()}が{@link
 *                     ContinuationCapability#PLAIN_FLOW}だった場合のみ
 *                     true。
 */
public record PlainFlowTailProgram(List<OpenPathSnapshot.OpenLevelDescriptor> levels, boolean allPlainFlow) {

	public PlainFlowTailProgram {
		levels = List.copyOf(levels);
	}

	/**
	 * {@code program.tail()}が{@link ResumeTail.LegacyOpen}の場合のみ、
	 * 対応する{@code PlainFlowTailProgram}を返す(それ以外——tailが
	 * {@link ResumeTail.OpenText}——は予測すべき残存tailが無いため空)。
	 */
	public static Optional<PlainFlowTailProgram> compile(final ContinuationProgram program) {
		if (!(program.tail() instanceof ResumeTail.LegacyOpen legacyOpen)) {
			return Optional.empty();
		}
		final OpenPathSnapshot snapshot = program.snapshot();
		final int from = legacyOpen.firstOpenPathIndex();
		final int to = snapshot.depth();
		final List<OpenPathSnapshot.OpenLevelDescriptor> levels = snapshot.levels().subList(from, to);
		boolean allPlainFlow = true;
		for (final OpenPathSnapshot.OpenLevelDescriptor level : levels) {
			if (!(level.role() instanceof OpenPathSnapshot.OpenLevelRole.Ancestor(
					final ContinuationCapability capability)) || capability != ContinuationCapability.PLAIN_FLOW) {
				allPlainFlow = false;
				break;
			}
		}
		return Optional.of(new PlainFlowTailProgram(levels, allPlainFlow));
	}
}
