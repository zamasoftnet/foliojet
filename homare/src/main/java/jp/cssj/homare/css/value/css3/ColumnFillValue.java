package jp.cssj.homare.css.value.css3;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnFillValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ColumnFillValue implements CSS3Value {
	public static final byte AUTO = 1;

	public static final byte BALANCE = 2;

	public static final ColumnFillValue AUTO_VALUE = new ColumnFillValue(AUTO);

	public static final ColumnFillValue BALANCE_VALUE = new ColumnFillValue(BALANCE);

	private final byte columnFill;

	private ColumnFillValue(byte textAlign) {
		this.columnFill = textAlign;
	}

	public short getValueType() {
		return TYPE_COLUMN_FILL;
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