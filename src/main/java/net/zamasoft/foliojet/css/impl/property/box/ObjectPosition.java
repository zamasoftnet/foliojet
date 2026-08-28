package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.Offset;

/**
 * <a href="https://drafts.csswg.org/css-images-3/#the-object-position">
 * object-position 特性</a>です。&lt;position&gt;の文法と解決は
 * {@link BackgroundPosition}を共有し、既定値(50% 50%)だけが異なります。
 *
 * @author MIYABE Tatsuhiko
 */
public class ObjectPosition extends BackgroundPosition {
	public static final PrimitivePropertyInfo INFO_X = new ObjectPosition();

	public static final PrimitivePropertyInfo INFO_Y = new ObjectPosition();

	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_X, INFO_Y };

	public static Offset get(CSSStyle style) {
		Value xValue = style.get(INFO_X);
		Value yValue = style.get(INFO_Y);
		return BoxValueUtils.toOffset(xValue, yValue);
	}

	protected ObjectPosition() {
		super("object-position");
	}

	@Override
	public Value getDefault(CSSStyle style) {
		return PercentageValue.HALF;
	}

	@Override
	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}
}
