package net.zamasoft.foliojet.css.value;

/** {@code border-image-outset} の上・右・下・左の値です。 */
public record BorderImageOutsetValue(Value top, Value right, Value bottom, Value left) implements Value {
	public static final BorderImageOutsetValue DEFAULT = new BorderImageOutsetValue(RealValue.ZERO, RealValue.ZERO,
			RealValue.ZERO, RealValue.ZERO);
}
