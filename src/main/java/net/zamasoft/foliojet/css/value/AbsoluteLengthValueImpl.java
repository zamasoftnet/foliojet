package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.ua.UserAgent;

class AbsoluteLengthValueImpl extends AbsoluteLengthValue {
	private final UserAgent ua;

	private final Unit unit;

	private final double length;

	AbsoluteLengthValueImpl(UserAgent ua, Unit unit, double length) {
		switch (unit) {
		case IN:
		case CM:
		case MM:
		case PT:
		case PC:
		case PX:
			break;
		default:
			throw new IllegalArgumentException(unit.toString());
		}
		this.ua = ua;
		this.unit = unit;
		this.length = length;
	}

	public Unit getUnit() {
		return this.unit;
	}

	public double getLength(Unit unit) {
		return LengthUtils.convert(this.ua, this.length, this.unit, unit);
	}

	public double getLength() {
		return this.getLength(Unit.PT);
	}

	public int compareTo(AbsoluteLengthValue length) {
		double a = this.getLength(Unit.MM);
		double b = length.getLength(Unit.MM);
		return (a == b) ? 0 : ((a > b) ? 1 : -1);
	}

	public boolean isNegative() {
		return this.length < 0f;
	}

	public boolean isZero() {
		return this.length == 0f;
	}
}
