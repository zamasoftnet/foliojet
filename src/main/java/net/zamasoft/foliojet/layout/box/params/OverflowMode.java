package net.zamasoft.foliojet.layout.box.params;

/**
 * Values of the overflow property.
 *
 * @author MIYABE Tatsuhiko
 */
public enum OverflowMode {
	VISIBLE, HIDDEN, SCROLL, AUTO;

	/**
	 * Whether to clip overflowing drawing at the box (2026-08-09). On screen, CSS scroll containers
	 * (scroll/auto) allow access to content via scrollbars, but in print, clip to the visible area as
	 * browsers do (owner decision: previously, overflow was drawn as is, causing absolutely positioned
	 * tab headings, etc. to overlap fully expanded content in asahi.com's breaking-news section).
	 * Every value other than VISIBLE also establishes a BFC that contains its floats (2026-10-09; it was limited to
	 * HIDDEN, and an {@code overflow: auto} box holding only floats was 0pt tall and clipped them).
	 */
	public boolean clipsPaint() {
		return this != VISIBLE;
	}
}
