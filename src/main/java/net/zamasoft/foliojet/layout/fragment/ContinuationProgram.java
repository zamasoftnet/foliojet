package net.zamasoft.foliojet.layout.fragment;

import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 破断時の継続を、読み取り専用の平坦な計画へコンパイルした結果の共通型
 * です(2026-07-21新設、M6b Phase B B4)。PAGE継続({@link PageResumeProgram}、
 * 旧{@code ResumeProgram})とCOLUMN継続({@link ColumnResumeProgram})は、
 * ownerボックスのfragment化有無という非対称性があるため単純な共通レコード
 * にはせず、{@code fragmentLevels}/{@code tail}/{@code replayRanges}という
 * 共有アクセサだけをこのsealed interfaceへ出す(ChatGPT Pro相談、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
 * 参照)。
 */
public sealed interface ContinuationProgram permits PageResumeProgram, ColumnResumeProgram {
	OpenPathSnapshot snapshot();

	List<FragmentResumeLevel> fragmentLevels();

	ResumeTail tail();

	Map<IBox, Continuation.SourceRange> replayRanges();
}
