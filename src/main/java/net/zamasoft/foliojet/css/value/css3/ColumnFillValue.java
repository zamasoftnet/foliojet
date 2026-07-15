package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum ColumnFillValue implements Value {
	AUTO_VALUE(ColumnFillValue.AUTO),

	BALANCE_VALUE(ColumnFillValue.BALANCE);
	public static final byte AUTO = 1;

	public static final byte BALANCE = 2;

	private final byte columnFill;

	private ColumnFillValue(byte textAlign) {
		this.columnFill = textAlign;
	}

	public byte getColumnFill() {
		return this.columnFill;
	}

	public String toString() {
		switch (this.columnFill) {
		case AUTO:
			return "auto";

		case BALANCE:
			return "balance";

		default:
			throw new IllegalStateException();
		}
	}
}