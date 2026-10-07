package net.zamasoft.foliojet.css.value;

/**
 * The supported {@code text-spacing-trim} values.
 *
 * <p>Following CSS Text 4 semantics, {@code normal} trims adjacent punctuation within a line
 * but leaves opening brackets at the line start full-width. {@code trim-start} also trims
 * the line start flush, while {@code space-all} leaves punctuation full-width.</p>
 */
public enum TextSpacingTrimValue implements Value {
	NORMAL("normal", false, false, false, false),

	SPACE_ALL("space-all", true, false, false, false),

	SPACE_FIRST("space-first", false, false, false, true),

	TRIM_START("trim-start", false, true, false, false),

	TRIM_BOTH("trim-both", false, true, true, false),

	/** Uses trim-both as the UA's high-quality default. */
	AUTO("auto", false, true, true, false);

	private final String text;

	private final boolean spaceAll;

	private final boolean trimStart;

	private final boolean trimEnd;

	private final boolean spaceFirst;

	private TextSpacingTrimValue(final String text, final boolean spaceAll, final boolean trimStart,
			final boolean trimEnd, final boolean spaceFirst) {
		this.text = text;
		this.spaceAll = spaceAll;
		this.trimStart = trimStart;
		this.trimEnd = trimEnd;
		this.spaceFirst = spaceFirst;
	}

	public boolean isSpaceAll() {
		return this.spaceAll;
	}

	public boolean trimsLineStart() {
		return this.trimStart;
	}

	public boolean trimsLineEnd() {
		return this.trimEnd;
	}

	public boolean spacesFirstLine() {
		return this.spaceFirst;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
