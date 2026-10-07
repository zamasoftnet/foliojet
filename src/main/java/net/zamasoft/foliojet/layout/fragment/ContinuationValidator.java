package net.zamasoft.foliojet.layout.fragment;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Directly validates the authoritative continuation inputs (PAGE/COLUMN inputs to
 * {@link Continuation}). Added 2026-07-24, E-3 increment 1; design consultation §3.
 *
 * <p>
 * Ports the invariants previously enforced by the program subsystem
 * ({@code ResumeProgramCompiler}/{@code ColumnResumeProgramCompiler} and
 * {@code ContinuationVerifier}/{@code ColumnContinuationVerifier}, removed in E-3 increment 6):
 * snapshot/continuation depth equations, bounded frame walks (rejecting cycles, nulls, and
 * snapshot overflow), valid prefix serial order and ranges, finite crossExtent, and correspondence
 * with snapshot levels. This is the sole validation layer after program removal.
 * </p>
 *
 * <p>
 * Contract:
 * </p>
 * <ul>
 * <li>Never calls {@link FragmentRecipe#instantiate} during validation
 * (no fragment creation or builder state mutation).</li>
 * <li>Frame traversal uses bounded iteration capped by snapshot depth, not recursion
 * (merge gate; depths around 5000 can be processed without JVM recursion).</li>
 * <li>All failures are {@link ContinuationInvariantViolationException}
 * (the same typed exception as the existing compilers/verifiers).</li>
 * </ul>
 */
public final class ContinuationValidator {
	private ContinuationValidator() {
	}

	/**
	 * Summary of the validated open path shape (a byproduct of the validation walk).
	 * Used in execution as the authoritative terminal open shape of COLUMN continuation
	 * ({@code ColumnContinuation.pathShape()}). It also once directly derived the tail policy
	 * ({@code WorklistTailGate}), but the gate was retired in the legacy recursion removal,
	 * increment 4d, on 2026-07-30.
	 *
	 * @param firstOpenPathIndex first uncollected open path index (immediately after the frames
	 *                           traversable as a first-class chain; equals {@code snapshot.depth()}
	 *                           if all levels have been collected)
	 * @param terminalShape      open shape of the terminal frame
	 */
	public record PathShape(int firstOpenPathIndex, OpenShape terminalShape) {
	}

	/**
	 * Directly validates PAGE continuation (ports the invariants of the old
	 * {@code ResumeProgramCompiler.compile} + {@code ContinuationVerifier.verify}).
	 *
	 * @throws ContinuationInvariantViolationException if the structure is invalid
	 */
	public static PathShape validatePage(final OpenPathSnapshot snapshot, final Continuation continuation) {
		if (snapshot == null || continuation == null || continuation.root() == null) {
			throw new ContinuationInvariantViolationException("PAGE continuation has null snapshot/root");
		}
		if (snapshot.depth() <= 0) {
			throw new ContinuationInvariantViolationException("snapshot depth must be positive");
		}
		if (continuation.depth() != snapshot.depth()) {
			throw new ContinuationInvariantViolationException(
					"snapshot depth=" + snapshot.depth() + ", continuation depth=" + continuation.depth());
		}

		final Set<Continuation.ContinuationFrame> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Continuation.ContinuationFrame frame = continuation.root();
		int index = 0;
		while (true) {
			checkFrame(snapshot, seen, frame, index);

			switch (frame.tail()) {
			case Continuation.OpenTail.Child(final Continuation.ContinuationFrame child) -> {
				frame = child;
				++index;
			}
			case Continuation.OpenTail.OpenTailShape(final OpenShape shape) -> {
				final int openDepth = shape.depth();
				final int frameCount = index + 1;
				if (frameCount + openDepth - 1 != continuation.depth()) {
					throw new ContinuationInvariantViolationException("depth invariant failed: frames=" + frameCount
							+ ", tailDepth=" + openDepth + ", continuationDepth=" + continuation.depth());
				}
				if (frameCount == snapshot.depth() && openDepth != 1) {
					throw new ContinuationInvariantViolationException(
							"fully collected path must end in OpenText, but openDepth=" + openDepth);
				}
				return new PathShape(frameCount, shape);
			}
			}
		}
	}

	/**
	 * Directly validates COLUMN continuation inputs (owner anchor + descendant chain inside the owner).
	 * Ports the invariants of the old {@code ColumnResumeProgramCompiler.compileColumn} +
	 * {@code ColumnContinuationVerifier.verify}.
	 *
	 * <p>
	 * Retains the existing compiler/verifier contract: the depth equation differs from PAGE
	 * (owner = index 0 is not a fragment level, so {@code chainFrames + tailDepth == snapshotDepth}),
	 * and {@code childFrame == null} is valid (no open descendants directly under the owner,
	 * or the break did not pass through them).
	 * </p>
	 *
	 * @param anchor     remainder directly under the owner
	 * @param snapshot   relative open path snapshot at the break (index 0 = owner)
	 * @param childFrame continuation frame when the break passes through directly below the owner (otherwise null)
	 * @throws ContinuationInvariantViolationException if the structure is invalid
	 */
	public static PathShape validateColumn(final ColumnAnchor anchor, final OpenPathSnapshot snapshot,
			final Continuation.ContinuationFrame childFrame) {
		if (anchor == null || snapshot == null) {
			throw new ContinuationInvariantViolationException("COLUMN continuation has null snapshot/anchor");
		}
		if (snapshot.depth() <= 0) {
			throw new ContinuationInvariantViolationException("snapshot depth must be positive");
		}

		if (childFrame == null) {
			// Valid case where the break did not pass through descendants. The owner's own open state
			// is accounted for separately as one OpenText unit (same contract as ColumnResumeProgramCompiler):
			// OpenText if depth==1; otherwise, the legacy open state of depth snapshot.depth()
			// starts at index 1.
			return new PathShape(1, OpenShape.of(snapshot.depth()));
		}

		final Set<Continuation.ContinuationFrame> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Continuation.ContinuationFrame frame = childFrame;
		int index = 1;
		while (true) {
			checkFrame(snapshot, seen, frame, index);

			switch (frame.tail()) {
			case Continuation.OpenTail.Child(final Continuation.ContinuationFrame child) -> {
				frame = child;
				++index;
			}
			case Continuation.OpenTail.OpenTailShape(final OpenShape shape) -> {
				final int openDepth = shape.depth();
				final int chainFrames = index; // Indices 1..index have been traversed
				if (chainFrames + openDepth != snapshot.depth()) {
					throw new ContinuationInvariantViolationException("COLUMN depth invariant failed: levels="
							+ chainFrames + ", tailDepth=" + openDepth + ", snapshotDepth=" + snapshot.depth());
				}
				final int firstOpenPathIndex = 1 + chainFrames;
				if (firstOpenPathIndex == snapshot.depth() && openDepth != 1) {
					throw new ContinuationInvariantViolationException(
							"fully collected path must end in OpenText, but openDepth=" + openDepth);
				}
				return new PathShape(firstOpenPathIndex, shape);
			}
			}
		}
	}

	/**
	 * Directly checks the signature (class/writing-mode/column-count) of the actual fragment
	 * immediately after instantiate against the corresponding level of the break snapshot.
	 * E-3 increment 2: directly implements the sole independent value of the
	 * {@code ResumeOp.Instantiate} check in the shadow ({@code ResumeProgramTrace}).
	 * Call before builder state mutation (startFlowBlock/restyle).
	 *
	 * @throws ContinuationInvariantViolationException if the signatures do not match
	 */
	public static void checkFragmentSignature(final OpenPathSnapshot snapshot, final int openPathIndex,
			final OpenPathSnapshot.FragmentSignature actual) {
		if (openPathIndex < 0 || openPathIndex >= snapshot.depth()) {
			throw new ContinuationInvariantViolationException(
					"fragment openPathIndex=" + openPathIndex + " out of snapshot depth=" + snapshot.depth());
		}
		final OpenPathSnapshot.FragmentSignature expected = snapshot.levels().get(openPathIndex).fragmentSignature();
		if (!expected.equals(actual)) {
			throw new ContinuationInvariantViolationException("fragment signature mismatch at openPathIndex="
					+ openPathIndex + ": expected=" + expected + ", actual=" + actual);
		}
	}

	/**
	 * Shared validation for one frame walk step (null, cycles, snapshot depth overflow,
	 * snapshot level correspondence, crossExtent, and prefix order/ranges).
	 */
	private static void checkFrame(final OpenPathSnapshot snapshot, final Set<Continuation.ContinuationFrame> seen,
			final Continuation.ContinuationFrame frame, final int index) {
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
		if (descriptor.index() != index) {
			throw new ContinuationInvariantViolationException(
					"level " + index + " descriptor index=" + descriptor.index());
		}
		if (!Double.isFinite(frame.crossExtent()) || frame.crossExtent() < 0) {
			throw new ContinuationInvariantViolationException(
					"level " + index + " has invalid crossExtent=" + frame.crossExtent());
		}

		int lastSerial = -1;
		final List<Continuation.SourceRange> prefix = frame.prefixItems();
		for (final Continuation.SourceRange range : prefix) {
			if (range.serial() <= lastSerial) {
				throw new ContinuationInvariantViolationException(
						"level " + index + " prefix serial not strictly increasing: " + range.serial());
			}
			lastSerial = range.serial();
			if (range.fromId() < 0 || range.toId() < range.fromId()) {
				throw new ContinuationInvariantViolationException(
						"level " + index + " has invalid source range: " + range);
			}
		}
	}
}
