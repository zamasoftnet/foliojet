package net.zamasoft.foliojet.ua;

public class AbortException extends RuntimeException {
	private static final long serialVersionUID = 0L;
	private final byte state;
	/** きりのよいところまで処理する中断処理の定数です。 */
	public static final byte ABORT_NORMAL = 1;

	/** 強制的に中断する処理の定数です。 */
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