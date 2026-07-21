package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

/**
 * {@link PageResumeProgram}の構造的な不変条件を検証します(2026-07-21新設、
 * M6b Phase B B2)。{@link ResumeProgramCompiler#compile}が最後に呼ぶ
 * ため、通常は既にここに来る時点で成立しているはずだが、将来の変更が
 * 不変条件を壊していないかを機械的に固定する独立した検証層として分離
 * する。
 *
 * <p>
 * PAGE専用の検証器である(B4でCOLUMN用の別検証器を追加する予定——PAGEでは
 * fragmentLevelsが非空であることを要求するが、COLUMNではowner直下が
 * 即座に開きテキストになるsnapshot depth==1のケースが正当なため、この
 * ままではCOLUMNへ流用できない。ChatGPT Pro相談で確認、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
 * 参照)。
 * </p>
 */
public final class ContinuationVerifier {
	private ContinuationVerifier() {
	}

	public static void verify(final PageResumeProgram program) {
		if (program.snapshot() == null || program.tail() == null) {
			throw new ContinuationInvariantViolationException("PageResumeProgram has null snapshot/tail");
		}
		if (program.snapshot().depth() <= 0) {
			throw new ContinuationInvariantViolationException("snapshot depth must be positive");
		}

		final List<FragmentResumeLevel> levels = program.fragmentLevels();
		if (levels.isEmpty()) {
			throw new ContinuationInvariantViolationException("PageResumeProgram has no levels");
		}

		for (int i = 0; i < levels.size(); ++i) {
			final FragmentResumeLevel level = levels.get(i);
			if (level.openPathIndex() != i) {
				throw new ContinuationInvariantViolationException(
						"level " + i + " has openPathIndex=" + level.openPathIndex());
			}
			if (level.descriptor().index() != i) {
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
			if (levels.size() != program.snapshot().depth() || openText.openDepth() != 1) {
				throw new ContinuationInvariantViolationException(
						"OpenText tail with incomplete levels (levels=" + levels.size() + ", snapshotDepth="
								+ program.snapshot().depth() + ")");
			}
		}
		case ResumeTail.LegacyOpen legacyOpen -> {
			if (legacyOpen.firstOpenPathIndex() != levels.size()) {
				throw new ContinuationInvariantViolationException(
						"LegacyOpen firstOpenPathIndex=" + legacyOpen.firstOpenPathIndex()
								+ " does not match levels.size()=" + levels.size());
			}
		}
		}
	}
}
