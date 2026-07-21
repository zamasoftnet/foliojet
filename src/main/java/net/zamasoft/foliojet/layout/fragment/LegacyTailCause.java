package net.zamasoft.foliojet.layout.fragment;

/**
 * {@link ResumeTail.LegacyOpen}が生じた理由です(2026-07-21新設、
 * M6b Phase B B4。旧{@code ResumeProgram.LegacyTailCause}をPAGE/COLUMN
 * 共有型として抽出した)。
 */
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
