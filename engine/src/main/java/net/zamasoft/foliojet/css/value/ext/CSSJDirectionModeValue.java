package net.zamasoft.foliojet.css.value.ext;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJDirectionModeValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJDirectionModeValue implements ExtValue {
	public static final byte PHYSICAL = 1;
	public static final byte HORIZONTAL_TB = 2;
	public static final byte VERTICAL_RL = 3;

	public static final CSSJDirectionModeValue PHYSICAL_VALUE = new CSSJDirectionModeValue(PHYSICAL);

	public static final CSSJDirectionModeValue HORIZONTAL_TB_VALUE = new CSSJDirectionModeValue(HORIZONTAL_TB);

	public static final CSSJDirectionModeValue VERTICAL_RL_VALUE = new CSSJDirectionModeValue(VERTICAL_RL);

	private final byte directionMode;

	private CSSJDirectionModeValue(byte directionMode) {
		this.directionMode = directionMode;
	}

	public short getValueType() {
		return TYPE_CSSJ_DIRECTION_MODE;
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