package net.zamasoft.foliojet.css.value;

/** CSS Rubyの{@code ruby-overhang}値。 */
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
