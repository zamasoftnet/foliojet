package net.zamasoft.foliojet.layout.box.params;

/**
 * 長さ値の指定方法です。Length/Dimension/Insets/Offsetで共用します。
 */
public enum LengthType {
	ABSOLUTE, RELATIVE, AUTO;

	static final LengthType[] VALUES = values();
}
