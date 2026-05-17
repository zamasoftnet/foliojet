package jp.cssj.homare.css.value;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.impl.css.property.FontSize;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: EmLengthValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class EmLengthValue implements LengthValue {
	private static final EmLengthValue ZERO_VALUE = new EmLengthValue(0);

	private final double value;

	public static EmLengthValue create(double value) {
		if (value == 0) {
			return ZERO_VALUE;
		}
		return new EmLengthValue(value);
	}

	private EmLengthValue(double value) {
		this.value = value;
	}

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style) {
		double fontSize = FontSize.get(style);
		return AbsoluteLengthValue.create(style.getUserAgent(), fontSize * this.value);
	}

	public short getValueType() {
		return Value.TYPE_EM_LENGTH;
	}

	public short getUnitType() {
		return LengthValue.UNIT_FR;
	}

	public boolean isNegative() {
		return this.value < 0;
	}

	public boolean isZero() {
		return this.value == 0;
	}

	public String toString() {
		return this.value + "em";
	}
}