package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: DirectionValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class DirectionValue implements Value {
	public static final DirectionValue LTR_VALUE = new DirectionValue(AbstractTextParams.DIRECTION_LTR);

	public static final DirectionValue RTL_VALUE = new DirectionValue(AbstractTextParams.DIRECTION_RTL);

	private final byte direction;

	private DirectionValue(byte direction) {
		this.direction = direction;
	}

	public short getValueType() {
		return TYPE_DIRECTION;
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