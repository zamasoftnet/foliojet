package net.zamasoft.foliojet.ua;

public class AbortException extends RuntimeException {
	private static final long serialVersionUID = 0L;
	private final byte state;
	/** Constant for aborting after processing reaches a suitable stopping point. */
	public static final byte ABORT_NORMAL = 1;

	/** Constant for forced abort. */
	public static final byte ABORT_FORCE = 2;

	public AbortException(byte state) {
		this.state = state;
	}

	public AbortException() {
		this(ABORT_NORMAL);
	}

	public byte getState() {
		return this.state;
	}
}