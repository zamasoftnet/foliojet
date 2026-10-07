package net.zamasoft.foliojet.css.value;

/**
 * An {@code @page { size }} value (named pages N3/N4, 2026-07-31:
 * consult-codex-2026-07-31-named-pages.txt Q3).
 *
 * @author MIYABE Tatsuhiko
 */
public final class PageSizeValue implements Value {

	public static final byte ORIENTATION_NONE = 0;

	public static final byte ORIENTATION_LANDSCAPE = 1;

	public static final byte ORIENTATION_PORTRAIT = 2;

	/** {@code size: auto} (the default output size). */
	public static final PageSizeValue AUTO = new PageSizeValue(-1, -1, ORIENTATION_NONE);

	/** Width and height (pt; zero or less uses the default size). */
	public final double width, height;

	public final byte orientation;

	public PageSizeValue(final double width, final double height, final byte orientation) {
		this.width = width;
		this.height = height;
		this.orientation = orientation;
	}

	/**
	 * Resolves the actual size, including orientation: landscape uses the longer side
	 * as the width, and portrait uses the shorter side.
	 *
	 * @param defaultWidth  the UA's default width for size:auto
	 * @param defaultHeight the corresponding default height
	 * @return {width, height}
	 */
	public double[] resolve(final double defaultWidth, final double defaultHeight) {
		double w = this.width > 0 ? this.width : defaultWidth;
		double h = this.height > 0 ? this.height : defaultHeight;
		if (this.orientation == ORIENTATION_LANDSCAPE && w < h || this.orientation == ORIENTATION_PORTRAIT && w > h) {
			final double t = w;
			w = h;
			h = t;
		}
		return new double[] { w, h };
	}

	@Override
	public String toString() {
		if (this == AUTO) {
			return "auto";
		}
		final String o = this.orientation == ORIENTATION_LANDSCAPE ? " landscape"
				: this.orientation == ORIENTATION_PORTRAIT ? " portrait" : "";
		if (this.width > 0) {
			return this.width + "pt " + this.height + "pt" + o;
		}
		return o.isEmpty() ? "auto" : o.trim();
	}
}
