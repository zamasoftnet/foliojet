package net.zamasoft.foliojet.layout.fragment;

import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * 破断時の継続({@link Continuation})を、読み取り専用の平坦な計画へ
 * コンパイルした結果です(2026-07-21新設、M6b Phase B B2)。
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
 * levelsが伸び、LegacyOpenの深さが縮む。
 * </p>
 *
 * @param target       PAGE/COLUMNのどちらの継続か(B2は{@link
 *                     ResumeTarget.NewPage}のみ)
 * @param snapshot     破断時に観測したflowStackのスナップショット
 * @param levels       first-classにコンパイルされたlevel(root含む)
 * @param tail         levelsの先の開いた続き
 * @param replayRanges 閉部分木の再生範囲({@link Continuation#ranges()}
 *                     をそのまま運ぶ)
 */
public record ResumeProgram(ResumeTarget target, OpenPathSnapshot snapshot, List<ResumeLevel> levels,
		ResumeTail tail, Map<IBox, Continuation.SourceRange> replayRanges) {

	public ResumeProgram {
		levels = List.copyOf(levels);
		replayRanges = Map.copyOf(replayRanges);
	}

	/**
	 * first-classにコンパイルされた1レベルです。{@code Continuation
	 * .ContinuationFrame}と1対1対応するが、tailを持たない(tailは
	 * {@link ResumeProgram#tail}へ集約する)。
	 */
	public record ResumeLevel(int openPathIndex, FragmentRecipe recipe, FragmentState state, Container remainder,
			double crossExtent, List<Continuation.SourceRange> prefixItems,
			OpenPathSnapshot.OpenLevelDescriptor descriptor) {

		public ResumeLevel {
			prefixItems = List.copyOf(prefixItems);
		}
	}

	/** levelsの先の開いた続きです。 */
	public sealed interface ResumeTail {
		/** 旧{@code OpenShape.depth()}相当の深さ。 */
		int openDepth();

		/** 全open boxがlevelへコンパイル済みで、残るのは開きテキストのみ。 */
		record OpenText() implements ResumeTail {
			public int openDepth() {
				return 1;
			}
		}

		/**
		 * 現行executor(box-restyle経由のOpenChain再帰)へ委譲する未
		 * コンパイルの続き。
		 *
		 * @param firstOpenPathIndex levels直後、未コンパイル部分の開始位置
		 * @param openDepth          未コンパイル部分の深さ
		 * @param cause              未コンパイルになった理由
		 */
		record LegacyOpen(int firstOpenPathIndex, int openDepth, LegacyTailCause cause) implements ResumeTail {
		}
	}

	/** {@link ResumeTail.LegacyOpen}が生じた理由です。 */
	public sealed interface LegacyTailCause {
		/** {@code OpenPathScan}の収集可能性判定で止まった(capability非対応)。 */
		record CapabilityBarrier(OpenPathSnapshot.CapabilityBarrier barrier) implements LegacyTailCause {
		}

		/**
		 * capability上は承認されたが、{@code splitForContinuation}が
		 * KEEP/MOVE等でframe生成を止めた——capability分類とは無関係の理由。
		 */
		record SplitStopped(int openPathIndex) implements LegacyTailCause {
		}
	}

	/** 継続の対象です。B2では{@link NewPage}のみ対応。 */
	public sealed interface ResumeTarget {
		/** ページ継続(RootBuilder.pageBreak経由)。 */
		record NewPage() implements ResumeTarget {
		}
	}
}
