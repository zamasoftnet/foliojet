package net.zamasoft.foliojet.layout;

import net.zamasoft.foliojet.message.MessageCodeUtils;

/**
 * A failure when the retention limit ({@code processing.retained-text-limit}) is exceeded.
 * Retains message code 0x380F and arguments (element name, limit, reached value); {@code DirectSession}
 * converts it to {@code TranscoderException(STATE_BROKEN)} through the same path as
 * {@code ContinuationInvariantViolationException}.
 */
public class RetainedTextLimitException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final short code;
	private final String[] args;

	public RetainedTextLimitException(final short code, final String[] args) {
		super(MessageCodeUtils.toString(code, args));
		this.code = code;
		this.args = args.clone();
	}

	public short getCode() {
		return this.code;
	}

	public String[] getArgs() {
		return this.args.clone();
	}

	/** Extracts failures wrapped by parsers, formatters, or workers. */
	public static RetainedTextLimitException findIn(final Throwable failure) {
		final java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
			if (cause instanceof RetainedTextLimitException retained) return retained;
		}
		return null;
	}
}
