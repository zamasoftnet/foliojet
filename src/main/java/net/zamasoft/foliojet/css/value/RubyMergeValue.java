package net.zamasoft.foliojet.css.value;

/** CSS Rubyの{@code ruby-merge}値。 */
public enum RubyMergeValue implements Value {
	SEPARATE("separate"), MERGE("merge"), AUTO("auto");

	private final String text;

	private RubyMergeValue(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
