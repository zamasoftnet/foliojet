package net.zamasoft.foliojet.css.value;

/**
 * A computed {@code <angle>} value. Internally uses degrees, the normalized unit for CSS math functions.
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
