package net.zamasoft.foliojet.layout.fragment;

/**
 * The common contract for sessions owning leases on absorbed replay ranges (per occurrence)
 * (introduced 2026-07-21, M6b Phase B4-Step4).
 * Both PAGE's {@code
 * RootBuilder.ResumeSession} and COLUMN's {@code RootBuilder
 * .ColumnResumeSession} implement it and share the same stack ({@code RootBuilder.sessions}).
 * Thus {@code RootBuilder.replaySubtree()} need only inspect the current top session,
 * even for a nested PAGE break during COLUMN resume or a nested COLUMN break during COLUMN resume
 * (ChatGPT Pro consultation; see the design consultation).
 */
public interface ReplayLeaseSession {
	/** Marks consumption of an absorbed range complete (from replaySubtree's finally). */
	void releaseLease(Continuation.SourceRange occurrence);

	/** Whether any unconsumed leases remain (for the sanity check at session end). */
	boolean hasUnconsumedLeases();
}
