package net.zamasoft.foliojet.layout.fragment;

/**
 * {@link ContinuationProgram#fragmentLevels()}の先の開いた続きです
 * (2026-07-21新設、M6b Phase B B4。旧{@code ResumeProgram.ResumeTail}を
 * PAGE/COLUMN共有型として抽出した)。
 */
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

	/**
	 * plan選択済みチェーンメンバーがMOVEで打ち切られた継続
	 * ({@link Continuation.OpenTail.MovedOpen}由来、2026-07-22新設、
	 * M6b Phase B5c-2 Step1)。{@link LegacyOpen}の
	 * {@code LegacyTailCause.SplitStopped}相当だが、KEEP/MOVE混同を
	 * 分離するため独立したvariantにする。実行はB5c-2完了までlegacy
	 * bridge(box-restyle経由のOpenChain再帰)へ委譲する——型だけを
	 * 先に分離する。
	 *
	 * @param firstOpenPathIndex levels直後、未コンパイル部分の開始位置
	 * @param openDepth          未コンパイル部分の深さ
	 */
	record MovedOpen(int firstOpenPathIndex, int openDepth) implements ResumeTail {
	}
}
