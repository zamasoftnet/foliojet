package net.zamasoft.foliojet.impl.css.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * padding-top / padding-right / padding-bottom / padding-left 特性です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class Padding extends AbstractPrimitivePropertyInfo {
	public static final Padding TOP = new Padding(Side.TOP);

	public static final Padding RIGHT = new Padding(Side.RIGHT);

	public static final Padding BOTTOM = new Padding(Side.BOTTOM);

	public static final Padding LEFT = new Padding(Side.LEFT);

	private static final Padding[] BY_SIDE = { TOP, RIGHT, BOTTOM, LEFT };

	private Padding(Side side) {
		super("padding-" + side.text());
	}

	public static Value get(CSSStyle style, Side side) {
		return style.get(BY_SIDE[side.resolve(style).ordinal()]);
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
