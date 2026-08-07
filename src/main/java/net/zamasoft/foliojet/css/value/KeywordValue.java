package net.zamasoft.foliojet.css.value;

/**
 * 単独のキーワードを表す値です。
 */
public enum KeywordValue implements Value {
	AUTO("auto"),

	NONE("none"),

	NORMAL("normal"),

	DEFAULT("default"),

	TRANSPARENT("transparent"),

	INHERIT("inherit"),

	INITIAL("initial"),

	UNSET("unset"),

	/** background-sizeのキーワード形式(2026-08-06、BackgroundSize参照)。 */
	CONTAIN("contain"),

	COVER("cover");

	private final String text;

	private KeywordValue(String text) {
		this.text = text;
	}

	public String toString() {
		return this.text;
	}
}
