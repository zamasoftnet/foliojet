package net.zamasoft.foliojet.ua;

/**
 * font-size の絶対サイズキーワードです。中間サイズ(medium)に対する比率を持ちます。
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
	 * mediumに対する倍率を返します。
	 */
	public double ratio() {
		return this.ratio;
	}
}
