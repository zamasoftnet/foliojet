package net.zamasoft.foliojet.css.value;

/**
 * {@code hanging-punctuation}の実装済み組合せです。
 * {@code first}と行末方式({@code allow-end}/{@code force-end})は順不同で
 * 併用できる。{@code last}は別途、要素の真の最終行判定が必要なため未実装。
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
