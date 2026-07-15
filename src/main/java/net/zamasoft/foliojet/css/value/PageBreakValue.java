package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class PageBreakValue implements Value {
	public static final byte PAGE_BREAK_AUTO = 0;
	public static final byte PAGE_BREAK_AVOID = 1;
	public static final byte PAGE_BREAK_ALWAYS = 2;
	public static final byte PAGE_BREAK_LEFT = 3;
	public static final byte PAGE_BREAK_RIGHT = 4;
	public static final byte PAGE_BREAK_PAGE = 5;
	public static final byte PAGE_BREAK_COLUMN = 6;
	// public static final byte PAGE_BREAK_AVOID_PAGE = 7;
	// public static final byte PAGE_BREAK_AVOID_COLUMN = 8;
	public static final byte PAGE_BREAK_VERSO = 9;
	public static final byte PAGE_BREAK_RECTO = 10;
	public static final byte PAGE_BREAK_IF_LEFT = 11;
	public static final byte PAGE_BREAK_IF_RIGHT = 12;
	public static final byte PAGE_BREAK_IF_VERSO = 13;
	public static final byte PAGE_BREAK_IF_RECTO = 14;

	public static final PageBreakValue AUTO_VALUE = new PageBreakValue(PAGE_BREAK_AUTO);

	public static final PageBreakValue ALWAYS_VALUE = new PageBreakValue(PAGE_BREAK_ALWAYS);

	public static final PageBreakValue AVOID_VALUE = new PageBreakValue(PAGE_BREAK_AVOID);

	public static final PageBreakValue LEFT_VALUE = new PageBreakValue(PAGE_BREAK_LEFT);

	public static final PageBreakValue RIGHT_VALUE = new PageBreakValue(PAGE_BREAK_RIGHT);

	public static final PageBreakValue PAGE_VALUE = new PageBreakValue(PAGE_BREAK_PAGE);

	public static final PageBreakValue COLUMN_VALUE = new PageBreakValue(PAGE_BREAK_COLUMN);

	// public static final PageBreakValue AVOID_PAGE_VALUE = new PageBreakValue(
	// PAGE_BREAK_AVOID_PAGE);
	//
	// public static final PageBreakValue AVOID_COLUMN_VALUE = new
	// PageBreakValue(
	// PAGE_BREAK_AVOID_COLUMN);

	public static final PageBreakValue VERSO_VALUE = new PageBreakValue(PAGE_BREAK_VERSO);

	public static final PageBreakValue RECTO_VALUE = new PageBreakValue(PAGE_BREAK_RECTO);
	public static final PageBreakValue IF_LEFT_VALUE = new PageBreakValue(PAGE_BREAK_IF_LEFT);

	public static final PageBreakValue IF_RIGHT_VALUE = new PageBreakValue(PAGE_BREAK_IF_RIGHT);
	public static final PageBreakValue IF_VERSO_VALUE = new PageBreakValue(PAGE_BREAK_IF_VERSO);

	public static final PageBreakValue IF_RECTO_VALUE = new PageBreakValue(PAGE_BREAK_IF_RECTO);

	private final byte pageBreak;

	private PageBreakValue(byte pageBreak) {
		this.pageBreak = pageBreak;
	}

	public byte getPageBreak() {
		return this.pageBreak;
	}

	public String toString() {
		switch (this.pageBreak) {
		case PAGE_BREAK_AUTO:
			return "auto";

		case PAGE_BREAK_ALWAYS:
			return "always";

		case PAGE_BREAK_AVOID:
			return "avoid";

		case PAGE_BREAK_LEFT:
			return "left";

		case PAGE_BREAK_RIGHT:
			return "right";

		case PAGE_BREAK_PAGE:
			return "page";

		case PAGE_BREAK_COLUMN:
			return "column";

		// case PAGE_BREAK_AVOID_PAGE:
		// return "avoid-page";
		//
		// case PAGE_BREAK_AVOID_COLUMN:
		// return "avoid-column";

		case PAGE_BREAK_VERSO:
			return "verso";

		case PAGE_BREAK_RECTO:
			return "recto";

		case PAGE_BREAK_IF_LEFT:
			return "if-left";

		case PAGE_BREAK_IF_RIGHT:
			return "if-right";

		case PAGE_BREAK_IF_VERSO:
			return "if-verso";

		case PAGE_BREAK_IF_RECTO:
			return "if-recto";

		default:
			throw new IllegalStateException();
		}
	}
}