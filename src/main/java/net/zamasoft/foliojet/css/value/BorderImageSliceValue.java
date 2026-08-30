package net.zamasoft.foliojet.css.value;

/** {@code border-image-slice} の4辺値と {@code fill} フラグです。 */
public record BorderImageSliceValue(Value top, Value right, Value bottom, Value left, boolean fill)
		implements Value {
	public static final BorderImageSliceValue DEFAULT = new BorderImageSliceValue(PercentageValue.FULL,
			PercentageValue.FULL, PercentageValue.FULL, PercentageValue.FULL, false);
}
