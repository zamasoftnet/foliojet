package net.zamasoft.foliojet.css.value;

/**
 * {@code @page { size }}の値です(名前付きページN3/N4、2026-07-31——
 * consult-codex-2026-07-31-named-pages.txt Q3)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class PageSizeValue implements Value {

	public static final byte ORIENTATION_NONE = 0;

	public static final byte ORIENTATION_LANDSCAPE = 1;

	public static final byte ORIENTATION_PORTRAIT = 2;

	/** {@code size: auto}(出力既定寸法)。 */
	public static final PageSizeValue AUTO = new PageSizeValue(-1, -1, ORIENTATION_NONE);

	/** 幅・高さ(pt。0以下=既定寸法を使う)。 */
	public final double width, height;

	public final byte orientation;

	public PageSizeValue(final double width, final double height, final byte orientation) {
		this.width = width;
		this.height = height;
		this.orientation = orientation;
	}

	/**
	 * 実寸を解決します(orientation適用込み——landscapeは長辺を幅に、
	 * portraitは短辺を幅にする)。
	 *
	 * @param defaultWidth  size:autoのUA既定幅
	 * @param defaultHeight 同高さ
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
