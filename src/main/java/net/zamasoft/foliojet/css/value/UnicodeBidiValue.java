package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum UnicodeBidiValue implements Value {
	NORMAL_VALUE(UnicodeBidiValue.NORMAL),

	EMBED_VALUE(UnicodeBidiValue.EMBED),

	BIDI_OVERRIDE_VALUE(UnicodeBidiValue.BIDI_OVERRIDE),

	ISOLATE_VALUE(UnicodeBidiValue.ISOLATE),

	ISOLATE_OVERRIDE_VALUE(UnicodeBidiValue.ISOLATE_OVERRIDE),

	PLAINTEXT_VALUE(UnicodeBidiValue.PLAINTEXT);
	public static final byte NORMAL = 1;

	public static final byte EMBED = 2;

	public static final byte BIDI_OVERRIDE = 3;

	/** css-writing-modes-3 §2.2 の isolate 系(2026-09-04。それまでは embed/bidi-override/normal へ潰していた)。 */
	public static final byte ISOLATE = 4;

	public static final byte ISOLATE_OVERRIDE = 5;

	public static final byte PLAINTEXT = 6;

	private final byte unicodeBidi;

	private UnicodeBidiValue(byte unicodeBidi) {
		this.unicodeBidi = unicodeBidi;
	}

	public byte getUnicodeBidi() {
		return this.unicodeBidi;
	}

	public String toString() {
		switch (this.unicodeBidi) {
		case NORMAL:
			return "normal";

		case EMBED:
			return "embed";

		case BIDI_OVERRIDE:
			return "bidi-override";

		case ISOLATE:
			return "isolate";

		case ISOLATE_OVERRIDE:
			return "isolate-override";

		case PLAINTEXT:
			return "plaintext";

		default:
			throw new IllegalStateException();
		}
	}
}