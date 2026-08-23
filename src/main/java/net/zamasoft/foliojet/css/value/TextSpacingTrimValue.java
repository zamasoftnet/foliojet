package net.zamasoft.foliojet.css.value;

/**
 * {@code text-spacing-trim}の実装済み値です。
 *
 * <p>CSS Text 4の意味に合わせ、{@code normal}は行中の隣接約物を詰めるが
 * 行頭の始め括弧は全角のまま、{@code trim-start}は行頭も天付き、
 * {@code space-all}は約物を全角のままにする。</p>
 */
public enum TextSpacingTrimValue implements Value {
	NORMAL("normal", false, false, false, false),

	SPACE_ALL("space-all", true, false, false, false),

	SPACE_FIRST("space-first", false, false, false, true),

	TRIM_START("trim-start", false, true, false, false),

	TRIM_BOTH("trim-both", false, true, true, false),

	/** UAの高品質既定としてtrim-bothを採用する。 */
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
