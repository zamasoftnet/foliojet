package net.zamasoft.foliojet.css.value;

/**
 * A content-visibility value (css-contain-2, 2026-08-10).
 *
 * <p>
 * {@code hidden} retains the element's own box but <b>omits its contents from layout</b>.
 * Real sites collapse off-canvas mega menus, etc. using {@code content-visibility:hidden}+
 * {@code opacity:0} while keeping {@code position:static}. Without support, huge
 * transparent boxes occupy the sheet (observed on the yomiuri.co.jp front page).
 * {@code auto} requests deferred rendering based on the screen's visible area;
 * print renders everything (as in Chrome printing), so treat it as {@code visible}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public enum ContentVisibilityValue implements Value {
	VISIBLE_VALUE(ContentVisibilityValue.VISIBLE),

	HIDDEN_VALUE(ContentVisibilityValue.HIDDEN),

	AUTO_VALUE(ContentVisibilityValue.AUTO);

	public static final byte VISIBLE = 0;

	public static final byte HIDDEN = 1;

	public static final byte AUTO = 2;

	private final byte contentVisibility;

	private ContentVisibilityValue(byte contentVisibility) {
		this.contentVisibility = contentVisibility;
	}

	public byte getContentVisibility() {
		return this.contentVisibility;
	}

	public String toString() {
		switch (this.contentVisibility) {
		case VISIBLE:
			return "visible";

		case HIDDEN:
			return "hidden";

		case AUTO:
			return "auto";

		default:
			throw new IllegalStateException();
		}
	}
}
