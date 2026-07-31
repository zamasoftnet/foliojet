package net.zamasoft.foliojet.css.value;

/**
 * {@code text-autospace}の値です(和文詰めA1、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt Q1)。初期サブセット:
 * {@code normal | no-autospace | ideograph-alpha || ideograph-numeric}。
 * {@code auto}・{@code punctuation}・{@code insert}/{@code replace}は
 * サブセット外(宣言無効)。内部表現はbit flag(将来の値追加に備える)。
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

	/** 和字と欧文アルファベットの間。 */
	public static final byte ALPHA = 1;

	/** 和字と欧文数字の間。 */
	public static final byte NUMERIC = 2;

	private final String text;

	private final byte flags;

	private TextAutospaceValue(final String text, final byte flags) {
		this.text = text;
		this.flags = flags;
	}

	/** 実効フラグ({@code ALPHA}|{@code NUMERIC})です。 */
	public byte getFlags() {
		return this.flags;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
