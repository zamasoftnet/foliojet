package net.zamasoft.foliojet.layout.fragment;

/**
 * 吸収済み再生範囲(occurrence単位)のリースを所有するセッションの共通
 * 契約です(2026-07-21新設、M6b Phase B4-Step4)。PAGEの{@code
 * RootBuilder.ResumeSession}とCOLUMNの{@code RootBuilder
 * .ColumnResumeSession}の両方がこれを実装し、同じスタック
 * ({@code RootBuilder.sessions})で管理されることで、COLUMN resume中の
 * nested PAGE break/COLUMN resume中の入れ子COLUMN改段でも、
 * {@code RootBuilder.replaySubtree()}が常に「現在のtopセッション」だけを
 * 見ればよいようにする(ChatGPT Pro相談、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
 * 参照)。
 */
public interface ReplayLeaseSession {
	/** 吸収済み範囲の消費完了です(replaySubtreeのfinallyから)。 */
	void releaseLease(Continuation.SourceRange occurrence);

	/** 未消費のリースが残っているか(セッション終了時の健全性チェック用)。 */
	boolean hasUnconsumedLeases();
}
