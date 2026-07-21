package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * {@link Continuation}を読み取り専用の{@link ResumeProgram}へコンパイル
 * します(2026-07-21新設、M6b Phase B B2)。反復(明示ループ)で
 * {@code Continuation.ContinuationFrame}の{@code Child}連鎖を歩く——
 * 深さ5000程度でもJVM再帰なしに処理できる(既存{@code RootBuilder
 * .resumeFrame()}と同じ反復化パターン)。fragmentの実際の構築
 * ({@code recipe.instantiate()})・builder状態の変異は一切行わない。
 */
public final class ResumeProgramCompiler {
	private ResumeProgramCompiler() {
	}

	/**
	 * @param target     PAGE/COLUMNのどちらの継続か
	 * @param snapshot   破断時のflowStackスナップショット
	 * @param continuation コンパイル対象の継続記述
	 * @throws ContinuationInvariantViolationException 構造が破れている場合
	 */
	public static ResumeProgram compile(final ResumeProgram.ResumeTarget target, final OpenPathSnapshot snapshot,
			final Continuation continuation) {
		if (continuation.depth() != snapshot.depth()) {
			throw new ContinuationInvariantViolationException(
					"snapshot depth=" + snapshot.depth() + ", continuation depth=" + continuation.depth());
		}

		final List<ResumeProgram.ResumeLevel> levels = new ArrayList<>();
		final Set<Continuation.ContinuationFrame> seen = Collections.newSetFromMap(new IdentityHashMap<>());

		Continuation.ContinuationFrame frame = continuation.root();
		int index = 0;
		while (true) {
			if (frame == null) {
				throw new ContinuationInvariantViolationException("null frame at index " + index);
			}
			if (!seen.add(frame)) {
				throw new ContinuationInvariantViolationException("cyclic frame chain at index " + index);
			}
			if (index >= snapshot.depth()) {
				throw new ContinuationInvariantViolationException("frame chain exceeds snapshot depth");
			}

			final OpenPathSnapshot.OpenLevelDescriptor descriptor = snapshot.levels().get(index);
			levels.add(new ResumeProgram.ResumeLevel(index, frame.recipe(), frame.state(), frame.container(),
					frame.crossExtent(), frame.prefixItems(), descriptor));

			switch (frame.tail()) {
			case Continuation.OpenTail.Child(final Continuation.ContinuationFrame child) -> {
				frame = child;
				++index;
			}
			case Continuation.OpenTail.OpenTailShape(final OpenShape shape) -> {
				final int openDepth = shape.depth();
				final int frameCount = levels.size();

				if (frameCount + openDepth - 1 != continuation.depth()) {
					throw new ContinuationInvariantViolationException("depth invariant failed: frames=" + frameCount
							+ ", tailDepth=" + openDepth + ", continuationDepth=" + continuation.depth());
				}

				final ResumeProgram.ResumeTail tail = compileTail(snapshot, frameCount, openDepth);
				final ResumeProgram program = new ResumeProgram(target, snapshot, levels, tail,
						continuation.ranges());
				ContinuationVerifier.verify(program);
				return program;
			}
			}
		}
	}

	private static ResumeProgram.ResumeTail compileTail(final OpenPathSnapshot snapshot, final int frameCount,
			final int openDepth) {
		// frameCountは、levels直後の最初の未コンパイルopenPath indexでもある。
		if (frameCount == snapshot.depth()) {
			if (openDepth != 1) {
				throw new ContinuationInvariantViolationException(
						"fully compiled path must end in OpenText, but openDepth=" + openDepth);
			}
			return new ResumeProgram.ResumeTail.OpenText();
		}

		final ResumeProgram.LegacyTailCause cause;
		if (snapshot.firstBarrier().isPresent() && snapshot.firstBarrier().get().openPathIndex() == frameCount) {
			cause = new ResumeProgram.LegacyTailCause.CapabilityBarrier(snapshot.firstBarrier().get());
		} else {
			// capability上は承認済みだが、splitForContinuationがKEEP/MOVE等で
			// frame生成を途中で止めた(barrierより前、またはbarrier自体が
			// 無いのに停止した)ケース。
			cause = new ResumeProgram.LegacyTailCause.SplitStopped(frameCount);
		}
		return new ResumeProgram.ResumeTail.LegacyOpen(frameCount, openDepth, cause);
	}
}
