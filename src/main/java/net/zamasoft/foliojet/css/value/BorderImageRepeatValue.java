package net.zamasoft.foliojet.css.value;

/** Horizontal and vertical repetition modes of {@code border-image-repeat}. */
public record BorderImageRepeatValue(Mode horizontal, Mode vertical) implements Value {
	public enum Mode {
		STRETCH, REPEAT, ROUND, SPACE
	}

	public static final BorderImageRepeatValue STRETCH = new BorderImageRepeatValue(Mode.STRETCH, Mode.STRETCH);
}
