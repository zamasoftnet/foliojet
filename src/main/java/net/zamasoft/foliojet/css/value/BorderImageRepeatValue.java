package net.zamasoft.foliojet.css.value;

/** {@code border-image-repeat} の横方向・縦方向の反復方式です。 */
public record BorderImageRepeatValue(Mode horizontal, Mode vertical) implements Value {
	public enum Mode {
		STRETCH, REPEAT, ROUND, SPACE
	}

	public static final BorderImageRepeatValue STRETCH = new BorderImageRepeatValue(Mode.STRETCH, Mode.STRETCH);
}
