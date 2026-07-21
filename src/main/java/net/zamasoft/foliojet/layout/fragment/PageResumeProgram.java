package net.zamasoft.foliojet.layout.fragment;

import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 破断時の継続({@link Continuation})を、読み取り専用の平坦な計画へ
 * コンパイルしたPAGE継続の結果です(2026-07-21新設、M6b Phase B B2。
 * B4で{@code ResumeProgram}から改名し、{@link ContinuationProgram}の
 * PAGE側実装とした——PAGEはただ1種類の継続対象しか持たないため、旧
 * {@code ResumeTarget.NewPage}という単一値のtargetフィールドは
 * {@link ColumnResumeProgram}との型自体による判別に置き換えて削除した)。
 *
 * <p>
 * B2時点では既存executor({@code RootBuilder.resumeFrame()})は変更せず、
 * この計画は「実行すればこうなるはず」という予告(shadow)としてのみ
 * 使う——{@link ResumeOp#expectedOps}が予告する操作列と、既存executorが
 * 実際に選んだ操作列を突き合わせる({@link ResumeProgramTrace}参照)。
 * </p>
 *
 * <p>
 * 末尾({@link ResumeTail})は現行{@code ContinuationFrame}として実在
 * するlevelの先(collectable prefixの外)を必ずしも表現しない——
 * {@link ResumeTail.LegacyOpen}として、未対応の理由({@link
 * LegacyTailCause})付きで保持する。B3以降、対応範囲が広がるにつれて
 * fragmentLevelsが伸び、LegacyOpenの深さが縮む。
 * </p>
 *
 * @param snapshot      破断時に観測したflowStackのスナップショット
 * @param fragmentLevels first-classにコンパイルされたlevel(root含む)
 * @param tail          fragmentLevelsの先の開いた続き
 * @param replayRanges  閉部分木の再生範囲({@link Continuation#ranges()}
 *                      をそのまま運ぶ)
 */
public record PageResumeProgram(OpenPathSnapshot snapshot, List<FragmentResumeLevel> fragmentLevels, ResumeTail tail,
		Map<IBox, Continuation.SourceRange> replayRanges) implements ContinuationProgram {

	public PageResumeProgram {
		fragmentLevels = List.copyOf(fragmentLevels);
		replayRanges = Map.copyOf(replayRanges);
	}
}
