package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code RootBuilder.resumeFrame()}の呼び出し境界における意味操作です
 * (2026-07-21新設、M6b Phase B B2)。{@code FlowContainer.restyle}内部の
 * 全操作(replay-subtree/text-tail等)までは予測しない——それは
 * shadow compilerではなく未完成の第二executorになってしまうため
 * (ChatGPT Pro相談で指摘)。{@link #expectedOps}が{@link PageResumeProgram}
 * から予告する操作列と、{@code resumeFrame()}が実際に選んだ操作列
 * ({@link ResumeProgramTrace}経由)を突き合わせる。
 */
public sealed interface ResumeOp {
	/** レベル{@code level}のfragmentがrecipeから再構成された。 */
	record Instantiate(int level, OpenPathSnapshot.FragmentSignature fragment) implements ResumeOp {
	}

	/** レベル{@code level}で{@code startFlowBlock}が呼ばれた。 */
	record StartFlow(int level) implements ResumeOp {
	}

	/** レベル{@code level}のコンテナが{@code restyleFrame}で再開された。 */
	record RestyleFrame(int level, TailMode mode, int openDepth, List<Continuation.SourceRange> prefix)
			implements ResumeOp {
	}

	/** レベル{@code level}で収集不能な破断(チェーンなし)の全ボックスrestyle。 */
	record RestyleWholeBox(int level, int openDepth) implements ResumeOp {
	}

	/**
	 * COLUMN継続専用(2026-07-21新設、M6b Phase B B4、未配線):
	 * {@code AbstractContainerBox.commitPreparedColumn()}によりownerへ
	 * 新columnが1つ追加された。
	 */
	record CommitNextColumn(OpenPathSnapshot.FragmentSignature owner, int oldActualColumnCount,
			int newActualColumnCount) implements ResumeOp {
	}

	/**
	 * COLUMN継続専用(2026-07-21新設、M6b Phase B B4、未配線):
	 * {@link ColumnAnchor#remainder()}がclosedとして再生された。
	 */
	record RestyleColumnAnchor(int openDepth, List<Continuation.SourceRange> prefix) implements ResumeOp {
	}

	enum TailMode {
		CLOSED_CHILD, OPEN_TAIL
	}

	/**
	 * {@link PageResumeProgram}から、既存{@code resumeFrame()}実行時に
	 * 期待される操作列を機械的に導出します。副作用なし(fragment生成・
	 * builder状態変異は一切行わない)。
	 */
	static List<ResumeOp> expectedOps(final PageResumeProgram program) {
		final List<ResumeOp> ops = new ArrayList<>();
		final List<FragmentResumeLevel> levels = program.fragmentLevels();

		for (int i = 0; i < levels.size(); ++i) {
			final FragmentResumeLevel level = levels.get(i);
			final boolean terminal = i == levels.size() - 1;

			ops.add(new Instantiate(i, level.descriptor().fragmentSignature()));

			if (!terminal) {
				ops.add(new StartFlow(i));
				ops.add(new RestyleFrame(i, TailMode.CLOSED_CHILD, 0, level.prefixItems()));
				continue;
			}

			if (i == 0) {
				// 現行 resumeFrame() の index == 0 分岐(収集不能な破断)
				ops.add(new RestyleWholeBox(i, program.tail().openDepth()));
			} else {
				ops.add(new StartFlow(i));
				ops.add(new RestyleFrame(i, TailMode.OPEN_TAIL, program.tail().openDepth(), level.prefixItems()));
			}
		}
		return List.copyOf(ops);
	}

	/**
	 * {@link ColumnResumeProgram}から、owner内側のfragment chain実行時に
	 * 期待される操作列を機械的に導出します(2026-07-21新設、
	 * M6b Phase B4-Step4)。PAGE版と異なり、index==0(収集不能な破断の
	 * 全ボックスrestyle)分岐は存在しない——COLUMNのfragment levelは
	 * 常にindex 1から始まり、owner(index 0)自体はfragment levelではなく
	 * {@link ColumnAnchor}として別途扱われるため(anchor自体の再生は
	 * このshadow比較の対象外——{@code RootBuilder.resumeColumn()}が
	 * fragment chain実行の前後で直接行う)。{@code fragmentLevels}が
	 * 空(owner直下に開いた子孫が全く無い、または貫通しなかった)場合は
	 * 空リストを返す——この場合、fragment chain実行自体が呼ばれない。
	 */
	static List<ResumeOp> expectedOps(final ColumnResumeProgram program) {
		final List<ResumeOp> ops = new ArrayList<>();
		final List<FragmentResumeLevel> levels = program.fragmentLevels();

		for (int i = 0; i < levels.size(); ++i) {
			final FragmentResumeLevel level = levels.get(i);
			final boolean terminal = i == levels.size() - 1;
			final int index = level.openPathIndex();

			ops.add(new Instantiate(index, level.descriptor().fragmentSignature()));

			if (!terminal) {
				ops.add(new StartFlow(index));
				ops.add(new RestyleFrame(index, TailMode.CLOSED_CHILD, 0, level.prefixItems()));
				continue;
			}

			ops.add(new StartFlow(index));
			ops.add(new RestyleFrame(index, TailMode.OPEN_TAIL, program.tail().openDepth(), level.prefixItems()));
		}
		return List.copyOf(ops);
	}
}
