package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.BackgroundImage;

/**
 * 背景の繰り返し方法です。
 * 
 * @author MIYABE Tatsuhiko
 */
public enum BackgroundRepeatValue implements Value {
	NO_REPEAT_VALUE(BackgroundImage.REPEAT_NO),

	REPEAT_X_VALUE(BackgroundImage.REPEAT_X),

	REPEAT_Y_VALUE(BackgroundImage.REPEAT_Y),

	REPEAT_VALUE(BackgroundImage.REPEAT);

	private final byte backgroundRepeat;

	private BackgroundRepeatValue(byte backgroundRepeat) {
		this.backgroundRepeat = backgroundRepeat;
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