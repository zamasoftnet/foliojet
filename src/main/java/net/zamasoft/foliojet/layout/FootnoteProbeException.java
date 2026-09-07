package net.zamasoft.foliojet.layout;

/** Bまたは報告listenerの失敗。部分出力を正常終了させる設定の対象にはしない。 */
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
