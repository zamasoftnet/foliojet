package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Border.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public class Border implements Comparable<Border> {
	public static final short NONE = 0;

	public static final short HIDDEN = 1;

	public static final short DOUBLE = 2;

	public static final short SOLID = 3;

	public static final short DASHED = 4;

	public static final short DOTTED = 5;

	public static final short RIDGE = 6;

	public static final short OUTSET = 7;

	public static final short GROOVE = 8;

	public static final short INSET = 9;

	public static final Border NONE_BORDER;

	public static final Border HIDDEN_BORDER;

	static {
		NONE_BORDER = new Border(Border.NONE, 0, null);
		HIDDEN_BORDER = new Border(Border.HIDDEN, 0, null);
	}

	/**
	 * Width (thickness).
	 */
	public final double width;

	/**
	 * Style.
	 */
	public final short style;

	/**
	 * Color. null means transparent.
	 */
	public final Color color;

	/**
	 * Upper limit of the border width's <b>used value</b> (points).
	 *
	 * <p>
	 * CSS defines no upper limit, but real browsers all cap it at the saturation value of their internal
	 * representation (Chrome's {@code LayoutUnit} is about 33.55 million px). We take the same approach,
	 * for the same reason that {@code colspan}/{@code rowspan} are clamped to the HTML Standard's limits.
	 * </p>
	 *
	 * <p>
	 * 1e6 pt is about 35 m: nearly 70 times the PDF page-size limit of 14,400 pt (200 inches), far beyond
	 * any legitimate border. It is also one hundredth of
	 * {@link net.zamasoft.foliojet.layout.util.LayoutUtils#DRAWABLE_LIMIT} (1e8 pt), so summing all four
	 * sides or nesting borders still stays within drawable bounds.
	 * </p>
	 *
	 * <p>
	 * Without clamping, declarations such as {@code border-bottom:4294967295px} (= 3.22e9 pt) become
	 * actual dimensions, and the "abnormal drawing height" assertion in {@code BackgroundBorderDrawable}
	 * <b>fails the conversion</b> (WPT {@code css-break/grid/grid-large-end-border-crash.html}).
	 * Even in production with assertions disabled, trying to draw a 3,500 km border does not make it
	 * correct. <b>Clamping input to a sane range is the right layer for this</b>.
	 * </p>
	 */
	public static final double MAX_WIDTH = 1e6;

	public static Border create(short style, double width, Color color) {
		// Used-value limit (see {@link #MAX_WIDTH}). NaN becomes 0 here
		// (all comparisons with NaN are false, so isNull() below cannot catch it).
		if (Double.isNaN(width)) {
			width = 0;
		} else if (width > MAX_WIDTH) {
			width = MAX_WIDTH;
		}
		// SPEC CSS2.1 8.5.3
		switch (style) {
		case Border.NONE:
			if (color == null) {
				return NONE_BORDER;
			}
			width = 0;
			break;
		case Border.HIDDEN:
			if (color == null) {
				return HIDDEN_BORDER;
			}
			width = 0;
			break;
		default:
			break;
		}
		return new Border(style, width, color);
	}

	private Border(short style, double width, Color color) {
		this.style = style;
		this.width = width;
		this.color = color;
	}

	public boolean isVisible() {
		if (this.isNull() || this.color == null) {
			return false;
		}
		return true;
	}

	public boolean isNull() {
		return this.width <= 0;
	}

	public String toString() {
		return "[style=" + this.style + ",width=" + this.width + ",color=" + this.color + "]";
	}

	public int compareTo(Border o) {
		Border next = (Border) o;
		if (next == null) {
			return -1;
		}
		// rule 1
		if (this.style == Border.HIDDEN) {
			if (next.style == Border.HIDDEN) {
				return 0;
			}
			return -1;
		}
		if (next.style == Border.HIDDEN) {
			return 1;
		}
		// rule 2
		if (this.style == Border.NONE) {
			if (next.style == Border.NONE) {
				return 0;
			}
			return 1;
		}
		if (next.style == Border.NONE) {
			return -1;
		}
		// rule 3
		if (next.width > this.width) {
			return 1;
		}
		if (next.width < this.width) {
			return -1;
		}
		if (next.style < this.style) {
			return 1;
		}
		if (next.style > this.style) {
			return -1;
		}
		return 0;
	}

	public boolean equals(Object o) {
		Border b = (Border) o;
		return this.style == b.style && this.width == b.width && this.color.equals(b.color);
	}
}