package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteLengthValueImpl.java 1552 2018-04-26 01:43:24Z miyabe $
 */
class AbsoluteLengthValueImpl extends AbsoluteLengthValue {
	private final UserAgent ua;

	private short unitType;

	private double length;

	AbsoluteLengthValueImpl(UserAgent ua, short unitType, double length) {
		switch (unitType) {
		case UNIT_IN:
			this.setInches(length);
			break;

		case UNIT_CM:
			this.setCentimeters(length);
			break;

		case UNIT_MM:
			this.setMillimeters(length);
			break;

		case UNIT_PT:
			this.setPoints(length);
			break;

		case UNIT_PC:
			this.setPicas(length);
			break;

		case UNIT_PX:
			this.setPixels(length);
			break;

		default:
			throw new IllegalArgumentException();
		}
		this.ua = ua;
	}

	AbsoluteLengthValueImpl(UserAgent ua, double length) {
		this(ua, LengthValue.UNIT_PT, length);
	}

	public short getUnitType() {
		return this.unitType;
	}

	public double getLength(short unitType) {
		switch (unitType) {
		case UNIT_IN:
			return this.getInches();

		case UNIT_CM:
			return this.getCentimeters();

		case UNIT_MM:
			return this.getMillimeters();

		case UNIT_PT:
			return this.getPoints();

		case UNIT_PC:
			return this.getPicas();

		case UNIT_PX:
			return this.getPixels();

		default:
			throw new IllegalArgumentException();
		}
	}

	/**
	 * PT単位で長さを返します。
	 * 
	 * @return
	 */
	public double getLength() {
		return this.getLength(LengthValue.UNIT_PT);
	}

	protected void setInches(double inches) {
		this.length = inches;
		this.unitType = UNIT_IN;
	}

	protected void setCentimeters(double centimeters) {
		this.length = centimeters;
		this.unitType = UNIT_CM;
	}

	protected void setMillimeters(double millimeters) {
		this.length = millimeters;
		this.unitType = UNIT_MM;
	}

	protected void setPoints(double points) {
		this.length = points;
		this.unitType = UNIT_PT;
	}

	protected void setPicas(double picas) {
		this.length = picas;
		this.unitType = UNIT_PC;
	}

	protected void setPixels(double pixels) {
		this.length = pixels;
		this.unitType = UNIT_PX;
	}

	protected double getInches() {
		return LengthUtils.convert(this.ua, this.length, this.unitType, UNIT_IN);
	}

	protected double getCentimeters() {
		return LengthUtils.convert(this.ua, this.length, this.unitType, UNIT_CM);
	}

	protected double getMillimeters() {
		return LengthUtils.convert(this.ua, this.length, this.unitType, UNIT_MM);
	}

	protected double getPoints() {
		return LengthUtils.convert(this.ua, this.length, this.unitType, UNIT_PT);
	}

	protected double getPicas() {
		return LengthUtils.convert(this.ua, this.length, this.unitType, UNIT_PC);
	}

	protected double getPixels() {
		return LengthUtils.convert(this.ua, this.length, this.unitType, UNIT_PX);
	}

	public int compareTo(AbsoluteLengthValue o) {
		AbsoluteLengthValue length = (AbsoluteLengthValue) o;
		double a = this.getLength(UNIT_MM);
		double b = length.getLength(UNIT_MM);
		return (a == b) ? 0 : ((a > b) ? 1 : -1);
	}

	public boolean isNegative() {
		return this.length < 0f;
	}

	public boolean isZero() {
		return this.length == 0f;
	}
}