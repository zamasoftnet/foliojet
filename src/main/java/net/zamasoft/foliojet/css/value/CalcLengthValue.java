package net.zamasoft.foliojet.css.value;

/**
 * 絶対長さと割合(パーセント)が混在した calc() の結果です(例: {@code calc(50% + 10px)})。
 * <p>
 * 使用値計算(レイアウト時、パーセントの基準値が定まった時点)まで両成分を
 * 分離したまま持ち回ります。{@code absolute} は{@link AbsoluteLengthValue#getLength()}
 * と同じ規約(PT単位)、{@code ratio} は{@link PercentageValue#getRatio()}と同じ規約
 * (100%=1.0)です。基準値 {@code ref} での実際の長さは {@code absolute + ratio * ref}
 * です(このクラス自身はrefを持たないため計算しません。実際の解決は
 * {@link net.zamasoft.foliojet.layout.util.LayoutUtils}側、
 * {@link net.zamasoft.foliojet.layout.box.params.LengthType#MIXED}経由で行います)。
 * </p>
 */
public final class CalcLengthValue implements Value, QuantityValue {
	private final double absolute;
	private final double ratio;

	/**
	 * 絶対成分と割合成分から値を生成します。どちらか一方が0なら、より単純な
	 * {@link AbsoluteLengthValue}/{@link PercentageValue}を返します
	 * (両方0でもゼロ長のAbsoluteLengthValueを返す)。
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

	/** PT単位の絶対成分。 */
	public double getAbsolute() {
		return this.absolute;
	}

	/** 割合成分(100%=1.0)。 */
	public double getRatio() {
		return this.ratio;
	}

	/**
	 * 絶対成分・割合成分が同符号の場合のみ、確実に負であることが分かる。
	 * 符号が異なる場合は基準値次第で正負が変わるため、確実な判定はできない
	 * (falseを返す。CSS仕様上も、パーセントを含む値の負値判定は使用値計算時まで
	 * 確定しない)。
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
