package net.zamasoft.foliojet.css.value;

/** A value representing a single keyword. */
public enum KeywordValue implements Value {
	AUTO("auto"),

	NONE("none"),

	NORMAL("normal"),

	DEFAULT("default"),

	TRANSPARENT("transparent"),

	INHERIT("inherit"),

	INITIAL("initial"),

	UNSET("unset"),

	/** The keyword form of background-size (2026-08-06, see BackgroundSize). */
	CONTAIN("contain"),

	COVER("cover"),

	/**
	 * Intrinsic size keywords (css-sizing-3 §2.2, 2026-08-29). Accepted by width/height,
	 * the min-/max- variants, and their logical counterparts.
	 * The form with an argument, {@code fit-content(<length-percentage>)}, is {@link FitContentValue}.
	 */
	MAX_CONTENT("max-content"),

	MIN_CONTENT("min-content"),

	FIT_CONTENT("fit-content"),

	/** The initial value of image-orientation (css-images-3, 2026-08-30). */
	FROM_IMAGE("from-image"),

	/**
	 * Internal marker for the gradient approximation of mask-image (2026-08-09, see MaskImage).
	 * This is not a CSS keyword.
	 */
	CLIP("clip");

	private final String text;

	private KeywordValue(String text) {
		this.text = text;
	}

	public String toString() {
		return this.text;
	}
}
