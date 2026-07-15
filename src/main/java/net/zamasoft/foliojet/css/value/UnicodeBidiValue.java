package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum UnicodeBidiValue implements Value {
	NORMAL_VALUE(UnicodeBidiValue.NORMAL),

	EMBED_VALUE(UnicodeBidiValue.EMBED),

	BIDI_OVERRIDE_VALUE(UnicodeBidiValue.BIDI_OVERRIDE);
	public static final byte NORMAL = 1;

	public static final byte EMBED = 2;

	public static final byte BIDI_OVERRIDE = 3;

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

		default:
			throw new IllegalStateException();
		}
	}
}