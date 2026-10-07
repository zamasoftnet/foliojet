package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundRepeat;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.BackgroundRepeatValue;

/**
 * {@code mask-repeat} (css-masking-1 §7.7, 2026-08-29). Uses the same grammar as
 * {@link BackgroundRepeat} (space/round are unsupported and treated as repeat).
 */
public class MaskRepeat extends BackgroundRepeat {
	public static final PrimitivePropertyInfo INFO = new MaskRepeat();

	public static byte get(final CSSStyle style) {
		return ((BackgroundRepeatValue) style.get(INFO)).getBackgroundRepeat();
	}

	/** Whether the value is still the default (repeat). */
	public static boolean isDefault(final CSSStyle style) {
		return style.get(INFO) == BackgroundRepeatValue.REPEAT_VALUE;
	}

	protected MaskRepeat() {
		super("mask-repeat");
	}
}
