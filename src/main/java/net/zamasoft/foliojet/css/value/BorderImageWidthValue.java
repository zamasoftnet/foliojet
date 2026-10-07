package net.zamasoft.foliojet.css.value;

/** Top, right, bottom, and left values of {@code border-image-width}. */
public record BorderImageWidthValue(Value top, Value right, Value bottom, Value left) implements Value {
	public static final BorderImageWidthValue DEFAULT = new BorderImageWidthValue(RealValue.ONE, RealValue.ONE,
			RealValue.ONE, RealValue.ONE);
}
