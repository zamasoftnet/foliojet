package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: UnicodeBidiValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class UnicodeBidiValue implements Value {
	public static final byte NORMAL = 1;

	public static final byte EMBED = 2;

	public static final byte BIDI_OVERRIDE = 3;

	public static final UnicodeBidiValue NORMAL_VALUE = new UnicodeBidiValue(NORMAL);

	public static final UnicodeBidiValue EMBED_VALUE = new UnicodeBidiValue(EMBED);

	public static final UnicodeBidiValue BIDI_OVERRIDE_VALUE = new UnicodeBidiValue(BIDI_OVERRIDE);

	private final byte unicodeBidi;

	private UnicodeBidiValue(byte unicodeBidi) {
		this.unicodeBidi = unicodeBidi;
	}

	public short getValueType() {
		return TYPE_UNICODE_BIDI;
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