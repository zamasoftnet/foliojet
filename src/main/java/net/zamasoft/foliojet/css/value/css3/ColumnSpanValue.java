package net.zamasoft.foliojet.css.value.css3;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnSpanValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ColumnSpanValue implements CSS3Value {
	public static final byte ALL = -1;

	public static final ColumnSpanValue SINGLE_VALUE = new ColumnSpanValue((byte) 1);

	public static final ColumnSpanValue ALL_VALUE = new ColumnSpanValue(ALL);

	private final byte columnSpan;

	private ColumnSpanValue(byte textAlign) {
		this.columnSpan = textAlign;
	}

	public short getValueType() {
		return TYPE_COLUMN_SPAN;
	}

	public byte getColumnSpan() {
		return this.columnSpan;
	}

	public String toString() {
		switch (this.columnSpan) {
		case -1:
			return "all";

		default:
			return String.valueOf(this.columnSpan);
		}
	}
}