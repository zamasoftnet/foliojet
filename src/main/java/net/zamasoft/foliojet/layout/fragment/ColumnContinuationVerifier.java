package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

/**
 * {@link ColumnResumeProgram}の構造的な不変条件を検証します(2026-07-21
 * 新設、M6b Phase B B4、未配線)。{@link ContinuationVerifier}のPAGE専用
 * 検証とは深さの数式が異なるため分離した——PAGEではroot自体がfragment
 * levelだが、COLUMNではowner(anchor)はfragment levelに含まれないため、
 * {@code fragmentLevels.size() + tailDepth == snapshotDepth}が不変条件に
 * なる(PAGEは{@code fragmentLevels.size() + tailDepth - 1 ==
 * snapshotDepth}。ChatGPT Pro相談で確認、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
 * 参照)。COLUMNでは{@code snapshot.depth()==1, fragmentLevels=0,
 * tail=OpenText}も正当なケースであり、PAGE用検証の「levelsが空なら
 * 例外」は適用しない。
 */
public final class ColumnContinuationVerifier {
	private ColumnContinuationVerifier() {
	}

	public static void verify(final ColumnResumeProgram program) {
		if (program.snapshot() == null || program.tail() == null || program.anchor() == null) {
			throw new ContinuationInvariantViolationException("ColumnResumeProgram has null snapshot/tail/anchor");
		}
		if (program.snapshot().depth() <= 0) {
			throw new ContinuationInvariantViolationException("snapshot depth must be positive");
		}

		final List<FragmentResumeLevel> levels = program.fragmentLevels();

		for (int i = 0; i < levels.size(); ++i) {
			final FragmentResumeLevel level = levels.get(i);
			final int expectedOpenPathIndex = i + 1;
			if (level.openPathIndex() != expectedOpenPathIndex) {
				throw new ContinuationInvariantViolationException(
						"level " + i + " has openPathIndex=" + level.openPathIndex() + ", expected "
								+ expectedOpenPathIndex);
			}
			if (level.descriptor().index() != expectedOpenPathIndex) {
				throw new ContinuationInvariantViolationException(
						"level " + i + " descriptor index=" + level.descriptor().index());
			}
			if (!Double.isFinite(level.crossExtent()) || level.crossExtent() < 0) {
				throw new ContinuationInvariantViolationException(
						"level " + i + " has invalid crossExtent=" + level.crossExtent());
			}

			int lastSerial = -1;
			for (final Continuation.SourceRange range : level.prefixItems()) {
				if (range.serial() <= lastSerial) {
					throw new ContinuationInvariantViolationException(
							"level " + i + " prefix serial not strictly increasing: " + range.serial());
				}
				lastSerial = range.serial();
				if (range.fromId() < 0 || range.toId() < range.fromId()) {
					throw new ContinuationInvariantViolationException(
							"level " + i + " has invalid source range: " + range);
				}
			}
		}

		switch (program.tail()) {
		case ResumeTail.OpenText openText -> {
			if (1 + levels.size() != program.snapshot().depth() || openText.openDepth() != 1) {
				throw new ContinuationInvariantViolationException("OpenText tail with incomplete levels (levels="
						+ levels.size() + ", snapshotDepth=" + program.snapshot().depth() + ")");
			}
		}
		case ResumeTail.LegacyOpen legacyOpen -> {
			if (legacyOpen.firstOpenPathIndex() != 1 + levels.size()) {
				throw new ContinuationInvariantViolationException(
						"LegacyOpen firstOpenPathIndex=" + legacyOpen.firstOpenPathIndex()
								+ " does not match 1 + levels.size()=" + (1 + levels.size()));
			}
		}
		case ResumeTail.MovedOpen movedOpen -> {
			// 2026-07-22(M6b Phase B5c-2 Step1・段階4)
			if (movedOpen.firstOpenPathIndex() != 1 + levels.size()) {
				throw new ContinuationInvariantViolationException(
						"MovedOpen firstOpenPathIndex=" + movedOpen.firstOpenPathIndex()
								+ " does not match 1 + levels.size()=" + (1 + levels.size()));
			}
			// 段階4(codex設計相談で確認): COLUMNの深さ不変条件は
			// fragmentLevels.size() + tailDepth == snapshotDepth
			if (levels.size() + movedOpen.openDepth() != program.snapshot().depth()) {
				throw new ContinuationInvariantViolationException(
						"MovedOpen depth invariant failed: levels=" + levels.size() + ", openDepth="
								+ movedOpen.openDepth() + ", snapshotDepth=" + program.snapshot().depth());
			}
		}
		}
	}
}
