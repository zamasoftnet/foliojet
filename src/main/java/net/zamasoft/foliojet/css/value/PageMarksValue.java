package net.zamasoft.foliojet.css.value;

/**
 * {@code @page { marks }}の値です(2026-08-02)。
 *
 * <p>
 * {@link #UNSPECIFIED}は「CSSで指定されていない=入出力プロパティ
 * {@code output.marks}に従う」ことを表す({@code size: auto}が
 * {@code output.page-width/height}へ委ねるのと同じ)。
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
