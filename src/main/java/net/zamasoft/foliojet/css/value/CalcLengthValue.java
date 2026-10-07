package net.zamasoft.foliojet.css.value;

/**
 * Result of calc() mixing absolute length and percentage (e.g. {@code calc(50% + 10px)}).
 * <p>
 * Carries both components separately until used-value computation (during layout,
 * when the percentage reference is known). {@code absolute} follows
 * {@link AbsoluteLengthValue#getLength()} (PT units); {@code ratio} follows
 * {@link PercentageValue#getRatio()} (100%=1.0). Actual length with reference
 * {@code ref} is {@code absolute + ratio * ref}. This class does not hold ref and
 * therefore does not calculate it; actual resolution occurs in
 * {@link net.zamasoft.foliojet.layout.util.LayoutUtils}, via
 * {@link net.zamasoft.foliojet.layout.box.params.LengthType#MIXED}.
 * </p>
 */
public final class CalcLengthValue implements Value, QuantityValue {
	private final double absolute;
	private final double ratio;

	/**
	 * Creates a value from absolute and ratio components. If either is 0, returns the
	 * simpler {@link AbsoluteLengthValue}/{@link PercentageValue}
	 * (if both are 0, returns a zero-length AbsoluteLengthValue).
	 */
	public static QuantityValue create(net.zamasoft.foliojet.ua.UserAgent ua, double absolute, double ratio) {
		if (ratio == 0) {
			return AbsoluteLengthValue.create(ua, absolute);
		}
		if (absolute == 0) {
			return PercentageValue.create(ratio * 100);
		}
		return new CalcLengthValue(absolute, ratio);
	}

	private CalcLengthValue(double absolute, double ratio) {
		this.absolute = absolute;
		this.ratio = ratio;
	}

	/** Absolute component in PT units. */
	public double getAbsolute() {
		return this.absolute;
	}

	/** Ratio component (100%=1.0). */
	public double getRatio() {
		return this.ratio;
	}

	/**
	 * The result is certainly negative only when absolute and ratio components have the
	 * same sign. With opposite signs, the reference value determines the sign, so a definite
	 * check is impossible (returns false; CSS likewise defers negativity checks for
	 * percentage-containing values until used-value computation).
	 */
	public boolean isNegative() {
		return this.absolute < 0 && this.ratio < 0;
	}

	public boolean isZero() {
		return this.absolute == 0 && this.ratio == 0;
	}

	public String toString() {
		return "calc(" + this.absolute + "pt + " + (this.ratio * 100) + "%)";
	}
}
