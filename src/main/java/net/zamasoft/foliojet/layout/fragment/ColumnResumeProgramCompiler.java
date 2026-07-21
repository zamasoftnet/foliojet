package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * COLUMN継続のownerより内側の子孫チェーンを、読み取り専用の
 * {@link ColumnResumeProgram}へコンパイルします(2026-07-21新設、
 * M6b Phase B B4、未配線)。{@link ResumeProgramCompiler}と同じ反復
 * (明示ループ)パターンだが、index 1から開始する(index 0はowner
 * anchorであり、fragment levelではないため)。owner自身のfragment化は
 * 一切行わない——{@link PreparedColumnCut#childFrame()}がnullの場合
 * (ownerの直下に開いた子孫が全くない、または貫通しなかった場合)も
 * 正規のケースとして扱う。
 */
public final class ColumnResumeProgramCompiler {
	private ColumnResumeProgramCompiler() {
	}

	/**
	 * @param target    owner識別情報
	 * @param anchor    owner直下の残余
	 * @param snapshot  破断時の相対open pathスナップショット(index 0 = owner)
	 * @param childFrame owner直下で貫通した場合の継続フレーム(貫通しなければnull)
	 * @param ranges    閉部分木の再生範囲
	 * @param terminalStopReason owner直下自身が{@code PlainWithChainStop}
	 *                           (非空container)で打ち切られた場合の理由
	 *                           (2026-07-22新設、M6b Phase B5c-2 段階4。
	 *                           {@code childFrame==null}のときのみ意味を
	 *                           持つ。通常は{@code null})
	 * @throws ContinuationInvariantViolationException 構造が破れている場合
	 */
	public static ColumnResumeProgram compileColumn(final NextColumnTarget target, final ColumnAnchor anchor,
			final OpenPathSnapshot snapshot, final Continuation.ContinuationFrame childFrame,
			final Map<IBox, Continuation.SourceRange> ranges, final ChainStopReason terminalStopReason) {
		final List<FragmentResumeLevel> levels = new ArrayList<>();

		if (childFrame == null) {
			// ownerの切断がそもそも子孫を貫通しなかった(plan未選択、または
			// owner自身の直下に開いた子孫が存在しない)正規のケース。
			// openDepthはsnapshot.depth()をそのまま使う(depth-1ではない)
			// ——PAGE側のindex==0(全ボックスrestyle)ケースが、実測された
			// OpenTailShape.depth()(常にflowStack.size()と同じ値)を
			// そのままLegacyOpen.openDepthに使っているのと同じ規約。
			// owner自身はsnapshotのboxカウントに含まれるが、その「開き」は
			// 暗黙のOpenText 1単位として別勘定されるため、depth==1
			// (owner自身がsnapshotの唯一のレベル=子孫なし)でもOpenText
			// (openDepth==1)が正しい——実測でdepth-1を使うと
			// AssertionError(flowStack空)になることを確認して修正した。
			final ResumeTail tail;
			if (terminalStopReason == ChainStopReason.MOVE) {
				// 2026-07-22(段階4): owner直下自身がMOVEで打ち切られた場合、
				// MovedOpenで型付けする(codex設計相談で確認:
				// firstOpenPathIndex=1(=1+levels.size()、levelsは常に空)、
				// openDepth=snapshot.depth()——PAGE側のplan.openTailDepth()
				// を流用する必要はない)。depth==1(=OpenTextに相当する
				// 状況)でMOVEが起きるのは不変条件違反——owner自身が
				// 丸ごと動いたのに開きテキストすら残らないのは、chain
				// メンバーの選択と矛盾する
				if (snapshot.depth() <= 1) {
					throw new ContinuationInvariantViolationException(
							"MovedOpen at COLUMN owner anchor with snapshot.depth()=" + snapshot.depth());
				}
				tail = new ResumeTail.MovedOpen(1, snapshot.depth());
			} else if (snapshot.depth() == 1) {
				tail = new ResumeTail.OpenText();
			} else {
				final int firstUncompiled = 1;
				final int openDepth = snapshot.depth();
				final LegacyTailCause cause;
				if (snapshot.firstBarrier().isPresent()
						&& snapshot.firstBarrier().get().openPathIndex() == firstUncompiled) {
					cause = new LegacyTailCause.CapabilityBarrier(snapshot.firstBarrier().get());
				} else {
					cause = new LegacyTailCause.SplitStopped(firstUncompiled);
				}
				tail = new ResumeTail.LegacyOpen(firstUncompiled, openDepth, cause);
			}
			final ColumnResumeProgram program = new ColumnResumeProgram(target, anchor, snapshot, levels, tail,
					ranges);
			ColumnContinuationVerifier.verify(program);
			return program;
		}

		final Set<Continuation.ContinuationFrame> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Continuation.ContinuationFrame frame = childFrame;
		int index = 1;
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

				if (levels.size() + openDepth != snapshot.depth()) {
					throw new ContinuationInvariantViolationException("COLUMN depth invariant failed: levels="
							+ levels.size() + ", tailDepth=" + openDepth + ", snapshotDepth=" + snapshot.depth());
				}

				final int firstUncompiled = 1 + levels.size();
				final ResumeTail tail;
				if (firstUncompiled == snapshot.depth()) {
					if (openDepth != 1) {
						throw new ContinuationInvariantViolationException(
								"fully compiled path must end in OpenText, but openDepth=" + openDepth);
					}
					tail = new ResumeTail.OpenText();
				} else {
					final LegacyTailCause cause;
					if (snapshot.firstBarrier().isPresent()
							&& snapshot.firstBarrier().get().openPathIndex() == firstUncompiled) {
						cause = new LegacyTailCause.CapabilityBarrier(snapshot.firstBarrier().get());
					} else {
						cause = new LegacyTailCause.SplitStopped(firstUncompiled);
					}
					tail = new ResumeTail.LegacyOpen(firstUncompiled, openDepth, cause);
				}

				final ColumnResumeProgram program = new ColumnResumeProgram(target, anchor, snapshot, levels, tail,
						ranges);
				ColumnContinuationVerifier.verify(program);
				return program;
			}
			case Continuation.OpenTail.MovedOpen(final int openDepth) -> {
				// 2026-07-22(M6b Phase B5c-2 Step1): OpenTailShapeと同じ
				// COLUMN深さ不変条件。現時点ではproducerが無いため実際には
				// 到達しない
				if (levels.size() + openDepth != snapshot.depth()) {
					throw new ContinuationInvariantViolationException("COLUMN depth invariant failed: levels="
							+ levels.size() + ", tailDepth=" + openDepth + ", snapshotDepth=" + snapshot.depth());
				}

				final int firstUncompiled = 1 + levels.size();
				final ResumeTail tail = new ResumeTail.MovedOpen(firstUncompiled, openDepth);

				final ColumnResumeProgram program = new ColumnResumeProgram(target, anchor, snapshot, levels, tail,
						ranges);
				ColumnContinuationVerifier.verify(program);
				return program;
			}
			}
		}
	}
}
