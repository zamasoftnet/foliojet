package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.Offset;

/**
 * {@code mask-position}(css-masking-1 §7.6、2026-08-29)。&lt;position&gt;の
 * 文法と解決は{@link BackgroundPosition}を共有し、{@code url()}のマスク画像
 * ({@link MaskImage})の配置に使う。
 */
public class MaskPosition extends BackgroundPosition {
	public static final PrimitivePropertyInfo INFO_X = new MaskPosition();
	public static final PrimitivePropertyInfo INFO_Y = new MaskPosition();
	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_X, INFO_Y };

	public static Offset get(final CSSStyle style) {
		final Value xValue = style.get(INFO_X);
		final Value yValue = style.get(INFO_Y);
		return BoxValueUtils.toOffset(xValue, yValue);
	}

	/** 既定(0% 0%)のままか。既定なら従来の「箱いっぱいに描く」近似を使う。 */
	public static boolean isDefault(final CSSStyle style) {
		return style.get(INFO_X) == net.zamasoft.foliojet.css.value.PercentageValue.ZERO
				&& style.get(INFO_Y) == net.zamasoft.foliojet.css.value.PercentageValue.ZERO;
	}

	protected MaskPosition() {
		super("mask-position");
	}

	@Override
	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}
}
