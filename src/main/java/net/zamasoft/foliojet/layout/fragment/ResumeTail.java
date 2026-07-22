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
}
