package jp.cssj.homare.style.box.params;

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
	 * 幅(太さ)です。
	 */
	public final double width;

	/**
	 * スタイルです。
	 */
	public final short style;

	/**
	 * 色です。 透明の場合はnullです。
	 */
	public final Color color;

	public static Border create(short style, double width, Color color) {
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