package net.zamasoft.foliojet.css.value;

/** The CSS Ruby {@code ruby-overhang} value. */
public enum RubyOverhangValue implements Value {
	AUTO("auto"), NONE("none");

	private final String text;

	private RubyOverhangValue(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
