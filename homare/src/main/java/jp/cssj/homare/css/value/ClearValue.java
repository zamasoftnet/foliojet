package jp.cssj.homare.css.value;

import jp.cssj.homare.style.box.params.Types;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ClearValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ClearValue implements Value {
	public static final ClearValue NONE_VALUE = new ClearValue(Types.CLEAR_NONE);

	public static final ClearValue LEFT_VALUE = new ClearValue(Types.CLEAR_START);

	public static final ClearValue RIGHT_VALUE = new ClearValue(Types.CLEAR_END);

	public static final ClearValue START_VALUE = new ClearValue(Types.CLEAR_START);

	public static final ClearValue END_VALUE = new ClearValue(Types.CLEAR_END);

	public static final ClearValue BOTH_VALUE = new ClearValue(Types.CLEAR_BOTH);

	private final byte clear;

	private ClearValue(byte clear) {
		this.clear = clear;
	}

	public short getValueType() {
		return Value.TYPE_CLEAR;
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