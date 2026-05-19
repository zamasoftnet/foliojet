package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteLengthValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbsoluteLengthValue implements LengthValue, Comparable<AbsoluteLengthValue> {
	public short getValueType() {
		return Value.TYPE_ABSOLUTE_LENGTH;
	}

	public abstract short getUnitType();

	public abstract double getLength(short unitType);

	public abstract double getLength();

	public static final AbsoluteLengthValue ZERO = new AbsoluteLengthValue() {
		public short getUnitType() {
			return UNIT_PT;
		}

		public double getLength(short unitType) {
			return 0;
		}

		public double getLength() {
			return 0;
		}

		public int compareTo(AbsoluteLengthValue o) {
			AbsoluteLengthValue length = (AbsoluteLengthValue) o;
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

	public static AbsoluteLengthValue create(UserAgent ua, double value, short unitType) {
		if (value == 0) {
			return ZERO;
		}
		return new AbsoluteLengthValueImpl(ua, unitType, value);
	}

	public static AbsoluteLengthValue create(UserAgent ua, double value) {
		if (value == 0) {
			return ZERO;
		}
		return new AbsoluteLengthValueImpl(ua, value);
	}

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style) {
		return this;
	}

	public String toString() {
		StringBuffer str = new StringBuffer();
		str.append(this.getLength(this.getUnitType()));
		switch (this.getUnitType()) {
		case UNIT_IN:
			str.append("in");
			break;

		case UNIT_CM:
			str.append("cm");
			break;

		case UNIT_MM:
			str.append("mm");
			break;

		case UNIT_PT:
			str.append("pt");
			break;

		case UNIT_PC:
			str.append("pc");
			break;

		case UNIT_PX:
			str.append("px");
			break;

		default:
			throw new IllegalStateException();
		}
		return str.toString();
	}
}