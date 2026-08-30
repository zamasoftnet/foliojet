package net.zamasoft.foliojet.css.value;

/**
 * {@code <angle>}の計算値です。内部表現はCSS数学関数の正規化単位である度です。
 */
public final class AngleValue implements QuantityValue {
	private final double degrees;

	public static final AngleValue ZERO = new AngleValue(0);

	public static AngleValue create(double degrees) {
		return degrees == 0 ? ZERO : new AngleValue(degrees);
	}

	private AngleValue(double degrees) {
		this.degrees = degrees;
	}

	public double getDegrees() {
		return this.degrees;
	}

	public double getRadians() {
		return Math.toRadians(this.degrees);
	}

	@Override
	public boolean isZero() {
		return this.degrees == 0;
	}

	@Override
	public boolean isNegative() {
		return this.degrees < 0;
	}

	@Override
	public String toString() {
		return this.degrees + "deg";
	}
}
