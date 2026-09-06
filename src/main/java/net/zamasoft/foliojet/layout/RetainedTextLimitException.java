package net.zamasoft.foliojet.layout;

import net.zamasoft.foliojet.message.MessageCodeUtils;

/**
 * 溜め込みの上限({@code processing.retained-text-limit})を超えたときの失敗です。
 * メッセージコード 0x380F と引数(要素名・上限・到達値)を保持し、{@code DirectSession} が
 * {@code ContinuationInvariantViolationException} と同じ経路で {@code TranscoderException(STATE_BROKEN)} にする。
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

	/** パーサー・formatter・ワーカーが包んだ失敗を取り出します。 */
	public static RetainedTextLimitException findIn(final Throwable failure) {
		final java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
			if (cause instanceof RetainedTextLimitException retained) return retained;
		}
		return null;
	}
}
