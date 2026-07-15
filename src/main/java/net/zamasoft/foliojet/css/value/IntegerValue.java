package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class IntegerValue implements Value, QuantityValue {
	private final int intValue;

	public static final IntegerValue ZERO = new IntegerValue(0);

	public static final IntegerValue ONE = new IntegerValue(1);

	public static final IntegerValue TWO = new IntegerValue(2);

	public static final IntegerValue THREE = new IntegerValue(3);

	public static IntegerValue create(int a) {
		switch (a) {
		case 0:
			return ZERO;
		case 1:
			return ONE;
		case 2:
			return TWO;
		case 3:
			return THREE;
		default:
			return new IntegerValue(a);
		}
	}

	private IntegerValue(int intValue) {
		this.intValue = intValue;
	}

	public boolean isNegative() {
		return this.intValue < 0;
	}

	public boolean isZero() {
		return this.intValue == 0;
	}

	public int getInteger() {
		return this.intValue;
	}

	public String toString() {
		return String.valueOf(this.intValue);
	}
}