package jp.cssj.homare.css.value.css3;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.impl.css.property.FontSize;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: EmLengthValue.java 1474 2016-04-23 06:22:56Z miyabe $
 */
public class RemLengthValue implements LengthValue {
	private static final RemLengthValue ZERO_VALUE = new RemLengthValue(0);

	private final double value;

	public static RemLengthValue create(double value) {
		if (value == 0) {
			return ZERO_VALUE;
		}
		return new RemLengthValue(value);
	}

	private RemLengthValue(double value) {
		this.value = value;
	}

	public AbsoluteLengthValue toAbsoluteLength(CSSStyle style) {
		double fontSize = FontSize.get(style.getRootStyle());
		return AbsoluteLengthValue.create(style.getUserAgent(), fontSize * this.value);
	}

	public short getValueType() {
		return CSS3Value.TYPE_REM_LENGTH;
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
		return this.value + "rem";
	}
}