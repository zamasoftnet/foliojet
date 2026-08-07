package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BorderRadiusValue;
import net.zamasoft.foliojet.layout.box.params.RectBorder.Radius;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * border-*-radius 特性(4隅)です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class BorderRadius extends AbstractPrimitivePropertyInfo {
	public static final BorderRadius TOP_LEFT = new BorderRadius(Corner.TOP_LEFT);

	public static final BorderRadius TOP_RIGHT = new BorderRadius(Corner.TOP_RIGHT);

	public static final BorderRadius BOTTOM_RIGHT = new BorderRadius(Corner.BOTTOM_RIGHT);

	public static final BorderRadius BOTTOM_LEFT = new BorderRadius(Corner.BOTTOM_LEFT);

	private static final BorderRadius[] BY_CORNER = { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT };

	private BorderRadius(Corner corner) {
		super("border-" + corner.text() + "-radius");
	}

	public static Radius get(CSSStyle style, Corner corner) {
		final BorderRadiusValue r = (BorderRadiusValue) style.get(BY_CORNER[corner.resolve(style).ordinal()]);
		// パーセント成分は寸法確定後の描画時に解決するため比率のまま運ぶ
		final double hr, hrRatio, vr, vrRatio;
		if (r.hr instanceof PercentageValue percent) {
			hr = 0;
			hrRatio = percent.getRatio();
		} else {
			hr = ((AbsoluteLengthValue) r.hr).getLength();
			hrRatio = 0;
		}
		if (r.vr instanceof PercentageValue percent) {
			vr = 0;
			vrRatio = percent.getRatio();
		} else {
			vr = ((AbsoluteLengthValue) r.vr).getLength();
			vrRatio = 0;
		}
		return Radius.create(hr, vr, hrRatio, vrRatio);
	}

	public Value getDefault(CSSStyle style) {
		return BorderRadiusValue.ZERO_RADIUS;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		final BorderRadiusValue r = (BorderRadiusValue) value;
		// パーセントは計算値でも比率のまま(emExToAbsoluteLengthは%を素通し)
		final QuantityValue hr = (QuantityValue) ValueUtils.emExToAbsoluteLength(r.hr, style);
		final QuantityValue vr = (QuantityValue) ValueUtils.emExToAbsoluteLength(r.vr, style);
		return BorderRadiusValue.create(hr, vr);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final BorderRadiusValue value = BorderValueUtils.toBorderRadius(ua, tokens);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
