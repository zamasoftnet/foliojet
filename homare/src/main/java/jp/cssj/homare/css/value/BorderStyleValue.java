package jp.cssj.homare.css.value;

import jp.cssj.homare.style.box.params.Border;

/**
 * 境界線のスタイルです。 DOUBLE以下の値はSPEC CSS2 17.6.2 規則3の順に並べられています。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderStyleValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderStyleValue implements Value {
	public static final short NONE = Border.NONE;

	public static final short HIDDEN = Border.HIDDEN;

	public static final short DOUBLE = Border.DOUBLE;

	public static final short SOLID = Border.SOLID;

	public static final short DASHED = Border.DASHED;

	public static final short DOTTED = Border.DOTTED;

	public static final short RIDGE = Border.RIDGE;

	public static final short OUTSET = Border.OUTSET;

	public static final short GROOVE = Border.GROOVE;

	public static final short INSET = Border.INSET;

	public static final BorderStyleValue NONE_VALUE = new BorderStyleValue(NONE);

	public static final BorderStyleValue HIDDEN_VALUE = new BorderStyleValue(HIDDEN);

	public static final BorderStyleValue DOUBLE_VALUE = new BorderStyleValue(DOUBLE);

	public static final BorderStyleValue SOLID_VALUE = new BorderStyleValue(SOLID);

	public static final BorderStyleValue DASHED_VALUE = new BorderStyleValue(DASHED);

	public static final BorderStyleValue DOTTED_VALUE = new BorderStyleValue(DOTTED);

	public static final BorderStyleValue RIDGE_VALUE = new BorderStyleValue(RIDGE);

	public static final BorderStyleValue OUTSET_VALUE = new BorderStyleValue(OUTSET);

	public static final BorderStyleValue GROOVE_VALUE = new BorderStyleValue(GROOVE);

	public static final BorderStyleValue INSET_VALUE = new BorderStyleValue(INSET);

	private final short borderStyle;

	private BorderStyleValue(short borderStyle) {
		this.borderStyle = borderStyle;
	}

	public short getValueType() {
		return TYPE_BORDER_STYLE;
	}

	public short getBorderStyle() {
		return this.borderStyle;
	}

	public String toString() {
		switch (this.borderStyle) {
		case NONE:
			return "none";

		case HIDDEN:
			return "hidden";

		case DOTTED:
			return "dotted";

		case DASHED:
			return "dashed";

		case SOLID:
			return "solid";

		case DOUBLE:
			return "double";

		case GROOVE:
			return "groove";

		case RIDGE:
			return "ridge";

		case INSET:
			return "inset";

		case OUTSET:
			return "outset";

		default:
			throw new IllegalStateException();
		}
	}
}