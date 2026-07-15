package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.Types;

/**
 * @author MIYABE Tatsuhiko
 */
public enum OverflowValue implements Value {
	VISIBLE_VALUE(Types.OVERFLOW_VISIBLE),

	HIDDEN_VALUE(Types.OVERFLOW_HIDDEN),

	AUTO_VALUE(Types.OVERFLOW_SCROLL),

	SCROLL_VALUE(Types.OVERFLOW_AUTO);

	private final byte overflow;

	private OverflowValue(byte overflow) {
		this.overflow = overflow;
	}

	public byte getOverflow() {
		return this.overflow;
	}

	public String toString() {
		switch (this.overflow) {
		case Types.OVERFLOW_VISIBLE:
			return "visible";

		case Types.OVERFLOW_HIDDEN:
			return "hidden";

		case Types.OVERFLOW_SCROLL:
			return "scroll";

		case Types.OVERFLOW_AUTO:
			return "auto";

		default:
			throw new IllegalStateException();
		}
	}
}