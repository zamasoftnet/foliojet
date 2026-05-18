package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.Types;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PageBreakInsideValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class PageBreakInsideValue implements Value {
	public static final PageBreakInsideValue AUTO_VALUE = new PageBreakInsideValue(Types.PAGE_BREAK_AUTO);

	public static final PageBreakInsideValue AVOID_VALUE = new PageBreakInsideValue(Types.PAGE_BREAK_AVOID);
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

	public short getValueType() {
		return TYPE_PAGE_BREAK_INSIDE;
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