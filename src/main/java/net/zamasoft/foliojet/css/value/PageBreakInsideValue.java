package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;


/**
 * @author MIYABE Tatsuhiko
 */
public enum PageBreakInsideValue implements Value {
	AUTO_VALUE(PageBreakMode.AUTO),

	AVOID_VALUE(PageBreakMode.AVOID);

	//
	// public static final PageBreakInsideValue AVOID_PAGE_VALUE = new
	// PageBreakInsideValue(
	// Types.PAGE_BREAK_AVOID_PAGE);
	//
	// public static final PageBreakInsideValue AVOID_COLUMN_VALUE = new
	// PageBreakInsideValue(
	// Types.PAGE_BREAK_AVOID_COLUMN);

	private final PageBreakMode pageBreakInside;

	private PageBreakInsideValue(PageBreakMode pageBreakInside) {
		this.pageBreakInside = pageBreakInside;
	}

	public PageBreakMode getPageBreakInside() {
		return this.pageBreakInside;
	}

	public String toString() {
		switch (this.pageBreakInside) {
		case PageBreakMode.AUTO:
			return "auto";

		case PageBreakMode.AVOID:
			return "avoid";

		default:
			throw new IllegalStateException();
		}
	}
}