package net.zamasoft.foliojet.css.value;

/** Four side values and the {@code fill} flag of {@code border-image-slice}. */
public record BorderImageSliceValue(Value top, Value right, Value bottom, Value left, boolean fill)
		implements Value {
	public static final BorderImageSliceValue DEFAULT = new BorderImageSliceValue(PercentageValue.FULL,
			PercentageValue.FULL, PercentageValue.FULL, PercentageValue.FULL, false);
}
