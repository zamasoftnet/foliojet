package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.impl.property.box.Side;

/**
 * border-top-color / border-right-color / border-bottom-color /
 * border-left-color 特性です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class BorderColor extends AbstractPrimitivePropertyInfo {
	public static final BorderColor TOP = new BorderColor(Side.TOP);

	public static final BorderColor RIGHT = new BorderColor(Side.RIGHT);

	public static final BorderColor BOTTOM = new BorderColor(Side.BOTTOM);

	public static final BorderColor LEFT = new BorderColor(Side.LEFT);

	private static final BorderColor[] BY_SIDE = { TOP, RIGHT, BOTTOM, LEFT };

	private BorderColor(Side side) {
		super("border-" + side.text() + "-color");
	}

	public static net.zamasoft.pdfg2d.gc.paint.Color get(CSSStyle style, Side side) {
		Value value = style.get(BY_SIDE[side.resolve(style).ordinal()]);
		if (value == KeywordValue.TRANSPARENT) {
			return null;
		}
		return ((ColorValue) value).getColor();
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == KeywordValue.DEFAULT) {
			value = style.get(CSSColor.INFO);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ColorValueUtils.isTransparent(lu)) {
			return KeywordValue.TRANSPARENT;
		}
		Value value = ColorValueUtils.toColor(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
