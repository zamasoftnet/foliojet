package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum CSSJDirectionModeValue implements Value {
	PHYSICAL_VALUE(CSSJDirectionModeValue.PHYSICAL),

	HORIZONTAL_TB_VALUE(CSSJDirectionModeValue.HORIZONTAL_TB),

	VERTICAL_RL_VALUE(CSSJDirectionModeValue.VERTICAL_RL);
	public static final byte PHYSICAL = 1;
	public static final byte HORIZONTAL_TB = 2;
	public static final byte VERTICAL_RL = 3;

	private final byte directionMode;

	private CSSJDirectionModeValue(byte directionMode) {
		this.directionMode = directionMode;
	}

	public byte getDirectionMode() {
		return this.directionMode;
	}

	public String toString() {
		switch (this.directionMode) {
		case PHYSICAL:
			return "physical";

		case HORIZONTAL_TB:
			return "horizontal-tb";

		case VERTICAL_RL:
			return "vertical-rl";

		default:
			throw new IllegalStateException();
		}
	}
}