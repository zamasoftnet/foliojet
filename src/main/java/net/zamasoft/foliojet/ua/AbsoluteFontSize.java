package net.zamasoft.foliojet.ua;

/**
 * Absolute font-size keywords, with ratios relative to the medium size.
 */
public enum AbsoluteFontSize {
	XX_SMALL(3 / 5.0),

	X_SMALL(3 / 4.0),

	SMALL(8 / 9.0),

	MEDIUM(1),

	LARGE(6 / 5.0),

	X_LARGE(3 / 2.0),

	XX_LARGE(2);

	private final double ratio;

	private AbsoluteFontSize(double ratio) {
		this.ratio = ratio;
	}

	/**
	 * Returns the scale factor relative to medium.
	 */
	public double ratio() {
		return this.ratio;
	}
}
