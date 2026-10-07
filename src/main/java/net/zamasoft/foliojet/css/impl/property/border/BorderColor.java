package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.impl.property.box.Side;

/**
 * border-top-color / border-right-color / border-bottom-color /
 * border-left-color properties.
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
		Value declared = style.isDeclared(BY_SIDE[side.ordinal()]) ? null
				: LogicalBorder.declaredFor(style, LogicalBorder.Aspect.COLOR, side);
		Value value = declared != null ? declared : style.get(BY_SIDE[side.ordinal()]);
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
		// **Resolve typed attr() here** (2026-08-04). Passing it through unresolved
		// causes the cast to ColorValue in BorderColor.get() to fail
		// (an actual failure with <table border bordercolor>).
		value = ValueUtils.emExToAbsoluteLength(value, style);
		if (value == KeywordValue.DEFAULT || value == KeywordValue.NONE) {
			// DEFAULT is currentColor. NONE is an attr() that could not be resolved.
			value = style.get(CSSColor.INFO);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		// Typed attr() (2026-08-03). Get border width/color from attributes.
		final Value attrValue = net.zamasoft.foliojet.css.util.AttrValueUtils.toTypedAttr(ua, lu, net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.COLOR);
		if (attrValue != null) {
			return attrValue;
		}

		if (ColorValueUtils.isTransparent(lu)) {
			return KeywordValue.TRANSPARENT;
		}
		Value value = ColorValueUtils.toColorOrCurrent(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
