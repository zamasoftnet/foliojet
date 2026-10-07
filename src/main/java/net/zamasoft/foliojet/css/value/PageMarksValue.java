package net.zamasoft.foliojet.css.value;

/**
 * An {@code @page { marks }} value (2026-08-02).
 *
 * <p>
 * {@link #UNSPECIFIED} means "not specified in CSS: follow the I/O property
 * {@code output.marks}" (just as {@code size: auto} delegates
 * to {@code output.page-width/height}).
 * </p>
 */
public enum PageMarksValue implements Value {
	UNSPECIFIED, NONE, CROP, CROSS, BOTH;

	public boolean isCrop() {
		return this == CROP || this == BOTH;
	}

	public boolean isCross() {
		return this == CROSS || this == BOTH;
	}
}
