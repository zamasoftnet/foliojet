package net.zamasoft.foliojet.css.value;

/** {@code border-image-width} の上・右・下・左の値です。 */
public record BorderImageWidthValue(Value top, Value right, Value bottom, Value left) implements Value {
	public static final BorderImageWidthValue DEFAULT = new BorderImageWidthValue(RealValue.ONE, RealValue.ONE,
			RealValue.ONE, RealValue.ONE);
}
