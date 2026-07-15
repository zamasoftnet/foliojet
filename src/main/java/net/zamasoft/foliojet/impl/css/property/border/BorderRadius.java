package net.zamasoft.foliojet.impl.css.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BorderRadiusValue;
import net.zamasoft.foliojet.style.box.params.RectBorder.Radius;
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
		return Radius.create(((AbsoluteLengthValue) r.hr).getLength(), ((AbsoluteLengthValue) r.vr).getLength());
	}

	public Value getDefault(CSSStyle style) {
		return BorderRadiusValue.ZERO_RADIUS;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		final BorderRadiusValue r = (BorderRadiusValue) value;
		final AbsoluteLengthValue hr = (AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(r.hr, style);
		final AbsoluteLengthValue vr = (AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(r.vr, style);
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
