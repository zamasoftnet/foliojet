package net.zamasoft.foliojet.impl.css.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * top / right / bottom / left 特性です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class Inset extends AbstractPrimitivePropertyInfo {
	public static final Inset TOP = new Inset(Side.TOP);

	public static final Inset RIGHT = new Inset(Side.RIGHT);

	public static final Inset BOTTOM = new Inset(Side.BOTTOM);

	public static final Inset LEFT = new Inset(Side.LEFT);

	private static final Inset[] BY_SIDE = { TOP, RIGHT, BOTTOM, LEFT };

	private Inset(Side side) {
		super(side.text());
	}

	public static Value get(CSSStyle style, Side side) {
		return style.get(BY_SIDE[side.resolve(style).ordinal()]);
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = BoxValueUtils.toTRLB(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}
}
