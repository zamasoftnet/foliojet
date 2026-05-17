package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: RealValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class RealValue implements Value, QuantityValue {
	private final double real;

	public static final RealValue ZERO = new RealValue(0);
	public static final RealValue ONE = new RealValue(1);

	public static RealValue create(double real) {
		if (real == 0) {
			return ZERO;
		}
		if (real == 1) {
			return ONE;
		}
		return new RealValue(real);
	}

	private RealValue(double real) {
		this.real = real;
	}

	public boolean isNegative() {
		return this.real < 0;
	}

	public boolean isZero() {
		return this.real == 0;
	}

	public double getReal() {
		return this.real;
	}

	public short getValueType() {
		return TYPE_REAL;
	}

	public String toString() {
		return String.valueOf(this.real);
	}
}