package net.zamasoft.foliojet.css.value;

/**
 * An {@code aspect-ratio} value (css-sizing-4 §5, 2026-08-29).
 * {@code auto | <ratio> | auto && <ratio>}: {@code ratio} is width/height
 * (0=no specified ratio). For replaced elements, accompanying {@code auto} means
 * prefer the intrinsic ratio if present, otherwise use the specified ratio.
 * Treat degenerate ratios ({@code 0}, infinity) as {@code auto}, as specified.
 *
 * @author MIYABE Tatsuhiko
 */
public final class AspectRatioValue implements Value {
	/** {@code auto} (default). */
	public static final AspectRatioValue AUTO_VALUE = new AspectRatioValue(true, 0);

	private final boolean auto;

	private final double ratio;

	private AspectRatioValue(final boolean auto, final double ratio) {
		this.auto = auto;
		this.ratio = ratio;
	}

	/**
	 * @param auto  whether {@code auto} accompanies the ratio
	 * @param ratio width/height (fold degenerate values into auto)
	 */
	public static AspectRatioValue create(final boolean auto, final double ratio) {
		if (!(ratio > 0) || Double.isInfinite(ratio)) {
			return AUTO_VALUE;
		}
		return new AspectRatioValue(auto, ratio);
	}

	/** Whether {@code auto} is specified (alone or with a ratio). */
	public boolean isAuto() {
		return this.auto;
	}

	/** Whether a ratio is specified. */
	public boolean hasRatio() {
		return this.ratio > 0;
	}

	/** Width/height (0 if absent). */
	public double getRatio() {
		return this.ratio;
	}

	@Override
	public String toString() {
		if (!this.hasRatio()) {
			return "auto";
		}
		return (this.auto ? "auto " : "") + this.ratio;
	}
}
