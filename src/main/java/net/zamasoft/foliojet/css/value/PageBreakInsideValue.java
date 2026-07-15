package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.Types;

/**
 * @author MIYABE Tatsuhiko
 */
public enum PageBreakInsideValue implements Value {
	AUTO_VALUE(Types.PAGE_BREAK_AUTO),

	AVOID_VALUE(Types.PAGE_BREAK_AVOID);

	//
	// public static final PageBreakInsideValue AVOID_PAGE_VALUE = new
	// PageBreakInsideValue(
	// Types.PAGE_BREAK_AVOID_PAGE);
	//
	// public static final PageBreakInsideValue AVOID_COLUMN_VALUE = new
	// PageBreakInsideValue(
	// Types.PAGE_BREAK_AVOID_COLUMN);

	private final byte pageBreakInside;

	private PageBreakInsideValue(byte pageBreakInside) {
		this.pageBreakInside = pageBreakInside;
	}

	public byte getPageBreakInside() {
		return this.pageBreakInside;
	}

	public String toString() {
		switch (this.pageBreakInside) {
		case Types.PAGE_BREAK_AUTO:
			return "auto";

		case Types.PAGE_BREAK_AVOID:
			return "avoid";

		default:
			throw new IllegalStateException();
		}
	}
}