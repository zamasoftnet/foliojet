package net.zamasoft.foliojet.layout.fragment;

/**
 * Indicates an invariant violation in the continuation mechanism (resuming page/column breaks) (added 2026-07-21).
 * Represents all structural invariant violations requiring validation after resume completes, such as {@code
 * depth}/{@code OpenShape} consistency and restored {@code flowStack} state. These indicate an implementation bug
 * or an unsupported continuation path, such as a table with orthogonal writing-mode bypassing the {@code
 * breakDepth} barrier during a OnePass page break.
 *
 * <p>
 * B2 generalized this check; {@link ContinuationValidator} now handles the validation layer. Initially, to address
 * an existing unguarded crash path (orthogonal writing-mode tables, found in the ChatGPT Pro consultation and
 * confirmed by observation), the existing assert in {@code RootBuilder.pageBreak()} was first replaced with an
 * unconditional check throwing this exception.
 * </p>
 */
public class ContinuationInvariantViolationException extends RuntimeException {
	private static final long serialVersionUID = 0L;

	public ContinuationInvariantViolationException(final String message) {
		super(message);
	}
	/** Extracts invariant violations wrapped by a parser or formatter. */
	public static ContinuationInvariantViolationException findIn(final Throwable failure) {
		final java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
			if (cause instanceof ContinuationInvariantViolationException invariant) return invariant;
		}
		return null;
	}
}
