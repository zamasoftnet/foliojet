package net.zamasoft.foliojet.layout.fragment;

import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 破断時の継続を、読み取り専用の平坦な計画へコンパイルしたCOLUMN継続の
 * 結果です(2026-07-21新設、M6b Phase B B4、未配線・型のみ)。
 *
 * <p>
 * PAGE継続({@link PageResumeProgram})との違いは、ownerボックス自体は
 * fragment再構成されない(同一インスタンスへ新column追加のみ)ため、
 * index 0相当のlevelを持たず、代わりに{@link #anchor()}でowner直下の
 * 残余を表す点である。{@link #fragmentLevels()}はowner<b>より内側</b>の
 * 子孫だけを、PAGEと同じ{@link FragmentRecipe}/{@link FragmentState}で
 * 表す(index 1から開始)。
 * </p>
 *
 * @param target         owner識別情報(commit直前のidentity検証にのみ使う)
 * @param anchor         owner直下の残余
 * @param snapshot       破断時に観測した相対open pathのスナップショット
 *                       (index 0 = COLUMN_OWNER anchor)
 * @param fragmentLevels first-classにコンパイルされたowner descendant
 *                       のlevel(index 1から)
 * @param tail           fragmentLevelsの先の開いた続き
 * @param replayRanges   閉部分木の再生範囲
 */
public record ColumnResumeProgram(NextColumnTarget target, ColumnAnchor anchor, OpenPathSnapshot snapshot,
		List<FragmentResumeLevel> fragmentLevels, ResumeTail tail, Map<IBox, Continuation.SourceRange> replayRanges)
		implements ContinuationProgram {

	public ColumnResumeProgram {
		fragmentLevels = List.copyOf(fragmentLevels);
		replayRanges = Map.copyOf(replayRanges);
	}
}
