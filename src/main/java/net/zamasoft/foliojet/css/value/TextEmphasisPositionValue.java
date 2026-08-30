package net.zamasoft.foliojet.css.value;

/** {@code text-emphasis-position}の正規化済みの値です。 */
public enum TextEmphasisPositionValue implements Value {
	OVER_RIGHT("over right", false, false),
	OVER_LEFT("over left", false, true),
	UNDER_RIGHT("under right", true, false),
	UNDER_LEFT("under left", true, true);

	private final String cssText;
	private final boolean under;
	private final boolean left;

	private TextEmphasisPositionValue(final String cssText, final boolean under, final boolean left) {
		this.cssText = cssText;
		this.under = under;
		this.left = left;
	}

	public boolean isUnder() {
		return this.under;
	}

	public boolean isLeft() {
		return this.left;
	}

	@Override
	public String toString() {
		return this.cssText;
	}
}
