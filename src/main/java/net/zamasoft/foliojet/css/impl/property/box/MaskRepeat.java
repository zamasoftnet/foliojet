package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundRepeat;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.BackgroundRepeatValue;

/**
 * {@code mask-repeat}(css-masking-1 §7.7、2026-08-29)。文法は
 * {@link BackgroundRepeat}と同じ(space/roundは未対応でrepeat扱い)。
 */
public class MaskRepeat extends BackgroundRepeat {
	public static final PrimitivePropertyInfo INFO = new MaskRepeat();

	public static byte get(final CSSStyle style) {
		return ((BackgroundRepeatValue) style.get(INFO)).getBackgroundRepeat();
	}

	/** 既定(repeat)のままか。 */
	public static boolean isDefault(final CSSStyle style) {
		return style.get(INFO) == BackgroundRepeatValue.REPEAT_VALUE;
	}

	protected MaskRepeat() {
		super("mask-repeat");
	}
}
