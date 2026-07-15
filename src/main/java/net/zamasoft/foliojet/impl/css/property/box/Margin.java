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
 * margin-top / margin-right / margin-bottom / margin-left 特性です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class Margin extends AbstractPrimitivePropertyInfo {
	public static final Margin TOP = new Margin(Side.TOP);

	public static final Margin RIGHT = new Margin(Side.RIGHT);

	public static final Margin BOTTOM = new Margin(Side.BOTTOM);

	public static final Margin LEFT = new Margin(Side.LEFT);

	private static final Margin[] BY_SIDE = { TOP, RIGHT, BOTTOM, LEFT };

	private Margin(Side side) {
		super("margin-" + side.text());
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
		final Value value = BoxValueUtils.toMarginWidth(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}
}
