package net.zamasoft.foliojet.css.value;

/**
 * The supported combinations of {@code hanging-punctuation}.
 * {@code first} and an end-of-line mode ({@code allow-end}/{@code force-end}) can appear together
 * in either order. {@code last} is unimplemented because it separately requires identifying
 * the element's actual last line.
 */
public enum HangingPunctuationValue implements Value {
	NONE("none", false, false, false),
	FIRST("first", true, false, false),
	ALLOW_END("allow-end", false, true, false),
	FIRST_ALLOW_END("first allow-end", true, true, false),
	FORCE_END("force-end", false, false, true),
	FIRST_FORCE_END("first force-end", true, false, true);

	private final String text;
	private final boolean first;
	private final boolean allowEnd;
	private final boolean forceEnd;

	private HangingPunctuationValue(final String text, final boolean first, final boolean allowEnd,
			final boolean forceEnd) {
		this.text = text;
		this.first = first;
		this.allowEnd = allowEnd;
		this.forceEnd = forceEnd;
	}

	public boolean hangsFirst() {
		return this.first;
	}

	public boolean allowsEnd() {
		return this.allowEnd;
	}

	public boolean forcesEnd() {
		return this.forceEnd;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
