package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

/**
 * {@link ResumeProgram}が予告する意味操作列(expected)と、既存executor
 * ({@code RootBuilder.resumeFrame()})が実際に選んだ操作(actual)を
 * 突き合わせます(2026-07-21新設、M6b Phase B B2)。
 *
 * <p>
 * {@code ResumeSession}ごとに1つ所有させること——ソース再生中に改ページが
 * 入れ子になりうるため、{@code ResumeTrace}のような単一static bufferには
 * しない({@code RootBuilder.resumeFrame}へ通常のメソッド引数として渡す
 * ことで、Java呼び出しスタック自体が入れ子を自然に分離する)。
 * </p>
 */
public final class ResumeProgramTrace {
	private final List<ResumeOp> expected;
	private int cursor;

	public ResumeProgramTrace(final ResumeProgram program) {
		this.expected = ResumeOp.expectedOps(program);
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
