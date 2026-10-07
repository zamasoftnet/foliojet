package net.zamasoft.foliojet.layout;

/** A failure in B or a report listener. Excluded from settings that allow partial output to finish successfully. */
public final class FootnoteProbeException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	FootnoteProbeException(final RuntimeException cause) {
		super("Footnote page probe failed: " + cause.getMessage(), cause);
	}

	public static FootnoteProbeException findIn(final Throwable failure) {
		final java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
			if (cause instanceof FootnoteProbeException probe) return probe;
		}
		return null;
	}
}
