package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.Types;

/**
 * @author MIYABE Tatsuhiko
 */
public enum ClearValue implements Value {
	NONE_VALUE(Types.CLEAR_NONE),

	LEFT_VALUE(Types.CLEAR_START),

	RIGHT_VALUE(Types.CLEAR_END),

	START_VALUE(Types.CLEAR_START),

	END_VALUE(Types.CLEAR_END),

	BOTH_VALUE(Types.CLEAR_BOTH);

	private final byte clear;

	private ClearValue(byte clear) {
		this.clear = clear;
	}

	public byte getClear() {
		return this.clear;
	}

	public String toString() {
		switch (this.clear) {
		case Types.CLEAR_NONE:
			return "none";

		case Types.CLEAR_START:
			return "left";

		case Types.CLEAR_END:
			return "right";

		case Types.CLEAR_BOTH:
			return "both";

		default:
			throw new IllegalStateException();
		}
	}
}