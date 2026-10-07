package net.zamasoft.foliojet.css.value;

/**
 * A {@code text-autospace} value (Japanese text spacing A1, 2026-07-31:
 * consult-codex-2026-07-31-text-spacing.txt Q1). The initial subset is:
 * {@code normal | no-autospace | ideograph-alpha || ideograph-numeric}.
 * {@code auto}, {@code punctuation}, and {@code insert}/{@code replace} fall outside
 * the subset (invalidate the declaration). Internally uses bit flags to allow future additions.
 *
 * @author MIYABE Tatsuhiko
 */
public enum TextAutospaceValue implements Value {
	NORMAL("normal", (byte) (TextAutospaceValue.ALPHA | TextAutospaceValue.NUMERIC)),

	NO_AUTOSPACE("no-autospace", (byte) 0),

	IDEOGRAPH_ALPHA("ideograph-alpha", TextAutospaceValue.ALPHA),

	IDEOGRAPH_NUMERIC("ideograph-numeric", TextAutospaceValue.NUMERIC),

	IDEOGRAPH_ALPHA_NUMERIC("ideograph-alpha ideograph-numeric",
			(byte) (TextAutospaceValue.ALPHA | TextAutospaceValue.NUMERIC));

	/** Between Japanese characters and Latin letters. */
	public static final byte ALPHA = 1;

	/** Between Japanese characters and Latin digits. */
	public static final byte NUMERIC = 2;

	private final String text;

	private final byte flags;

	private TextAutospaceValue(final String text, final byte flags) {
		this.text = text;
		this.flags = flags;
	}

	/** The effective flags ({@code ALPHA}|{@code NUMERIC}). */
	public byte getFlags() {
		return this.flags;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
