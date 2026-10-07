package net.zamasoft.foliojet.layout.fragment;

import java.util.Map;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Canonical token for a COLUMN continuation (added 2026-07-24, E-3 increment 5). A small domain token replacing
 * {@code ColumnResumeProgram}+{@code RootBuilder.CompiledColumn} (design consultation
 * -codex.md §1).
 *
 * <p>
 * Not merged into the same record as PAGE continuations ({@link Continuation}) because of an essential asymmetry:
 * the owner box itself is not reconstructed as a fragment; a new column is merely committed to the same instance.
 * {@link #anchor()} represents the remainder directly under the owner, and {@link #childFrame()} represents the
 * descendant chain traversed by the cut inside the owner (null if not traversed: valid when there are no open
 * descendants directly under the owner, or the cut did not pass through them).
 * </p>
 *
 * <p>
 * Deliberately does not apply {@code Map.copyOf} to {@code ranges}. It is a mutable map for consume-once, directly
 * modified by {@code RootBuilder.replayFromSource()} via {@code remove()} during consumption (making it read-only
 * causes {@code UnsupportedOperationException} in production; retains the old {@code CompiledColumn} contract).
 * </p>
 *
 * @param snapshot   relative open-path snapshot at the break
 *                   (index 0 = COLUMN_OWNER anchor)
 * @param anchor     remainder directly under the owner
 * @param childFrame continuation frame if the cut passed through directly under the owner (null otherwise)
 * @param ranges     replay ranges for closed subtrees (consume-once, mutable)
 * @param pathShape  validated open-path shape returned by {@link ContinuationValidator#validateColumn}
 *                   (canonical source for tail-policy derivation and terminal OpenShape)
 */
public record ColumnContinuation(OpenPathSnapshot snapshot, ColumnAnchor anchor,
		Continuation.ContinuationFrame childFrame, Map<IBox, Continuation.SourceRange> ranges,
		ContinuationValidator.PathShape pathShape) {
}
