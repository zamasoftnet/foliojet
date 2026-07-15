package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 */
public enum DirectionValue implements Value {
	LTR_VALUE(AbstractTextParams.DIRECTION_LTR),

	RTL_VALUE(AbstractTextParams.DIRECTION_RTL);

	private final byte direction;

	private DirectionValue(byte direction) {
		this.direction = direction;
	}

	public byte getDirection() {
		return this.direction;
	}

	public String toString() {
		switch (this.direction) {
		case AbstractTextParams.DIRECTION_LTR:
			return "ltr";

		case AbstractTextParams.DIRECTION_RTL:
			return "rtl";

		default:
			throw new IllegalStateException();
		}
	}
}