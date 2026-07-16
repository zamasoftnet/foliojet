package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.Border;

/**
 * 境界線のスタイルです。 DOUBLE以下の値はSPEC CSS2 17.6.2 規則3の順に並べられています。
 * 
 * @author MIYABE Tatsuhiko
 */
public enum BorderStyleValue implements Value {
	NONE_VALUE(BorderStyleValue.NONE),

	HIDDEN_VALUE(BorderStyleValue.HIDDEN),

	DOUBLE_VALUE(BorderStyleValue.DOUBLE),

	SOLID_VALUE(BorderStyleValue.SOLID),

	DASHED_VALUE(BorderStyleValue.DASHED),

	DOTTED_VALUE(BorderStyleValue.DOTTED),

	RIDGE_VALUE(BorderStyleValue.RIDGE),

	OUTSET_VALUE(BorderStyleValue.OUTSET),

	GROOVE_VALUE(BorderStyleValue.GROOVE),

	INSET_VALUE(BorderStyleValue.INSET);
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

	private final short borderStyle;

	private BorderStyleValue(short borderStyle) {
		this.borderStyle = borderStyle;
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