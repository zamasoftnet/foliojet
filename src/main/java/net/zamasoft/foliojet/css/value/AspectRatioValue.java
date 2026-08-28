package net.zamasoft.foliojet.css.value;

/**
 * {@code aspect-ratio}の値です(css-sizing-4 §5、2026-08-29)。
 * {@code auto | <ratio> | auto && <ratio>}——{@code ratio}は幅/高さ
 * (0=比率指定なし)。{@code auto}併記は置換要素で「固有比率があれば
 * それを優先し、無ければ指定比率を使う」意味になる。退化した比率
 * ({@code 0}・無限大)は仕様どおり{@code auto}と同じ扱いにする。
 *
 * @author MIYABE Tatsuhiko
 */
public final class AspectRatioValue implements Value {
	/** {@code auto}(既定)。 */
	public static final AspectRatioValue AUTO_VALUE = new AspectRatioValue(true, 0);

	private final boolean auto;

	private final double ratio;

	private AspectRatioValue(final boolean auto, final double ratio) {
		this.auto = auto;
		this.ratio = ratio;
	}

	/**
	 * @param auto  {@code auto}が併記されているか
	 * @param ratio 幅/高さ(退化した値はautoへ畳む)
	 */
	public static AspectRatioValue create(final boolean auto, final double ratio) {
		if (!(ratio > 0) || Double.isInfinite(ratio)) {
			return AUTO_VALUE;
		}
		return new AspectRatioValue(auto, ratio);
	}

	/** {@code auto}が(単独または併記で)指定されているか。 */
	public boolean isAuto() {
		return this.auto;
	}

	/** 指定比率があるか。 */
	public boolean hasRatio() {
		return this.ratio > 0;
	}

	/** 幅/高さ(無ければ0)。 */
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
