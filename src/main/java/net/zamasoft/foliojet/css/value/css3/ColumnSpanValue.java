package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum ColumnSpanValue implements Value {
	SINGLE_VALUE((byte) 1),

	ALL_VALUE(ColumnSpanValue.ALL);
	public static final byte ALL = -1;

	private final byte columnSpan;

	private ColumnSpanValue(byte textAlign) {
		this.columnSpan = textAlign;
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