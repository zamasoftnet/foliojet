package net.zamasoft.foliojet.css.value;

/** Top, right, bottom, and left values of {@code border-image-outset}. */
public record BorderImageOutsetValue(Value top, Value right, Value bottom, Value left) implements Value {
	public static final BorderImageOutsetValue DEFAULT = new BorderImageOutsetValue(RealValue.ZERO, RealValue.ZERO,
			RealValue.ZERO, RealValue.ZERO);
}
