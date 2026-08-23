package net.zamasoft.foliojet.css.value;

/** CSS Rubyの{@code ruby-align}値。 */
public enum RubyAlignValue implements Value {
	START("start"),
	CENTER("center"),
	SPACE_BETWEEN("space-between"),
	SPACE_AROUND("space-around");

	private final String text;

	private RubyAlignValue(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
