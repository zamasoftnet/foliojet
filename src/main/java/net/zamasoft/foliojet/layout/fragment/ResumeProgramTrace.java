package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

/**
 * {@link PageResumeProgram}/{@code ColumnResumeProgram}が予告する意味
 * 操作列(expected)と、既存executor({@code RootBuilder.resumeFrame()}/
 * 将来のCOLUMN executor)が実際に選んだ操作(actual)を突き合わせます
 * (2026-07-21新設、M6b Phase B B2。B4でprogram型に依存しない
 * {@code List<ResumeOp>}コンストラクタへ一般化した——ChatGPT Pro相談、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
 * 参照)。
 *
 * <p>
 * セッションごとに1つ所有させること——ソース再生中に改ページが
 * 入れ子になりうるため、{@code ResumeTrace}のような単一static bufferには
 * しない(builderへ通常のメソッド引数として渡すことで、Java呼び出し
 * スタック自体が入れ子を自然に分離する)。
 * </p>
 */
public final class ResumeProgramTrace {
	private final List<ResumeOp> expected;
	private int cursor;

	public ResumeProgramTrace(final PageResumeProgram program) {
		this(ResumeOp.expectedOps(program));
	}

	public ResumeProgramTrace(final List<ResumeOp> expected) {
		this.expected = List.copyOf(expected);
	}

	/** {@code resumeFrame()}が実際に選んだ操作を記録・照合します。 */
	public void actual(final ResumeOp op) {
		if (this.cursor >= this.expected.size()) {
			throw new ResumeProgramMismatchException("余分なactual op: " + op);
		}
		final ResumeOp expectedOp = this.expected.get(this.cursor);
		if (!expectedOp.equals(op)) {
			throw new ResumeProgramMismatchException("op[" + this.cursor + "] expected=" + expectedOp + ", actual=" + op);
		}
		++this.cursor;
	}

	/** {@code resumeFrame()}正常終了後、全操作が消費されたことを確認します。 */
	public void verifyComplete() {
		if (this.cursor != this.expected.size()) {
			throw new ResumeProgramMismatchException(
					"操作列が途中で終了: consumed=" + this.cursor + ", expected=" + this.expected.size());
		}
	}
}
