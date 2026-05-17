package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PercentageValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class PercentageValue implements Value, QuantityValue {
	private final double percentage;

	public static final PercentageValue ZERO = new PercentageValue(0);

	public static final PercentageValue HALF = new PercentageValue(50);

	public static final PercentageValue FULL = new PercentageValue(100);

	public static PercentageValue create(double percentage) {
		if (percentage == 0) {
			return ZERO;
		}
		if (percentage == 50) {
			return HALF;
		}
		if (percentage == 100) {
			return FULL;
		}
		return new PercentageValue(percentage);
	}

	private PercentageValue(double percentage) {
		this.percentage = percentage;
	}

	public boolean isZero() {
		return this.percentage == 0;
	}

	public boolean isNegative() {
		return this.percentage < 0;
	}

	public double getPercentage() {
		return this.percentage;
	}

	public double getRatio() {
		return this.percentage / 100.0;
	}

	public short getValueType() {
		return TYPE_PERCENTAGE;
	}

	public String toString() {
		return this.percentage + "%";
	}
}