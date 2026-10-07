package net.zamasoft.foliojet.css.value;

/** The CSS Ruby {@code ruby-merge} value. */
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
