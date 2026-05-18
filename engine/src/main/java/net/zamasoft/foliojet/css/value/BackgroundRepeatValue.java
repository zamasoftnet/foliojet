package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.BackgroundImage;

/**
 * 背景の繰り返し方法です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundRepeatValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BackgroundRepeatValue implements Value {
	public static final BackgroundRepeatValue NO_REPEAT_VALUE = new BackgroundRepeatValue(BackgroundImage.REPEAT_NO);

	public static final BackgroundRepeatValue REPEAT_X_VALUE = new BackgroundRepeatValue(BackgroundImage.REPEAT_X);

	public static final BackgroundRepeatValue REPEAT_Y_VALUE = new BackgroundRepeatValue(BackgroundImage.REPEAT_Y);

	public static final BackgroundRepeatValue REPEAT_VALUE = new BackgroundRepeatValue(BackgroundImage.REPEAT);

	private final byte backgroundRepeat;

	private BackgroundRepeatValue(byte backgroundRepeat) {
		this.backgroundRepeat = backgroundRepeat;
	}

	public short getValueType() {
		return TYPE_BACKGROUND_REPEAT;
	}

	public byte getBackgroundRepeat() {
		return this.backgroundRepeat;
	}

	public String toString() {
		switch (this.backgroundRepeat) {
		case BackgroundImage.REPEAT_NO:
			return "no-repeat";

		case BackgroundImage.REPEAT_X:
			return "repeat-x";

		case BackgroundImage.REPEAT_Y:
			return "repeat-y";

		case BackgroundImage.REPEAT:
			return "repeat";

		default:
			throw new IllegalStateException();
		}
	}
}