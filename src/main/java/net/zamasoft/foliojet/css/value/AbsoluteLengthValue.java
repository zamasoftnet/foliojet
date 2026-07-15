package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 絶対(デバイス相対を含む)長さです。
 */
public abstract class AbsoluteLengthValue implements LengthValue, Comparable<AbsoluteLengthValue> {
	public abstract Unit getUnit();

	/**
	 * 指定した単位での長さを返します。
	 */
	public abstract double getLength(Unit unit);

	/**
	 * PT単位で長さを返します。
	 */
	public abstract double getLength();

	public static final AbsoluteLengthValue ZERO = new AbsoluteLengthValue() {
		public Unit getUnit() {
			return Unit.PT;
		}

		public double getLength(Unit unit) {
			return 0;
		}

		public double getLength() {
			return 0;
		}

		public int compareTo(AbsoluteLengthValue length) {
			if (length.isZero()) {
				return 0;
			}
			if (length.isNegative()) {
				return 1;
			}
			return -1;
		}

		public boolean isNegative() {
			return false;
		}

		public boolean isZero() {
			return true;
		}
	};

	public static AbsoluteLengthValue create(UserAgent ua, double value, Unit unit) {
		if (value == 0) {
			return ZERO;
		}
		return new AbsoluteLengthValueImpl(ua, unit, value);
	}

	public static AbsoluteLengthValue create(UserAgent ua, double value) {
		if (value == 0) {
			return ZERO;
		}
		return new AbsoluteLengthValueImpl(ua, Unit.PT, value);
	}

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style) {
		return this;
	}

	public String toString() {
		return this.getLength(this.getUnit()) + this.getUnit().name().toLowerCase();
	}
}
