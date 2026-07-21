package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * {@link Continuation}を読み取り専用の{@link PageResumeProgram}へ
 * コンパイルします(2026-07-21新設、M6b Phase B B2)。反復(明示ループ)で
 * {@code Continuation.ContinuationFrame}の{@code Child}連鎖を歩く——
 * 深さ5000程度でもJVM再帰なしに処理できる(既存{@code RootBuilder
 * .resumeFrame()}と同じ反復化パターン)。fragmentの実際の構築
 * ({@code recipe.instantiate()})・builder状態の変異は一切行わない。
 */
public final class ResumeProgramCompiler {
	private ResumeProgramCompiler() {
	}

	/**
	 * @param snapshot   破断時のflowStackスナップショット
	 * @param continuation コンパイル対象の継続記述
	 * @throws ContinuationInvariantViolationException 構造が破れている場合
	 */
	public static PageResumeProgram compile(final OpenPathSnapshot snapshot, final Continuation continuation) {
		if (continuation.depth() != snapshot.depth()) {
			throw new ContinuationInvariantViolationException(
					"snapshot depth=" + snapshot.depth() + ", continuation depth=" + continuation.depth());
		}

		final List<FragmentResumeLevel> levels = new ArrayList<>();
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
			levels.add(new FragmentResumeLevel(index, frame.recipe(), frame.state(), frame.container(),
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

				final ResumeTail tail = compileTail(snapshot, frameCount, openDepth);
				final PageResumeProgram program = new PageResumeProgram(snapshot, levels, tail,
						continuation.ranges());
				ContinuationVerifier.verify(program);
				return program;
			}
			case Continuation.OpenTail.MovedOpen(final int openDepth) -> {
				// 2026-07-22(M6b Phase B5c-2 Step1): OpenTailShapeと同じ深さ
				// 不変条件・LegacyOpen委譲(型だけ分離、実行は既存bridge)。
				// 現時点ではproducerが無いため実際には到達しない
				final int frameCount = levels.size();

				if (frameCount + openDepth - 1 != continuation.depth()) {
					throw new ContinuationInvariantViolationException("depth invariant failed: frames=" + frameCount
							+ ", tailDepth=" + openDepth + ", continuationDepth=" + continuation.depth());
				}

				final ResumeTail tail = new ResumeTail.MovedOpen(frameCount, openDepth);
				final PageResumeProgram program = new PageResumeProgram(snapshot, levels, tail,
						continuation.ranges());
				ContinuationVerifier.verify(program);
				return program;
			}
			}
		}
	}

	private static ResumeTail compileTail(final OpenPathSnapshot snapshot, final int frameCount,
			final int openDepth) {
		// frameCountは、levels直後の最初の未コンパイルopenPath indexでもある。
		if (frameCount == snapshot.depth()) {
			if (openDepth != 1) {
				throw new ContinuationInvariantViolationException(
						"fully compiled path must end in OpenText, but openDepth=" + openDepth);
			}
			return new ResumeTail.OpenText();
		}

		final LegacyTailCause cause;
		if (snapshot.firstBarrier().isPresent() && snapshot.firstBarrier().get().openPathIndex() == frameCount) {
			cause = new LegacyTailCause.CapabilityBarrier(snapshot.firstBarrier().get());
		} else {
			// capability上は承認済みだが、splitForContinuationがKEEP/MOVE等で
			// frame生成を途中で止めた(barrierより前、またはbarrier自体が
			// 無いのに停止した)ケース。
			cause = new LegacyTailCause.SplitStopped(frameCount);
		}
		return new ResumeTail.LegacyOpen(frameCount, openDepth, cause);
	}
}
